package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoAplicacao;
import br.com.miranda.gestor.ativos.brutos.external.dto.PanoramaMercadoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado.LinhaAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado.LinhaEvento;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado.LinhaMacro;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado.LinhaVolumeAnormal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Panorama do mercado para o carrossel da Inicio (Plano MERC, MERC-GES-1).
 * O repositorio agrega por pregao; aqui ficam os universos, a variacao, a
 * mediana e a coesao do setor, o desvio, a ordenacao com desempate e o
 * corte em 10. Resultado em cache por (janela, dataPregao), com validade de 30 segundos
 * para incorporar atualizações da carga no mesmo pregão.
 *
 * <p>Periodo de N pregoes: os N ultimos pregoes, de {@code dataInicial} ate
 * {@code dataPregao}. A variacao compara o fechamento de {@code dataPregao}
 * com o do pregao imediatamente anterior a {@code dataInicial} (assim
 * {@code 1d} e a variacao do ultimo pregao e {@code ano} e a do ano ate
 * agora); volume e negocios sao somados nos N pregoes.
 */
@Slf4j
@Service
public class ServicoPanoramaMercado {

    static final int LIMITE = 10;
    static final double VOLUME_MINIMO_LIQUIDOS = 1_000_000.0;
    static final double PRESENCA_MINIMA = 0.80;
    static final int EMPRESAS_MINIMAS_SETOR = 3;
    static final int PREGOES_MEDIA = 21;
    static final String OUTROS = "Outros";
    /** Abaixo disso a variacao conta como zero (fechamento igual). */
    static final double EPSILON = 1e-9;

    static final String SETORES = "setores";
    static final String ALTAS = "altas";
    static final String QUEDAS = "quedas";
    static final String LIQUIDEZ = "liquidez";
    static final String NEGOCIACAO = "negociacao";
    static final String AMPLITUDE = "amplitude";
    static final String VOLUME_ANORMAL = "volumeAnormal";
    static final String MACRO = "macro";
    static final String EVENTOS = "eventos";
    /** Ordem dos blocos em indisponivel. */
    static final List<String> BLOCOS = List.of(SETORES, ALTAS, QUEDAS, LIQUIDEZ, NEGOCIACAO, AMPLITUDE,
            VOLUME_ANORMAL, MACRO, EVENTOS);
    // BLOCOS_SEGUNDA_ONDA removido: todos os blocos sao calculados agora (MERC-GES-2).

    /** Catalogo de janelas do contrato. */
    enum Janela {
        UM_DIA("1d", 1), CINCO_DIAS("5d", 5), VINTE_UM_DIAS("21d", 21), ANO("ano", 0);

        final String codigo;
        final int pregoes;

        Janela(String codigo, int pregoes) {
            this.codigo = codigo;
            this.pregoes = pregoes;
        }

        static Janela de(String valor) {
            if (valor == null || valor.isBlank()) return CINCO_DIAS;
            String normalizado = valor.trim().toLowerCase(Locale.ROOT);
            return Arrays.stream(values()).filter(j -> j.codigo.equals(normalizado)).findFirst()
                    .orElseThrow(() -> new ExcecaoAplicacao("JANELA_INVALIDA",
                            "janela deve ser 1d, 5d, 21d ou ano: " + valor, HttpStatus.BAD_REQUEST));
        }
    }

    /** Pregoes que delimitam o periodo e a media de 21. Base e baseMedia21 podem faltar (historico curto). */
    record Periodo(LocalDate dataPregao, LocalDate dataInicial, LocalDate dataBase, LocalDate inicioMedia21,
                   LocalDate baseMedia21) {
    }

    /** Ativo do universo com a variacao ja calculada (nula fora dos liquidos). */
    private record Ativo(LinhaAtivo linha, boolean presenca, boolean liquido, Double variacao) {
        String simbolo() {
            return linha.simbolo();
        }

