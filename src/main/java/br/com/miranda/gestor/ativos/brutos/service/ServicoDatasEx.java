package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.ProventoDistribuidoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.DatasExDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDatasEx;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDatasEx.LinhaDataEx;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioProventoDistribuido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Calendario de datas ex (MICRO-GES-2). Fonte primaria data_ex_b3; sem linhas
 * para o simbolo, calcula ao vivo do COTAHIST. O mapa de marcas e hipotese de
 * trabalho (DEC-MICRO-1, a validar contra o layout da B3).
 */
@Service
@RequiredArgsConstructor
public class ServicoDatasEx {

    static final String ORIGEM_TABELA = "DATA_EX_B3";
    static final String ORIGEM_AO_VIVO = "COTAHIST_AO_VIVO";
    private static final int JANELA_PROVENTO_DIAS = 3;

    private final RepositorioDatasEx repositorioDatasEx;
    private final RepositorioProventoDistribuido repositorioProventos;

    public DatasExDTO calendario(String simbolo, LocalDate desde) {
        return calendario(simbolo, desde, LocalDate.now());
    }

    DatasExDTO calendario(String simboloEntrada, LocalDate desdeEntrada, LocalDate hoje) {
        String simbolo = simboloEntrada.trim().toUpperCase(Locale.ROOT);
        LocalDate desde = desdeEntrada != null ? desdeEntrada : hoje.minusMonths(24);

        List<LinhaDataEx> linhas = repositorioDatasEx.daTabela(simbolo, desde);
        String origem = ORIGEM_TABELA;
        if (linhas == null || linhas.isEmpty()) {
            linhas = primeiroDiaDeCadaJanela(repositorioDatasEx.aoVivo(simbolo, desde));
            origem = ORIGEM_AO_VIVO;
        }
        List<ProventoDistribuidoEntity> proventos = linhas.isEmpty() ? List.of()
                : repositorioProventos.findBySimboloOrderByDataPagamentoDesc(emissor(simbolo));

        List<DatasExDTO.Evento> eventos = new ArrayList<>();
        for (LinhaDataEx l : linhas) {
            eventos.add(montarEvento(l, proventos, ORIGEM_AO_VIVO.equals(origem)));
        }
        eventos.sort(Comparator.comparing(DatasExDTO.Evento::dataEx));
        return new DatasExDTO(simbolo, origem, desde, eventos, resumir(eventos, hoje));
    }

    /**
     * O COTAHIST repete a marca ex em varios pregoes seguidos (cerca de 8 dias);
     * o dia ex e o primeiro da janela: mesma marca com no maximo 5 dias corridos
     * desde a linha anterior continua a janela.
     */
    static List<LinhaDataEx> primeiroDiaDeCadaJanela(List<LinhaDataEx> linhas) {
        List<LinhaDataEx> saida = new ArrayList<>();
        LinhaDataEx anterior = null;
        for (LinhaDataEx l : linhas) {
            boolean continua = anterior != null && anterior.marca().equals(l.marca())
                    && ChronoUnit.DAYS.between(anterior.dataEx(), l.dataEx()) <= 5;
            if (!continua) saida.add(l);
            anterior = l;
        }
        return saida;
    }

    private DatasExDTO.Evento montarEvento(LinhaDataEx l, List<ProventoDistribuidoEntity> proventos, boolean aoVivo) {
        String tipo = aoVivo || l.tipo() == null ? tipoDaMarca(l.marca()) : l.tipo();
        BigDecimal ajuste = l.ajusteImplicito();
        BigDecimal pct = l.ajustePercentual();
        BigDecimal fech = l.fechamentoAnterior();
        if (ajuste == null && fech != null && l.aberturaEx() != null) {
            ajuste = fech.subtract(l.aberturaEx());
        }
        if (pct == null && ajuste != null && fech != null && fech.signum() > 0) {
            pct = ajuste.multiply(BigDecimal.valueOf(100)).divide(fech, 4, RoundingMode.HALF_UP);
        }
        BigDecimal valor = l.valorDeclarado();
        String origemValor = l.origemValor();
        if (valor == null) {
            BigDecimal juntado = valorDeProventos(l.dataEx(), l.marca(), tipo, proventos);
            if (juntado != null) {
                valor = juntado;
                origemValor = "PROVENTO_B3";
            }
        }
        BigDecimal razao = valor != null && valor.signum() > 0 && ajuste != null
                ? ajuste.divide(valor, 4, RoundingMode.HALF_UP) : null;
        BigDecimal yield = valor != null && fech != null && fech.signum() > 0
                ? valor.divide(fech, 6, RoundingMode.HALF_UP) : null;
        return new DatasExDTO.Evento(l.dataEx(), l.marca(), tipo, fech, l.aberturaEx(), ajuste, pct,
                valor, origemValor, razao, yield);
    }

