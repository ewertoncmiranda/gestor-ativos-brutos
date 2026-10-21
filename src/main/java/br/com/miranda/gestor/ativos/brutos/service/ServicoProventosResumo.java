package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.ProventoDistribuidoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO.Vela;
import br.com.miranda.gestor.ativos.brutos.external.dto.ProventosResumoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.ProventosResumoDTO.*;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivoF360;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioProventoDistribuido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Leitura do cache existente; nenhuma chamada externa ou escrita no caminho da tela. */
@Service
@RequiredArgsConstructor
public class ServicoProventosResumo {
    public static final LocalDate INICIO = LocalDate.of(2016, 1, 1);
    private static final String PREMISSA = "Valores brutos por ação, antes de impostos, sem reinvestimento. "
            + "Compra na abertura do primeiro pregão e manutenção até o último fechamento; eventos ex no primeiro pregão "
            + "não conferem direito a essa compra. Direitos declarados não são pagamentos recebidos. "
            + "Ajuste implícito = fechamento anterior − abertura ex; inclui movimento de mercado e não mede o valor do provento.";
    private final ServicoPregoes servicoPregoes;
    private final RepositorioIdentidadeAtivoF360 identidade;
    private final RepositorioProventoDistribuido proventos;

    public ProventosResumoDTO resumo(String simbolo, LocalDate de, LocalDate ate) {
        // Contexto completo impede transformar continuação de EJ na borda da janela em nova data ex.
        PregoesDTO historico = servicoPregoes.montar(simbolo, INICIO, ate, "dia");
        String canonico = historico.simbolo();
        var papel = identidade.buscar(canonico);
        String isin = papel == null ? null : papel.isin();
        return calcular(canonico, de, ate, historico.velas(), isin,
                proventos.findBySimboloOrderByDataPagamentoDesc(canonico.replaceAll("\\d+$", "")));
    }

