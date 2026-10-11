package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.ItemBloqueio;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.Motivo;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.MotivoBloqueio;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.Bloqueios;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.Diario;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.Operacao;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.Operacoes;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.PontoDiario;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.Resumo;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.Saude;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO.Violacao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.DiarioLinha;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.EventoLinha;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.OperacaoLinha;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.Regra;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Leitura do plano OPR (OPR-GES-2..4): resumo, diario, operacoes, bloqueios e saude do sistema operavel
 * simulado. SO LE: nao decide operacao, nao calcula regra de entrada ou saida, nao grava e nao publica
 * (a geracao e do worker do gerar-insights). Sem dados, devolve a estrutura vazia com aviso, nunca erro.
 */
@Service
@RequiredArgsConstructor
public class ServicoOperacional {

    public static final int LIMITE_PADRAO = 200;
    public static final int LIMITE_DIARIO = 500;
    public static final String NAO_OPERAVEL = "NAO_OPERAVEL";
    public static final String BLOQUEADO = "BLOQUEADO";

    static final Set<String> STATUS_OPERACAO = Set.of("PENDENTE", "ABERTA", "FECHADA", "CANCELADA");
    static final Set<String> MOTIVOS_SAIDA = Set.of("EVENTO", "STOP", "LIQUIDEZ", "SINAL", "PRAZO");
    private static final Pattern VERSAO = Pattern.compile("^OPR-\\d{4}\\.\\d{2}\\.\\d{2}-\\d+$");
    private static final Pattern SIMBOLO = Pattern.compile("^[A-Z0-9]{4,12}$");

    private static final Map<String, String> SAIDA_LEGIVEL = Map.of(
            "EVENTO", "Evento corporativo ou fato relevante",
            "STOP", "Stop de preço atingido",
            "LIQUIDEZ", "Liquidez insuficiente por dias seguidos",
            "SINAL", "O sinal de entrada deixou de valer",
            "PRAZO", "Prazo do horizonte encerrado");

    private static final Map<String, String> BLOQUEIO_LEGIVEL = Map.ofEntries(
            Map.entry("SEM_ATR", "Sem ATR calculado (histórico de preços insuficiente)"),
            Map.entry("SEM_ATR_SIZING", "Sem ATR para dimensionar a posição"),
            Map.entry("SERIE_SEM_AJUSTE", "Série de preços sem ajuste por evento corporativo"),
            Map.entry("EVENTO_CORPORATIVO_RECENTE", "Evento corporativo recente"),
            Map.entry("FATO_RELEVANTE_RECENTE", "Fato relevante recente"),
            Map.entry("SEM_FUNDAMENTO_VIGENTE", "Sem fundamento vigente na data (point-in-time)"),
            Map.entry("LIQUIDEZ_INSUFICIENTE", "Liquidez abaixo do mínimo da regra"),
            Map.entry("LIMITE_POSICOES_ABERTAS", "Limite de posições abertas atingido"),
            Map.entry("POSICAO_ABAIXO_DO_MINIMO", "Posição calculada abaixo do mínimo"),
            Map.entry("CAIXA_INSUFICIENTE", "Caixa insuficiente para a posição"),
            Map.entry("BLOQUEIO_DADO", "Dado ausente ou incompleto"),
            Map.entry("BLOQUEIO_LIQUIDEZ", "Bloqueio por liquidez"));

    private final RepositorioOperacional repositorio;

    // ---------- versao em uso ----------

    /** Versao pedida (validada) ou a ATIVA; sem ativa, a do diario mais recente; sem diario, a da ultima operacao. */
    Optional<String> versaoEmUso(String pedida) {
        if (pedida != null && !pedida.isBlank()) {
            if (!VERSAO.matcher(pedida.trim()).matches()) {
                throw new IllegalArgumentException("versaoRegra invalida (esperado OPR-AAAA.MM.DD-N): " + pedida);
            }
            return Optional.of(pedida.trim());
        }
        return repositorio.regraAtiva().map(Regra::versaoRegra)
                .or(repositorio::versaoDoUltimoDiario)
                .or(repositorio::versaoDaUltimaOperacao);
    }

    // ---------- resumo ----------