    /** Mapa de marcas (hipotese a validar); combinacao usa a primeira letra reconhecida. */
    static String tipoDaMarca(String marca) {
        if (marca == null) return "OUTRO";
        String m = marca.trim().toUpperCase(Locale.ROOT);
        for (int i = 1; i < m.length(); i++) {
            String t = switch (m.charAt(i)) {
                case 'B' -> "BONIFICACAO";
                case 'D' -> "DIVIDENDO";
                case 'G' -> "GRUPAMENTO";
                case 'J' -> "JCP";
                case 'R' -> "RENDIMENTO";
                case 'S' -> "SUBSCRICAO";
                case 'X' -> "DESDOBRAMENTO";
                default -> null;
            };
            if (t != null) return t;
        }
        return "OUTRO";
    }

    /** Provento ligado ao evento: data ex = data com + 1, tolerancia de 3 dias; combinacao soma. */
    private BigDecimal valorDeProventos(LocalDate dataEx, String marca, String tipo,
                                        List<ProventoDistribuidoEntity> proventos) {
        List<ProventoDistribuidoEntity> proximos = proventos.stream()
                .filter(p -> p.getUltimaDataComDireito() != null && p.getValorPorAcao() != null)
                .filter(p -> distancia(p, dataEx) <= JANELA_PROVENTO_DIAS)
                .toList();
        if (proximos.isEmpty()) return null;
        if (marca != null && marca.length() > 2) {
            return proximos.stream().map(ProventoDistribuidoEntity::getValorPorAcao)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return proximos.stream()
                .filter(p -> tipoCompativel(p.getTipo(), tipo))
                .min(Comparator.comparingLong(p -> distancia(p, dataEx)))
                .map(ProventoDistribuidoEntity::getValorPorAcao)
                .orElse(null);
    }

    private static long distancia(ProventoDistribuidoEntity p, LocalDate dataEx) {
        return Math.abs(ChronoUnit.DAYS.between(p.getUltimaDataComDireito().plusDays(1), dataEx));
    }

    private static boolean tipoCompativel(String tipoProvento, String tipoEvento) {
        if (tipoProvento == null) return false;
        String t = tipoProvento.toUpperCase(Locale.ROOT);
        boolean jcp = t.contains("JCP") || t.contains("JUROS") || t.contains("JRS");
        return switch (tipoEvento) {
            case "JCP" -> jcp;
            case "DIVIDENDO" -> t.contains("DIVID") && !jcp;
            case "RENDIMENTO" -> t.contains("REND");
            default -> false;
        };
    }

    private DatasExDTO.Resumo resumir(List<DatasExDTO.Evento> eventos, LocalDate hoje) {
        LocalDate limite = hoje.minusMonths(12);
        List<DatasExDTO.Evento> ultimos = eventos.stream()
                .filter(e -> e.dataEx().isAfter(limite) && !e.dataEx().isAfter(hoje)).toList();
        BigDecimal soma = ultimos.stream().map(DatasExDTO.Evento::yieldDoEvento)
                .filter(y -> y != null).reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate proxima = null;
        LocalDate baseEscolhida = null;
        String tipoProx = null;
        String confianca = null;
        for (String tipo : eventos.stream().map(DatasExDTO.Evento::tipo).distinct().toList()) {
            List<LocalDate> datas = eventos.stream().filter(e -> e.tipo().equals(tipo))
                    .map(DatasExDTO.Evento::dataEx).toList();
            // ocorrencia mais recente com 12+ meses de idade
            LocalDate base = datas.stream().filter(d -> !d.isAfter(limite)).max(Comparator.naturalOrder())
                    .orElse(null);
            if (base == null) continue;
            LocalDate proj = base;
            while (!proj.isAfter(hoje)) proj = proj.plusYears(1);
            if (proxima == null || proj.isBefore(proxima)) {
                proxima = proj;
                baseEscolhida = base;
                tipoProx = tipo;
                confianca = repeteAno(datas, base) ? "MEDIA" : "BAIXA";
            }
        }
        String leitura;
        if (proxima == null) {
            leitura = "Sem historico de 12 meses ou mais para estimar a proxima data ex.";
        } else {
            leitura = "Estimativa por sazonalidade: " + tipoProx + " por volta de " + proxima
                    + ", repetindo a ocorrencia de " + baseEscolhida
                    + ("MEDIA".equals(confianca) ? " (padrao repetido em mais de um ano)."
                    : " (uma unica ocorrencia no historico; apenas indicativo).");
        }
        return new DatasExDTO.Resumo(ultimos.size(), soma, proxima, tipoProx, confianca, leitura);
    }

    /** MEDIA quando outra ocorrencia do tipo cai a ate 20 dias do mesmo dia do ano, em outro ano. */
    private static boolean repeteAno(List<LocalDate> datas, LocalDate base) {
        return datas.stream().anyMatch(d -> d.getYear() != base.getYear()
                && Math.abs(d.getDayOfYear() - base.getDayOfYear()) <= 20);
    }

    private static String emissor(String simbolo) {
        return simbolo.replaceAll("\\d+$", "");
    }
}