        double volume() {
            return linha.volumePeriodo();
        }
    }

    private final RepositorioPanoramaMercado repositorio;
    static final Duration VALIDADE_CACHE = Duration.ofSeconds(30);
    private record EntradaCache(PanoramaMercadoDTO.Panorama panorama, Instant expiraEm) {}
    private final Clock relogio;
    private final Map<String, EntradaCache> cache = new ConcurrentHashMap<>();

    @Autowired
    public ServicoPanoramaMercado(RepositorioPanoramaMercado repositorio) {
        this(repositorio, Clock.systemUTC());
    }

    ServicoPanoramaMercado(RepositorioPanoramaMercado repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    public PanoramaMercadoDTO.Panorama panorama(String janelaPedida) {
        Janela janela = Janela.de(janelaPedida);
        Optional<LocalDate> ultimo = repositorio.ultimoPregao();
        if (ultimo.isEmpty()) {
            return semDado(janela);
        }
        // Verificada antes do cache: a conclusão da liquidez muda a data atendida
        // imediatamente, sem misturar métricas antigas com preços novos.
        LocalDate dataPregao = repositorio.ultimoPregaoComLiquidez(ultimo.get()).orElse(ultimo.get());
        String chave = janela.codigo + "|" + dataPregao;
        EntradaCache emCache = cache.get(chave);
        if (emCache != null && relogio.instant().isBefore(emCache.expiraEm())) {
            return emCache.panorama();
        }
        cache.remove(chave);
        PanoramaMercadoDTO.Panorama panorama = calcular(janela, periodo(janela, dataPregao));
        // Macro/eventos podem existir antes da liquidez. Isso não torna uma
        // resposta sem universo de ações apta a cache.
        if (panorama.universo().presenca() > 0) {
            cache.keySet().removeIf(k -> !k.endsWith("|" + dataPregao));
            cache.put(chave, new EntradaCache(panorama, relogio.instant().plus(VALIDADE_CACHE)));
        }
        return panorama;
    }

    Periodo periodo(Janela janela, LocalDate dataPregao) {
        List<LocalDate> recentes = repositorio.pregoesAte(dataPregao, PREGOES_MEDIA + 1);
        LocalDate inicioMedia21 = recentes.get(Math.min(PREGOES_MEDIA, recentes.size()) - 1);
        LocalDate baseMedia21 = recentes.size() > PREGOES_MEDIA ? recentes.get(PREGOES_MEDIA) : null;
        if (janela == Janela.ANO) {
            LocalDate inicial = repositorio.primeiroPregaoEntre(dataPregao.withDayOfYear(1), dataPregao)
                    .orElse(dataPregao);
            LocalDate base = repositorio.pregaoAnterior(inicial).orElse(null);
            return new Periodo(dataPregao, inicial, base, inicioMedia21, baseMedia21);
        }
        int n = janela.pregoes;
        LocalDate inicial = recentes.get(Math.min(n, recentes.size()) - 1);
        LocalDate base = recentes.size() > n ? recentes.get(n) : null;
        return new Periodo(dataPregao, inicial, base, inicioMedia21, baseMedia21);
    }

    PanoramaMercadoDTO.Panorama calcular(Janela janela, Periodo periodo) {
        List<LinhaAtivo> linhas;
        try {
            linhas = repositorio.ativos(periodo.dataBase(), periodo.dataInicial(), periodo.dataPregao(),
                    periodo.inicioMedia21(), periodo.baseMedia21());
        } catch (DataAccessException e) {
            log.warn("(PANORAMA-MERCADO)-Falha ao ler ativos do pregao {}: {}", periodo.dataPregao(), e.getMessage());
            linhas = List.of();
        }
        List<Ativo> ativos = linhas.stream().map(ServicoPanoramaMercado::classificar).toList();
        List<Ativo> liquidos = ativos.stream().filter(Ativo::liquido).toList();
        List<Ativo> presenca = ativos.stream().filter(Ativo::presenca).toList();
        // Evento corporativo nao ajustado no periodo: fora de tudo que usa variacao.
        List<Ativo> comVariacao = liquidos.stream().filter(a -> !a.linha().eventoPeriodo()).toList();

        List<PanoramaMercadoDTO.Setor> setores = setores(comVariacao);
        List<PanoramaMercadoDTO.Variacao> altas = altas(comVariacao);
        List<PanoramaMercadoDTO.Variacao> quedas = quedas(comVariacao);
        PanoramaMercadoDTO.Liquidez liquidez = liquidez(presenca);
        PanoramaMercadoDTO.Negociacao negociacao = negociacao(presenca);
        PanoramaMercadoDTO.Amplitude amplitude = amplitude(comVariacao);

        // --- MERC-GES-2: volume anormal, macro, eventos ----------------------

        List<PanoramaMercadoDTO.VolumeAnormal> volumeAnormal;
        try {
            List<LinhaVolumeAnormal> linhasVol =
                    repositorio.volumeAnormal(periodo.dataPregao(), periodo.inicioMedia21());
            volumeAnormal = volumeAnormal(linhasVol);
        } catch (DataAccessException e) {
            log.warn("(PANORAMA-MERCADO)-Falha ao ler volume anormal do pregao {}: {}",
                    periodo.dataPregao(), e.getMessage());
            volumeAnormal = List.of();
        }

        PanoramaMercadoDTO.Macro macro;
        try {
            LinhaMacro linhaMacro = repositorio.macro(periodo.dataPregao(), periodo.dataInicial());
            macro = linhaMacro == null ? macroVazio()
                    : new PanoramaMercadoDTO.Macro(
                            linhaMacro.selic(), linhaMacro.cdiPeriodo(),
                            linhaMacro.dolar(), linhaMacro.ipca12m());
        } catch (DataAccessException e) {
            log.warn("(PANORAMA-MERCADO)-Falha ao ler macro do pregao {}: {}",
                    periodo.dataPregao(), e.getMessage());
            macro = macroVazio();
        }

        List<PanoramaMercadoDTO.Evento> eventos;
        try {
            List<LinhaEvento> linhasEv = repositorio.eventos(periodo.dataPregao());
            eventos = linhasEv.stream()
                    .map(l -> new PanoramaMercadoDTO.Evento(l.simbolo(), l.tipo(), l.data(), l.fator()))
                    .toList();
        } catch (DataAccessException e) {
            log.warn("(PANORAMA-MERCADO)-Falha ao ler eventos do pregao {}: {}",
                    periodo.dataPregao(), e.getMessage());
            eventos = List.of();
        }

        // --- indisponivel ----------------------------------------------------

        List<String> indisponivel = new ArrayList<>();
        for (String bloco : BLOCOS) {
            boolean vazio = switch (bloco) {
                case SETORES -> setores.isEmpty();
                case ALTAS -> altas.isEmpty();
                case QUEDAS -> quedas.isEmpty();
                case LIQUIDEZ -> liquidez.maiores().isEmpty();
                case NEGOCIACAO -> negociacao.maiores().isEmpty();
                case AMPLITUDE -> amplitude.subiram() == null;
                case VOLUME_ANORMAL -> volumeAnormal.isEmpty();
                case MACRO -> macro.selic() == null;
                case EVENTOS -> eventos.isEmpty();
                default -> true;
            };
            if (vazio) indisponivel.add(bloco);
        }
        return new PanoramaMercadoDTO.Panorama(janela.codigo, periodo.dataPregao(), periodo.dataInicial(),
                new PanoramaMercadoDTO.Universo(liquidos.size(), presenca.size()), setores, altas, quedas,
                liquidez, negociacao, amplitude, volumeAnormal, macro, eventos, List.copyOf(indisponivel));
    }

    private static Ativo classificar(LinhaAtivo l) {
        boolean presenca = l.presenca() != null && l.presenca() >= PRESENCA_MINIMA - EPSILON;
        boolean extremos = positivo(l.fechamentoFinal()) && positivo(l.fechamentoBase());
        boolean liquido = presenca && extremos && l.volumeMedio63d() != null
                && l.volumeMedio63d() >= VOLUME_MINIMO_LIQUIDOS;
        Double variacao = liquido ? l.fechamentoFinal() / l.fechamentoBase() - 1.0 : null;
        return new Ativo(l, presenca, liquido, variacao);
    }

    private static boolean positivo(Double valor) {
        return valor != null && valor > 0;
    }

    // --- setores x empresas ---------------------------------------------------------------

    private static List<PanoramaMercadoDTO.Setor> setores(List<Ativo> comVariacao) {
        Map<String, List<Ativo>> porSetor = comVariacao.stream().filter(a -> a.linha().setor() != null)
                .collect(Collectors.groupingBy(a -> a.linha().setor(), TreeMap::new, Collectors.toList()));
        List<PanoramaMercadoDTO.Setor> setores = new ArrayList<>();
        List<Ativo> outros = new ArrayList<>();
        porSetor.forEach((setor, membros) -> {
            if (membros.size() < EMPRESAS_MINIMAS_SETOR) {
                outros.addAll(membros);
            } else {
                setores.add(setor(setor, membros.get(0).linha().grupo(), membros));
            }
        });
        setores.sort(Comparator.comparing(PanoramaMercadoDTO.Setor::variacaoMediana, Comparator.reverseOrder())
                .thenComparing(PanoramaMercadoDTO.Setor::setor));
        // "Outros" fica sempre por ultimo e nunca lidera.
        if (!outros.isEmpty()) {
            setores.add(setor(OUTROS, null, outros));
        }
        return List.copyOf(setores);
    }

    private static PanoramaMercadoDTO.Setor setor(String nome, String grupo, List<Ativo> membros) {
        List<Double> variacoes = membros.stream().map(Ativo::variacao).toList();
        double mediana = mediana(variacoes);
        double coesao = coesao(variacoes, mediana);
        Comparator<Ativo> porVolumeESimbolo = Comparator.comparingDouble(Ativo::volume).reversed()
                .thenComparing(Ativo::simbolo);
        List<Ativo> desc = membros.stream()
                .sorted(Comparator.comparingDouble((Ativo a) -> a.variacao() - mediana).reversed()
                        .thenComparing(porVolumeESimbolo))
                .toList();
        List<Ativo> asc = membros.stream()
                .sorted(Comparator.comparingDouble((Ativo a) -> a.variacao() - mediana)
                        .thenComparing(porVolumeESimbolo))
                .toList();
        // Ate 2x10 empresas, todas aparecem uma vez: a metade de cima em melhores, o resto em piores.
        int n = membros.size();
        int qtdMelhores = n >= 2 * LIMITE ? LIMITE : (n + 1) / 2;
        int qtdPiores = n >= 2 * LIMITE ? LIMITE : n / 2;
        Function<Ativo, PanoramaMercadoDTO.EmpresaSetor> empresa = a -> new PanoramaMercadoDTO.EmpresaSetor(
                a.simbolo(), a.linha().nome(), a.variacao(), a.variacao() - mediana, a.volume());
        return new PanoramaMercadoDTO.Setor(nome, grupo, n, mediana, coesao, selo(coesao),
                desc.stream().limit(qtdMelhores).map(empresa).toList(),
                asc.stream().limit(qtdPiores).map(empresa).toList());
    }

    static double mediana(List<Double> valores) {
        List<Double> ordenados = valores.stream().sorted().toList();
        int n = ordenados.size();
        if (n == 0) throw new IllegalArgumentException("mediana de lista vazia");
        return n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2.0;
    }

    /** Fracao das empresas que andaram na mesma direcao da mediana (subiu, caiu ou ficou estavel). */
    static double coesao(List<Double> variacoes, double mediana) {
        int direcao = direcao(mediana);
        long mesmaDirecao = variacoes.stream().filter(v -> direcao(v) == direcao).count();
        return (double) mesmaDirecao / variacoes.size();
    }

    static String selo(double coesao) {
        if (coesao >= 0.70 - EPSILON) return "JUNTO";
        if (coesao >= 0.50 - EPSILON) return "MISTO";
        return "DISPERSO";
    }

    private static int direcao(double variacao) {
        if (variacao > EPSILON) return 1;
        if (variacao < -EPSILON) return -1;
        return 0;
    }

    // --- altas e quedas -------------------------------------------------------------------

    private static List<PanoramaMercadoDTO.Variacao> altas(List<Ativo> comVariacao) {
        return variacoes(comVariacao.stream().filter(a -> direcao(a.variacao()) > 0).toList(),
                Comparator.comparingDouble(Ativo::variacao).reversed());
    }

    private static List<PanoramaMercadoDTO.Variacao> quedas(List<Ativo> comVariacao) {
        return variacoes(comVariacao.stream().filter(a -> direcao(a.variacao()) < 0).toList(),
                Comparator.comparingDouble(Ativo::variacao));
    }

    private static List<PanoramaMercadoDTO.Variacao> variacoes(List<Ativo> ativos, Comparator<Ativo> ordem) {
        List<Ativo> top = topo(ativos, ordem);
        List<PanoramaMercadoDTO.Variacao> itens = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Ativo a = top.get(i);
            itens.add(new PanoramaMercadoDTO.Variacao(i + 1, a.simbolo(), a.linha().nome(), a.linha().setor(),
                    a.variacao(), a.volume()));
        }
        return List.copyOf(itens);
    }