    public Resumo resumo(String versaoPedida) {
        Optional<String> versao = versaoEmUso(versaoPedida);
        if (versao.isEmpty()) {
            return resumoVazio(null, null, null, 0, 0L, 0L, List.of(avisoSemDados()));
        }
        String v = versao.get();
        Optional<Regra> regra = repositorio.regra(v);
        Optional<DiarioLinha> ultimo = repositorio.ultimoDiario(v);
        int dias = repositorio.contarDiario(v);
        Map<String, Long> ops = repositorio.contarOperacoesPorStatus(v);
        List<String> avisos = new ArrayList<>();
        regra.filter(r -> "RASCUNHO".equals(r.status()))
                .ifPresent(r -> avisos.add("Regra " + v + " ainda em RASCUNHO: o gerar-insights não a ativou."));
        Integer trava = regra.map(r -> inteiro(r.parametros(), "trava", "min_pregoes")).orElse(null);
        long abertas = ops.getOrDefault("ABERTA", 0L);
        long fechadas = ops.getOrDefault("FECHADA", 0L);
        String statusRegra = regra.map(Regra::status).orElse(null);
        if (ultimo.isEmpty()) {
            avisos.add("Sem diário operacional gravado para a regra " + v + ".");
            return resumoVazio(v, statusRegra, trava, dias, abertas, fechadas, avisos);
        }
        DiarioLinha d = ultimo.get();
        BigDecimal ddMax = d.drawdownMaximo() != null ? d.drawdownMaximo() : repositorio.drawdownMinimo(v).orElse(null);
        return new Resumo(OperacionalDTO.MODO, OperacionalDTO.AVISO, v, statusRegra, d.statusSistema(),
                motivos(d.motivosStatus()), repositorio.primeiraDataDoDiario(v).orElse(null), d.dataPregao(), dias,
                trava, d.capitalInicial(), d.patrimonio(), d.caixa(), d.exposicao(), d.posicoesAbertas(),
                d.retornoLiquidoAcumulado(), d.cdiAcumulado(), d.cdiLiquidoIrAcumulado(), excessoSobreCdiLiquido(d),
                d.drawdown(), ddMax, d.impostoEstimadoMes(), d.violacoesLiquidez(), abertas, fechadas, avisos);
    }

    /** Sem diario: NAO_OPERAVEL, 0 pregoes avaliados e ultimoPregao nulo (CTR-OPR-PAI). */
    private static Resumo resumoVazio(String versao, String statusRegra, Integer pregoesMinimos, int avaliados,
                                      long abertas, long fechadas, List<String> avisos) {
        return new Resumo(OperacionalDTO.MODO, OperacionalDTO.AVISO, versao, statusRegra, NAO_OPERAVEL, List.of(),
                null, null, avaliados, pregoesMinimos, null, null, null, null, null, null, null, null, null, null,
                null, null, null, abertas, fechadas, avisos);
    }

    /** Excesso gravado; se faltar, retorno liquido acumulado menos CDI liquido de IR (so com os dois). */
    static BigDecimal excessoSobreCdiLiquido(DiarioLinha d) {
        if (d.excessoSobreCdiLiquido() != null) {
            return d.excessoSobreCdiLiquido();
        }
        if (d.retornoLiquidoAcumulado() != null && d.cdiLiquidoIrAcumulado() != null) {
            return d.retornoLiquidoAcumulado().subtract(d.cdiLiquidoIrAcumulado());
        }
        return null;
    }

    // ---------- diario ----------