    static ProventosResumoDTO calcular(String simbolo, LocalDate de, LocalDate ate, List<Vela> historico,
                                       String isin, List<ProventoDistribuidoEntity> distribuicoes) {
        List<Vela> dias = historico.stream().filter(v -> !v.data().isAfter(ate))
                .sorted(Comparator.comparing(Vela::data)).toList();
        List<Vela> periodo = dias.stream().filter(v -> !v.data().isBefore(de)).toList();
        Map<LocalDate, Vela> porData = new TreeMap<>();
        Map<LocalDate, BigDecimal> fechamentoAnterior = new HashMap<>();
        Map<LocalDate, String> marcas = new TreeMap<>();
        Map<LocalDate, LocalDate> inicioDaJanela = new HashMap<>();
        Vela anterior = null;
        Vela ultimaMarcada = null;
        LocalDate inicioJanela = null;
        for (Vela dia : dias) {
            porData.put(dia.data(), dia);
            if (anterior != null) fechamentoAnterior.put(dia.data(), anterior.fechamento());
            String marca = marca(dia.marcaEx());
            if (!marca.isEmpty()) {
                boolean continua = ultimaMarcada != null && marca.equals(marca(ultimaMarcada.marcaEx()))
                        && ChronoUnit.DAYS.between(ultimaMarcada.data(), dia.data()) <= 5;
                if (!continua) {
                    // Primeira linha do histórico com marca não prova o início da janela ex.
                    if (anterior != null) marcas.put(dia.data(), marca);
                    inicioJanela = dia.data();
                }
                inicioDaJanela.put(dia.data(), inicioJanela);
                ultimaMarcada = dia;
            } else {
                ultimaMarcada = null;
            }
            anterior = dia;
        }
        Map<LocalDate, List<ProventoDistribuidoEntity>> pagamentos = new TreeMap<>();
        boolean temIsin = isin != null && !isin.isBlank();
        if (temIsin) {
            Set<String> vistos = new HashSet<>();
            for (var p : distribuicoes) {
                if (!isin.equalsIgnoreCase(p.getIsin()) || p.getUltimaDataComDireito() == null
                        || p.getValorPorAcao() == null || p.getValorPorAcao().signum() < 0 || !monetario(tipo(p.getTipo()))) continue;
                String chave = p.getIsin() + "|" + p.getTipo() + "|" + p.getDataPagamento() + "|" + p.getUltimaDataComDireito();
                if (!vistos.add(chave)) continue;
                LocalDate com = p.getUltimaDataComDireito();
                // Sem o pregão com no histórico, não é possível provar o próximo pregão deste papel.
                if (!porData.containsKey(com)) continue;
                LocalDate ex = dias.stream().map(Vela::data).filter(d -> d.isAfter(com)).findFirst().orElse(null);
                if (ex == null || ChronoUnit.DAYS.between(com, ex) > 10) continue;
                pagamentos.computeIfAbsent(ex, k -> new ArrayList<>()).add(p);
                marcas.merge(ex, "JCP".equals(tipo(p.getTipo())) ? "EJ" : "DIVIDENDO".equals(tipo(p.getTipo())) ? "ED" : "ER",
                        ServicoProventosResumo::combinarMarcas);
            }
        }
        // Uma data-com comprovada tem precedência sobre o começo inferido de uma sequência EJ.
        for (LocalDate ex : pagamentos.keySet()) {
            LocalDate candidato = inicioDaJanela.get(ex);
            if (candidato != null && !candidato.equals(ex) && !pagamentos.containsKey(candidato)) {
                String inferida = marcas.get(candidato);
                if (inferida != null) {
                    Set<String> tiposOficiais = new HashSet<>();
                    pagamentos.get(ex).forEach(p -> tiposOficiais.add(tipo(p.getTipo())));
                    StringBuilder restantes = new StringBuilder("E");
                    inferida.substring(1).chars().filter(c -> !tiposOficiais.contains(tipoLetra((char)c)))
                            .forEach(c -> restantes.append((char)c));
                    if (restantes.length() == 1) marcas.remove(candidato);
                    else marcas.put(candidato, restantes.toString());
                }
            }
        }
        List<Evento> eventos = new ArrayList<>();
        for (var entrada : marcas.entrySet()) {
            LocalDate ex = entrada.getKey();
            if (ex.isBefore(de) || ex.isAfter(ate)) continue;
            Vela dia = porData.get(ex);
            BigDecimal fech = fechamentoAnterior.get(ex);
            BigDecimal ajuste = fech != null && dia.abertura() != null ? fech.subtract(dia.abertura()) : null;
            List<Pagamento> parcelas = pagamentos.getOrDefault(ex, List.of()).stream()
                    .sorted(Comparator.comparing(ProventoDistribuidoEntity::getDataPagamento, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(p -> new Pagamento(p.getDataPagamento(), p.getValorPorAcao(), tipo(p.getTipo()))).toList();
            BigDecimal valor = parcelas.isEmpty() ? null : parcelas.stream().map(Pagamento::valorPorAcao).reduce(BigDecimal.ZERO, BigDecimal::add);
            eventos.add(new Evento(ex, entrada.getValue(), tipos(entrada.getValue()), fech, dia.abertura(), ajuste,
                    percentual(ajuste, fech), valor, parcelas, parcelas.isEmpty() ? "COTAHIST" : "B3_PROVENTOS_ISIN"));
        }
        List<Evento> monetarios = eventos.stream().filter(e -> contemMonetario(e.marca())).toList();
        int comValor = (int) monetarios.stream().filter(e -> valorCompleto(e)).count();
        BigDecimal total = soma(eventos, null);
        BigDecimal jcp = soma(eventos, "JCP");
        BigDecimal dividendos = soma(eventos, "DIVIDENDO");
        List<String> limitacoes = new ArrayList<>();
        if (!dias.isEmpty() && !marca(dias.getFirst().marcaEx()).isEmpty())
            limitacoes.add("O histórico começa com marca ex; sem pregão anterior o início dessa janela não é confirmado.");
        if (!temIsin) limitacoes.add("ISIN ausente: não é seguro associar valores entre classes ON e PN.");
        if (comValor < monetarios.size()) limitacoes.add("Há eventos monetários sem valor oficial completo; totais são parciais.");
        boolean societario = eventos.stream().anyMatch(e -> !e.marca().matches("E[DJR]+"));
        if (societario) limitacoes.add("Evento societário não monetário: retorno simples exige ajuste da quantidade de ações.");
        boolean salto = periodo.size() > 1 && ServicoPregoes.saltos(periodo.stream().map(v ->
                new br.com.miranda.gestor.ativos.brutos.repository.RepositorioPregoes.Pregao(v.data(), v.codigo(), v.abertura(),
                        v.maxima(), v.minima(), v.fechamento(), v.volume(), null, v.volumeFinanceiro(), v.fonte())).toList()).size() > 0;
        if (salto) limitacoes.add("Salto de preço bruto detectado: retorno com proventos suspenso até conciliar o evento.");
        boolean soOficial = !periodo.isEmpty() && periodo.stream().allMatch(v -> "B3_COTAHIST".equals(v.fonte()));
        if (!soOficial) limitacoes.add("Período sem cobertura integral de preços oficiais COTAHIST.");
        LocalDate coleta = distribuicoes.stream().filter(p -> temIsin && isin.equalsIgnoreCase(p.getIsin()))
                .map(ProventoDistribuidoEntity::getAtualizadoEm).filter(Objects::nonNull).map(d -> d.toLocalDate())
                .max(Comparator.naturalOrder()).orElse(null);
        // A tabela não registra cobertura de coletas vazias nem comprova completude do calendário.
        // Mesmo uma marca EJ com valor associado não prova que todos os pagamentos do período foram coletados.
        boolean cobertura = false;
        limitacoes.add("O cache B3 não registra cobertura integral das coletas; valores oficiais identificados são parciais e não comprovam retorno total.");
        if (coleta == null || de.isBefore(coleta.minusMonths(12)))
            limitacoes.add("A fonte coleta janela móvel de cerca de 12 meses; não garante histórico anterior.");
        BigDecimal retornoPreco = periodo.isEmpty() ? null : percentual(periodo.get(periodo.size() - 1).fechamento() == null
                || periodo.get(0).abertura() == null ? null : periodo.get(periodo.size() - 1).fechamento().subtract(periodo.get(0).abertura()), periodo.get(0).abertura());
        List<BigDecimal> ajustes = monetarios.stream().map(Evento::ajustePercentual).filter(Objects::nonNull).toList();
        BigDecimal media = ajustes.isEmpty() ? null : ajustes.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(ajustes.size()), 4, RoundingMode.HALF_UP);
        List<LocalDate> datas = monetarios.stream().map(Evento::dataEx).distinct().sorted().toList();
        BigDecimal intervalo = datas.size() < 2 ? null : BigDecimal.valueOf(ChronoUnit.DAYS.between(datas.get(0), datas.get(datas.size() - 1)))
                .divide(BigDecimal.valueOf(datas.size() - 1), 1, RoundingMode.HALF_UP);
        int meses = (int) datas.stream().map(YearMonth::from).distinct().count();
        Resumo resumo = new Resumo(total, jcp, dividendos, monetarios.size(), comValor, retornoPreco,
                null, null, media, meses, intervalo, datas.isEmpty() ? null : datas.get(datas.size() - 1), cobertura, List.copyOf(limitacoes));
        String sintese = "No período foram identificadas " + monetarios.size() + " datas ex monetárias em " + meses
                + " meses, com valor oficial associado aos tipos identificados em " + comValor + ". "
                + (retornoPreco == null ? "Variação de preço indisponível. " : "Variação do preço bruto: " + retornoPreco.toPlainString() + "%. ")
                + "Proventos contextualizam a remuneração ao acionista e os movimentos nas datas ex; "
                + "não alteram sozinhos a recomendação holística. Retorno com proventos indisponível sem cobertura comprovada.";
        return new ProventosResumoDTO(simbolo, de, ate, List.copyOf(eventos), resumo, sintese, PREMISSA);
    }

    private static BigDecimal soma(List<Evento> eventos, String tipo) {
        List<BigDecimal> valores = eventos.stream().flatMap(e -> e.pagamentos().stream())
                .filter(p -> tipo == null || tipo.equals(p.tipo())).map(Pagamento::valorPorAcao).toList();
        return valores.isEmpty() ? null : valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static boolean valorCompleto(Evento e) {
        for (char c : e.marca().substring(1).toCharArray()) {
            String t = tipoLetra(c);
            if (monetario(t) && e.pagamentos().stream().noneMatch(p -> t.equals(p.tipo()))) return false;
        }
        return e.valorPorAcao() != null;
    }
    private static boolean contemMonetario(String m) { return m.matches(".*[DJR].*"); }
    private static boolean monetario(String t) { return Set.of("JCP", "DIVIDENDO", "RENDIMENTO").contains(t); }
    private static String tipo(String valor) {
        String t = valor == null ? "" : valor.toUpperCase(Locale.ROOT);
        if (t.contains("JCP") || t.contains("JUROS") || t.contains("JRS")) return "JCP";
        if (t.contains("DIVID")) return "DIVIDENDO";
        return t.contains("REND") ? "RENDIMENTO" : "OUTRO";
    }
    private static String tipoLetra(char c) {
        return switch(c) { case 'D' -> "DIVIDENDO"; case 'J' -> "JCP"; case 'R' -> "RENDIMENTO";
            case 'B' -> "BONIFICACAO"; case 'G' -> "GRUPAMENTO"; case 'S' -> "SUBSCRICAO";
            case 'X' -> "DESDOBRAMENTO"; default -> "OUTRO"; };
    }
    private static String tipos(String marca) {
        return marca.substring(1).chars().mapToObj(c -> tipoLetra((char)c)).distinct().reduce((a,b) -> a + " + " + b).orElse("OUTRO");
    }
    private static String marca(String valor) {
        String m = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (!m.startsWith("E")) return "";
        String letras = m.substring(1).replaceAll("[E\\s+/,;-]", "");
        if (!letras.matches("[A-Z]+")) return "";
        StringBuilder s = new StringBuilder("E");
        letras.chars().distinct().sorted().forEach(c -> s.append((char)c));
        return s.toString();
    }
    private static String combinarMarcas(String a, String b) {
        StringBuilder s = new StringBuilder("E");
        (a.substring(1) + b.substring(1)).chars().distinct().sorted().forEach(c -> s.append((char)c));
        return s.toString();
    }
    private static BigDecimal percentual(BigDecimal valor, BigDecimal base) {
        return valor == null || base == null || base.signum() <= 0 ? null
                : valor.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }
}