    // --- liquidez e negociacao ------------------------------------------------------------

    private static PanoramaMercadoDTO.Liquidez liquidez(List<Ativo> presenca) {
        List<Ativo> base = presenca.stream().filter(a -> a.linha().volumeMedio63d() != null).toList();
        Comparator<Ativo> porVolume63 = Comparator.comparingDouble(a -> a.linha().volumeMedio63d());
        return new PanoramaMercadoDTO.Liquidez(itensLiquidez(topo(base, porVolume63.reversed())),
                itensLiquidez(topo(base, porVolume63)));
    }

    private static List<PanoramaMercadoDTO.ItemLiquidez> itensLiquidez(List<Ativo> ativos) {
        List<PanoramaMercadoDTO.ItemLiquidez> itens = new ArrayList<>();
        for (int i = 0; i < ativos.size(); i++) {
            LinhaAtivo l = ativos.get(i).linha();
            itens.add(new PanoramaMercadoDTO.ItemLiquidez(i + 1, l.simbolo(), l.nome(), l.setor(),
                    l.volumeMedio63d(), l.presenca(), l.spreadEstimado()));
        }
        return List.copyOf(itens);
    }

    /** Presenca >= 80% e ao menos um negocio no periodo: o ultimo lugar nao e uma acao parada com R$ 0. */
    private static PanoramaMercadoDTO.Negociacao negociacao(List<Ativo> presenca) {
        List<Ativo> base = presenca.stream().filter(a -> a.linha().negociosPeriodo() > 0).toList();
        Comparator<Ativo> porVolume = Comparator.comparingDouble(Ativo::volume);
        return new PanoramaMercadoDTO.Negociacao(itensNegociacao(topo(base, porVolume.reversed())),
                itensNegociacao(topo(base, porVolume)));
    }