    public Diario diario(LocalDate inicio, LocalDate fim, String versaoPedida) {
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("inicio nao pode ser depois de fim");
        }
        Optional<String> versao = versaoEmUso(versaoPedida);
        if (versao.isEmpty()) {
            return new Diario(OperacionalDTO.MODO, OperacionalDTO.AVISO, null, inicio, fim, List.of(),
                    List.of(avisoSemDados()));
        }
        List<PontoDiario> pontos = repositorio.diario(versao.get(), inicio, fim, LIMITE_DIARIO).stream()
                .map(ServicoOperacional::ponto).toList();
        List<String> avisos = pontos.isEmpty()
                ? List.of("Sem diário na janela pedida para a regra " + versao.get() + ".") : List.of();
        return new Diario(OperacionalDTO.MODO, OperacionalDTO.AVISO, versao.get(), inicio, fim, pontos, avisos);
    }

    private static PontoDiario ponto(DiarioLinha d) {
        return new PontoDiario(d.dataPregao(), d.capitalInicial(), d.patrimonio(), d.caixa(), d.exposicao(),
                d.posicoesAbertas(), d.operacoesAbertasDia(), d.operacoesFechadasDia(), d.retornoBrutoDia(),
                d.retornoLiquidoDia(), d.retornoLiquidoAcumulado(), d.cdiDia(), d.cdiAcumulado(),
                d.cdiLiquidoIrAcumulado(), excessoSobreCdiLiquido(d), d.drawdown(), d.drawdownMaximo(),
                d.impostoEstimadoMes(), d.violacoesLiquidez(), d.statusSistema(), motivos(d.motivosStatus()));
    }

    // ---------- operacoes ----------

    public Operacoes operacoes(String status, String simbolo, LocalDate inicio, LocalDate fim, String motivo,
                               Integer limite, String versaoPedida) {
        String statusN = normalizar(status, STATUS_OPERACAO, "status");
        String motivoN = normalizar(motivo, MOTIVOS_SAIDA, "motivo");
        String simboloN = simbolo == null || simbolo.isBlank() ? null : simbolo.trim().toUpperCase(Locale.ROOT);
        if (simboloN != null && !SIMBOLO.matcher(simboloN).matches()) {
            throw new IllegalArgumentException("simbolo invalido: " + simbolo);
        }
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("inicio nao pode ser depois de fim");
        }
        int lim = limite == null ? LIMITE_PADRAO : Math.max(1, Math.min(limite, RepositorioOperacional.LIMITE_MAXIMO));
        Optional<String> versao = versaoEmUso(versaoPedida);
        if (versao.isEmpty()) {
            return new Operacoes(OperacionalDTO.MODO, OperacionalDTO.AVISO, null, lim, 0, List.of(),
                    List.of(avisoSemDados()));
        }
        List<Operacao> lista = repositorio.operacoes(versao.get(), statusN, simboloN, inicio, fim, motivoN, lim)
                .stream().map(ServicoOperacional::operacao).toList();
        return new Operacoes(OperacionalDTO.MODO, OperacionalDTO.AVISO, versao.get(), lim, lista.size(), lista,
                lista.isEmpty() ? List.of("Nenhuma operação simulada para os filtros pedidos.") : List.of());
    }

    private static Operacao operacao(OperacaoLinha o) {
        BigDecimal custos = o.custosEntrada() == null && o.custosSaida() == null ? null
                : nz(o.custosEntrada()).add(nz(o.custosSaida()));
        return new Operacao(o.id(), o.simbolo(), o.setorGrupo(), o.horizontePregoes(), o.status(),
                o.dataDecisaoEntrada(), o.dataEntrada(), o.precoEntrada(), o.quantidade(), o.valorEntrada(),
                o.stopInicial(), o.stopAtual(), o.faixaLiquidezEntrada(), o.motivoEntrada(), o.dataDecisaoSaida(),
                o.dataSaida(), o.precoSaida(), o.motivoSaida(),
                o.motivoSaida() == null ? null : SAIDA_LEGIVEL.getOrDefault(o.motivoSaida(), o.motivoSaida()),
                o.motivoSaidaDetalhe(), o.custosEntrada(), o.custosSaida(), custos, o.resultadoBruto(),
                o.resultadoPosCustos(), o.impostoEstimado(), o.resultadoPosImpostoEstimado());
    }

    // ---------- bloqueios ----------

    public Bloqueios bloqueios(LocalDate data, String simbolo, String motivo, Integer limite, String versaoPedida) {
        String simboloN = simbolo == null || simbolo.isBlank() ? null : simbolo.trim().toUpperCase(Locale.ROOT);
        if (simboloN != null && !SIMBOLO.matcher(simboloN).matches()) {
            throw new IllegalArgumentException("simbolo invalido: " + simbolo);
        }
        String codigo = motivo == null || motivo.isBlank() ? null : motivo.trim().toUpperCase(Locale.ROOT);
        int lim = limite == null ? LIMITE_PADRAO : Math.max(1, Math.min(limite, RepositorioOperacional.LIMITE_MAXIMO));
        Optional<String> versao = versaoEmUso(versaoPedida);
        if (versao.isEmpty()) {
            return new Bloqueios(OperacionalDTO.MODO, OperacionalDTO.AVISO, null, data, 0, List.of(),
                    List.of(avisoSemDados()));
        }
        LocalDate dia = data != null ? data : repositorio.ultimaDataDeBloqueio(versao.get()).orElse(null);
        if (dia == null) {
            return new Bloqueios(OperacionalDTO.MODO, OperacionalDTO.AVISO, versao.get(), null, 0, List.of(),
                    List.of("Nenhum bloqueio registrado para a regra " + versao.get() + "."));
        }
        List<ItemBloqueio> lista = agrupar(
                repositorio.bloqueios(versao.get(), dia, simboloN, RepositorioOperacional.LIMITE_MAXIMO), codigo)
                .stream().limit(lim).toList();
        return new Bloqueios(OperacionalDTO.MODO, OperacionalDTO.AVISO, versao.get(), dia, lista.size(), lista,
                lista.isEmpty() ? List.of("Nenhum bloqueio em " + dia + " para os filtros pedidos.") : List.of());
    }

    /**
     * Um item por ativo (CTR-OPR-PAI) com todos os motivos do pregao; {@code codigo} (opcional) deixa so os
     * motivos desse codigo e descarta o ativo que ficar sem nenhum. A faixa vem do payload, se gravada.
     */
    static List<ItemBloqueio> agrupar(List<EventoLinha> eventos, String codigo) {
        Map<String, List<MotivoBloqueio>> motivosPorAtivo = new LinkedHashMap<>();
        Map<String, String> faixaPorAtivo = new LinkedHashMap<>();
        for (EventoLinha e : eventos) {
            String chave = e.simbolo() == null ? "" : e.simbolo();
            for (MotivoBloqueio m : expandir(e)) {
                if (codigo == null || codigo.equals(m.codigo())) {
                    motivosPorAtivo.computeIfAbsent(chave, k -> new ArrayList<>()).add(m);
                }
            }
            String faixa = primeiroTexto(e.payload(), "faixa", "faixa_liquidez", "faixa_liquidez_entrada");
            if (faixa != null) {
                faixaPorAtivo.putIfAbsent(chave, faixa.trim().toUpperCase(Locale.ROOT));
            }
        }
        List<ItemBloqueio> itens = new ArrayList<>();
        motivosPorAtivo.forEach((ativo, motivos) -> itens.add(
                new ItemBloqueio(ativo.isEmpty() ? null : ativo, faixaPorAtivo.get(ativo), motivos)));
        return itens;
    }

    /** Um evento carrega varios motivos em {@code motivos[]} (ou {@code bloqueios[]}, formato antigo); cada um vira um motivo. */
    static List<MotivoBloqueio> expandir(EventoLinha e) {
        JsonNode payload = e.payload();
        List<JsonNode> itens = new ArrayList<>();
        if (payload != null && payload.path("motivos").isArray()) {
            payload.path("motivos").forEach(itens::add);
        } else if (payload != null && payload.path("bloqueios").isArray()) {
            payload.path("bloqueios").forEach(itens::add);
        } else {
            itens.add(payload);
        }
        String categoria = "BLOQUEIO_DADO".equals(e.tipo()) ? "DADO" : "LIQUIDEZ";
        List<MotivoBloqueio> saida = new ArrayList<>();
        for (JsonNode item : itens) {
            String codigo = primeiroTexto(item, "codigo", "motivo", "codigo_bloqueio");
            if (codigo == null) {
                codigo = e.tipo();
            }
            codigo = codigo.trim().toUpperCase(Locale.ROOT);
            saida.add(new MotivoBloqueio(codigo, legivel(codigo), primeiroTexto(item, "detalhe", "mensagem"),
                    item == null ? null : item.get("valor"), item == null ? null : item.get("limite"), categoria,
                    e.eventId()));
        }
        return saida;
    }

    static String legivel(String codigo) {
        String conhecido = BLOQUEIO_LEGIVEL.get(codigo);
        if (conhecido != null) {
            return conhecido;
        }
        String texto = codigo.replace('_', ' ').toLowerCase(Locale.ROOT);
        return texto.isEmpty() ? codigo : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    // ---------- saude ----------

    public Saude saude(String versaoPedida) {
        boolean tabelas = repositorio.tabelasDisponiveis();
        Optional<String> versao = versaoEmUso(versaoPedida);
        Optional<LocalDate> ultimoPregao = repositorio.ultimoPregaoDisponivel();
        if (versao.isEmpty()) {
            List<String> avisos = new ArrayList<>();
            avisos.add(tabelas ? "Nenhuma regra, diário ou operação gravados ainda."
                    : "Tabelas operacionais indisponíveis (migração V23 aplicada?).");
            return new Saude(OperacionalDTO.MODO, OperacionalDTO.AVISO, null, NAO_OPERAVEL, null, null, null,
                    ultimoPregao.orElse(null), null, List.of(), List.of(), 0L, tabelas, avisos);
        }
        String v = versao.get();
        Optional<DiarioLinha> ultimo = repositorio.ultimoDiario(v);
        long pendentes = repositorio.eventosPendentes(v);
        if (ultimo.isEmpty()) {
            return new Saude(OperacionalDTO.MODO, OperacionalDTO.AVISO, v, NAO_OPERAVEL, null, null, null,
                    ultimoPregao.orElse(null), null, List.of(), List.of(), pendentes, tabelas,
                    List.of("Sem diário operacional gravado para a regra " + v + "."));
        }
        DiarioLinha d = ultimo.get();
        Optional<Regra> regra = repositorio.regra(v);
        List<String> avisos = new ArrayList<>();
        List<Violacao> violacoes = avaliarViolacoes(d, ultimoPregao.orElse(null), regra.map(Regra::parametros).orElse(null),
                avisos);
        String status = violacoes.isEmpty() ? d.statusSistema() : BLOQUEADO;
        Boolean completos = ultimoPregao.map(p -> !d.dataPregao().isBefore(p)).orElse(null);
        return new Saude(OperacionalDTO.MODO, OperacionalDTO.AVISO, v, status, d.statusSistema(), d.atualizadoEm(),
                d.dataPregao(), ultimoPregao.orElse(null), completos, violacoes.stream()
                .map(x -> new Motivo(x.tipo(), x.mensagem())).toList(), violacoes, pendentes, tabelas, avisos);
    }

    /**
     * Violacoes que bloqueiam: diario sem cobrir o ultimo pregao (dado incompleto), violacao de liquidez no
     * dia, drawdown no limite da regra e diario ja gravado como BLOQUEADO. O limite de drawdown e opcional
     * em {@code parametros_json} ({@code trava.drawdown_maximo} ou {@code risco.drawdown_maximo}); sem ele, o
     * drawdown nao bloqueia e um aviso diz isso.
     */
    static List<Violacao> avaliarViolacoes(DiarioLinha d, LocalDate ultimoPregao, JsonNode parametros,
                                           List<String> avisos) {
        List<Violacao> v = new ArrayList<>();
        if (ultimoPregao != null && d.dataPregao().isBefore(ultimoPregao)) {
            v.add(new Violacao("DADO_INCOMPLETO", "Diário avaliado até " + d.dataPregao()
                    + ", mas o último pregão disponível é " + ultimoPregao + ".",
                    texto(d.dataPregao().toString()), texto(ultimoPregao.toString())));
        }
        if (d.violacoesLiquidez() != null && d.violacoesLiquidez() > 0) {
            v.add(new Violacao("LIQUIDEZ", d.violacoesLiquidez() + " violação(ões) de liquidez no dia.",
                    JsonNodeFactory.instance.numberNode(d.violacoesLiquidez()), JsonNodeFactory.instance.numberNode(0)));
        }
        BigDecimal limite = numero(parametros, "trava", "drawdown_maximo");
        if (limite == null) {
            limite = numero(parametros, "risco", "drawdown_maximo");
        }
        BigDecimal dd = d.drawdownMaximo() != null ? d.drawdownMaximo() : d.drawdown();
        if (limite == null) {
            avisos.add("A regra não define limite de drawdown; ele não bloqueia o status.");
        } else if (dd != null && dd.compareTo(limite.abs().negate()) <= 0) {
            v.add(new Violacao("DRAWDOWN", "Drawdown de " + dd.toPlainString() + " atingiu o limite da regra.",
                    JsonNodeFactory.instance.numberNode(dd), JsonNodeFactory.instance.numberNode(limite.abs().negate())));
        }
        if (BLOQUEADO.equals(d.statusSistema())) {
            List<String> motivos = textos(d.motivosStatus());
            v.add(new Violacao("STATUS_ALTERADO", "Diário gravado como BLOQUEADO"
                    + (motivos.isEmpty() ? "." : ": " + String.join("; ", motivos)), texto(BLOQUEADO), null));
        }
        return v;
    }

    // ---------- utilitarios ----------

    private static String avisoSemDados() {
        return "Nenhuma regra, diário ou operação gravados ainda (o gerar-insights escreve; o gestor só lê).";
    }

    private static String normalizar(String valor, Set<String> aceitos, String nome) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String n = valor.trim().toUpperCase(Locale.ROOT);
        if (!aceitos.contains(n)) {
            throw new IllegalArgumentException(nome + " invalido: " + valor + " (aceitos: "
                    + String.join(", ", aceitos.stream().sorted().toList()) + ")");
        }
        return n;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static JsonNode texto(String valor) {
        return JsonNodeFactory.instance.textNode(valor);
    }

    private static String primeiroTexto(JsonNode no, String... campos) {
        if (no == null) {
            return null;
        }
        for (String campo : campos) {
            JsonNode valor = no.get(campo);
            if (valor != null && valor.isTextual() && !valor.asText().isBlank()) {
                return valor.asText();
            }
        }
        return null;
    }

    private static BigDecimal numero(JsonNode raiz, String bloco, String campo) {
        if (raiz == null) {
            return null;
        }
        JsonNode valor = raiz.path(bloco).path(campo);
        return valor.isNumber() ? valor.decimalValue() : null;
    }

    private static Integer inteiro(JsonNode raiz, String bloco, String campo) {
        if (raiz == null) {
            return null;
        }
        JsonNode valor = raiz.path(bloco).path(campo);
        return valor.isNumber() ? valor.intValue() : null;
    }

    /**
     * Motivos de status como [{codigo, descricao}]: objeto com codigo/mensagem vira os dois campos; texto que
     * parece codigo (MAIUSCULAS_E_UNDERSCORE) vira codigo com descricao legivel; texto livre vira
     * codigo MOTIVO e a propria frase na descricao.
     */
    static List<Motivo> motivos(JsonNode no) {
        List<Motivo> saida = new ArrayList<>();
        if (no == null || no.isNull()) {
            return saida;
        }
        if (no.isArray()) {
            no.forEach(item -> saida.addAll(motivos(item)));
        } else if (no.isTextual()) {
            String texto = no.asText().trim();
            if (texto.matches("[A-Z][A-Z0-9_]+")) {
                saida.add(new Motivo(texto, legivel(texto)));
            } else if (!texto.isEmpty()) {
                saida.add(new Motivo("MOTIVO", texto));
            }
        } else if (no.isObject()) {
            String codigo = primeiroTexto(no, "codigo", "motivo");
            String descricao = primeiroTexto(no, "descricao", "mensagem", "detalhe");
            if (codigo != null || descricao != null) {
                String c = codigo == null ? "MOTIVO" : codigo.trim().toUpperCase(Locale.ROOT);
                saida.add(new Motivo(c, descricao != null ? descricao : legivel(c)));
            }
        }
        return saida;
    }

    /** Textos de um JSON de motivos: lista de strings, de objetos com codigo/mensagem, ou um objeto so. */
    static List<String> textos(JsonNode no) {
        List<String> saida = new ArrayList<>();
        if (no == null || no.isNull()) {
            return saida;
        }
        if (no.isArray()) {
            no.forEach(item -> saida.addAll(textos(item)));
        } else if (no.isTextual()) {
            saida.add(no.asText());
        } else if (no.isObject()) {
            String t = primeiroTexto(no, "mensagem", "detalhe", "motivo", "codigo");
            if (t != null) {
                saida.add(t);
            }
        }
        return saida;
    }
}