    private static List<PanoramaMercadoDTO.ItemNegociacao> itensNegociacao(List<Ativo> ativos) {
        List<PanoramaMercadoDTO.ItemNegociacao> itens = new ArrayList<>();
        for (int i = 0; i < ativos.size(); i++) {
            LinhaAtivo l = ativos.get(i).linha();
            itens.add(new PanoramaMercadoDTO.ItemNegociacao(i + 1, l.simbolo(), l.nome(), l.setor(),
                    l.volumePeriodo(), l.negociosPeriodo()));
        }
        return List.copyOf(itens);
    }

    // --- amplitude ------------------------------------------------------------------------

    private static PanoramaMercadoDTO.Amplitude amplitude(List<Ativo> comVariacao) {
        if (comVariacao.isEmpty()) {
            return new PanoramaMercadoDTO.Amplitude(null, null, null, null);
        }
        int subiram = 0;
        int cairam = 0;
        int estaveis = 0;
        for (Ativo a : comVariacao) {
            switch (direcao(a.variacao())) {
                case 1 -> subiram++;
                case -1 -> cairam++;
                default -> estaveis++;
            }
        }
        // Media de 21 pregoes com evento na janela distorce: fica fora da conta.
        List<Ativo> comMedia = comVariacao.stream()
                .filter(a -> a.linha().media21() != null && !a.linha().evento21()).toList();
        Double acima = comMedia.isEmpty() ? null
                : (double) comMedia.stream().filter(a -> a.linha().fechamentoFinal() > a.linha().media21()).count()
                / comMedia.size();
        return new PanoramaMercadoDTO.Amplitude(subiram, cairam, estaveis, acima);
    }

    // --- comum ----------------------------------------------------------------------------

    /** Ordena pelo criterio, desempata por maior volume no periodo e depois simbolo, e corta em 10. */
    private static List<Ativo> topo(List<Ativo> ativos, Comparator<Ativo> criterio) {
        return ativos.stream()
                .sorted(criterio.thenComparing(Comparator.comparingDouble(Ativo::volume).reversed())
                        .thenComparing(Ativo::simbolo))
                .limit(LIMITE)
                .toList();
    }

    /**
     * Ordena por razao (volumePregao/media21) decrescente, desempata por
     * volumePregao decrescente e simbolo, corta em 10 e monta os DTOs com
     * posicao a partir de 1. Linhas com media21 == 0 sao ignoradas.
     */
    private static List<PanoramaMercadoDTO.VolumeAnormal> volumeAnormal(List<LinhaVolumeAnormal> linhas) {
        Comparator<LinhaVolumeAnormal> ord =
                Comparator.comparingDouble((LinhaVolumeAnormal l) -> l.volumePregao() / l.media21());
        List<LinhaVolumeAnormal> top = linhas.stream()
                .filter(l -> l.media21() > 0)
                .sorted(ord.reversed()
                        .thenComparing(
                                Comparator.comparingDouble(LinhaVolumeAnormal::volumePregao).reversed())
                        .thenComparing(LinhaVolumeAnormal::simbolo))
                .limit(LIMITE)
                .toList();
        List<PanoramaMercadoDTO.VolumeAnormal> itens = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            LinhaVolumeAnormal l = top.get(i);
            itens.add(new PanoramaMercadoDTO.VolumeAnormal(
                    i + 1, l.simbolo(), l.volumePregao() / l.media21(), l.volumePregao()));
        }
        return List.copyOf(itens);
    }

    private static PanoramaMercadoDTO.Macro macroVazio() {
        return new PanoramaMercadoDTO.Macro(null, null, null, null);
    }

    private static PanoramaMercadoDTO.Panorama semDado(Janela janela) {
        return new PanoramaMercadoDTO.Panorama(janela.codigo, null, null,
                new PanoramaMercadoDTO.Universo(0, 0), List.of(), List.of(), List.of(),
                new PanoramaMercadoDTO.Liquidez(List.of(), List.of()),
                new PanoramaMercadoDTO.Negociacao(List.of(), List.of()),
                new PanoramaMercadoDTO.Amplitude(null, null, null, null), List.of(), macroVazio(), List.of(),
                BLOCOS);
    }
}
