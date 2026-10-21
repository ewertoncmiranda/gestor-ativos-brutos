package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.PainelMacroDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.PainelMacroDTO.AtivoAcima;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Painel macro (MACRO-GES-3): le so o cache indice_macro e o ultimo insight de
 * cada acao; nunca chama o Banco Central. Serie ausente vira null + motivo.
 */
@Slf4j
@Service
public class ServicoPainelMacro {

    static final List<String> SERIES = List.of("SELIC", "CDI", "IPCA", "IGPM", "IPCA_12M",
            "DOLAR", "EURO", "IBCBR", "DESEMPREGO");
    private static final Set<String> EM_PERCENTUAL = Set.of("DOLAR", "EURO", "IBCBR");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal TOLERANCIA_CICLO = new BigDecimal("0.01");
    private static final int LIMITE_ATIVOS = 20;
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final String FONTE_EY =
            "insight_acao.detalhes_json.valuation.earnings_yield_percent (ultimo insight por simbolo)";

    /** Earnings yield do ultimo insight de cada simbolo (mesma escolha MAX(id) da listagem de ativos). */
    static final String SQL_EARNINGS_YIELD = """
            SELECT i.simbolo,
                   CAST(JSON_UNQUOTE(JSON_EXTRACT(i.detalhes_json, '$.valuation.earnings_yield_percent')) AS DECIMAL(12,4)) AS ey
            FROM insight_acao i
            WHERE i.id IN (SELECT MAX(i2.id) FROM insight_acao i2 GROUP BY i2.simbolo)
              AND JSON_TYPE(JSON_EXTRACT(i.detalhes_json, '$.valuation.earnings_yield_percent')) IN ('DOUBLE', 'INTEGER', 'DECIMAL')
            """;

    private final RepositorioIndiceMacro repositorio;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ServicoPainelMacro(RepositorioIndiceMacro repositorio, NamedParameterJdbcTemplate jdbc) {
        this(repositorio, jdbc, Clock.systemDefaultZone());
    }

    ServicoPainelMacro(RepositorioIndiceMacro repositorio, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.repositorio = repositorio;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public PainelMacroDTO montar() {
        LocalDate hoje = LocalDate.now(clock);
        List<PainelMacroDTO.Serie> series = SERIES.stream().map(this::serie).toList();
        List<IndiceMacroEntity> selic = pontos("SELIC");
        BigDecimal selicMeta = selic.isEmpty() ? null : selic.get(0).getValor();
        return new PainelMacroDTO(hoje, series, juroReal(hoje, selicMeta), ciclo(selic), barreira(selicMeta));
    }

    // ------------------------------------------------------------------ series

    PainelMacroDTO.Serie serie(String codigo) {
        List<IndiceMacroEntity> p = pontos(codigo);
        boolean pct = EM_PERCENTUAL.contains(codigo);
        String unidade = pct ? "%" : "p.p.";
        if (p.isEmpty()) {
            return new PainelMacroDTO.Serie(codigo, null, null, null, null, null, unidade, null,
                    "Serie " + codigo + " sem dados no cache indice_macro", null);
        }
        IndiceMacroEntity atual = p.get(0);
        BigDecimal v1 = variacao(p, 1, pct);
        BigDecimal v3 = variacao(p, 3, pct);
        BigDecimal v12 = variacao(p, 12, pct);
        BigDecimal acum = "IGPM".equals(codigo) ? acumulado12m(p) : null;
        String motivo = "IGPM".equals(codigo) && acum == null
                ? "IGP-M 12 meses indisponivel: faltam meses na serie mensal" : null;
        String leitura = codigo + " em " + fmt(atual.getValor()) + " em " + atual.getData()
                + (v12 == null ? "" : "; variacao de " + sinal(v12) + " " + unidade + " em 12 meses")
                + (acum == null ? "" : "; acumulado em 12 meses de " + fmt(acum) + "%")
                + ".";
        return new PainelMacroDTO.Serie(codigo, atual.getValor(), atual.getData(), v1, v3, v12,
                unidade, acum, motivo, leitura);
    }

    /** Variacao contra o ultimo ponto em ou antes de (data atual - meses). Null se nao houver. */
    static BigDecimal variacao(List<IndiceMacroEntity> desc, int meses, boolean percentual) {
        IndiceMacroEntity atual = desc.get(0);
        LocalDate alvo = atual.getData().minusMonths(meses);
        Optional<IndiceMacroEntity> base = desc.stream().filter(e -> !e.getData().isAfter(alvo)).findFirst();
        if (base.isEmpty()) return null;
        BigDecimal antes = base.get().getValor();
        if (!percentual) return escala(atual.getValor().subtract(antes));
        if (antes.signum() == 0) return null;
        return escala(atual.getValor().divide(antes, MC).subtract(BigDecimal.ONE).multiply(CEM));
    }

    /** Composto dos 12 ultimos meses consecutivos; null se faltar algum mes. */
    static BigDecimal acumulado12m(List<IndiceMacroEntity> desc) {
        YearMonth mes = YearMonth.from(desc.get(0).getData());
        BigDecimal fator = BigDecimal.ONE;
        for (int i = 0; i < 12; i++) {
            YearMonth procurado = mes.minusMonths(i);
            Optional<IndiceMacroEntity> ponto = desc.stream()
                    .filter(e -> YearMonth.from(e.getData()).equals(procurado)).findFirst();
            if (ponto.isEmpty()) return null;
            fator = fator.multiply(BigDecimal.ONE.add(ponto.get().getValor().divide(CEM, MC)), MC);
        }
        return escala(fator.subtract(BigDecimal.ONE).multiply(CEM));
    }

    // --------------------------------------------------------------- juro real

    PainelMacroDTO.JuroReal juroReal(LocalDate hoje, BigDecimal selicMeta) {
        int ano = hoje.getMonthValue() > 6 ? hoje.getYear() + 1 : hoje.getYear();
        String fonte = "FOCUS_IPCA_" + ano;
        List<IndiceMacroEntity> ipca = pontos(fonte);
        if (ipca.isEmpty()) {
            fonte = "IPCA_12M";
            ipca = pontos(fonte);
        }
        if (selicMeta == null) {
            return new PainelMacroDTO.JuroReal(null, null, null, null, "Selic meta ausente no cache", null);
        }
        if (ipca.isEmpty()) {
            return new PainelMacroDTO.JuroReal(null, selicMeta, null, null,
                    "Sem FOCUS_IPCA_" + ano + " nem IPCA_12M no cache", null);
        }
        BigDecimal esperado = ipca.get(0).getValor();
        BigDecimal real = calcularJuroReal(selicMeta, esperado);
        String leitura = "Juro real de " + fmt(real) + "%: a renda fixa paga isso sem risco; "
                + "ações precisam render mais que " + fmt(selicMeta) + "% de earnings yield.";
        return new PainelMacroDTO.JuroReal(real, selicMeta, esperado, fonte, null, leitura);
    }

    static BigDecimal calcularJuroReal(BigDecimal selic, BigDecimal ipca) {
        BigDecimal f = BigDecimal.ONE.add(selic.divide(CEM, MC))
                .divide(BigDecimal.ONE.add(ipca.divide(CEM, MC)), MC);
        return escala(f.subtract(BigDecimal.ONE).multiply(CEM));
    }

    // ------------------------------------------------------------------- ciclo

    PainelMacroDTO.CicloSelic ciclo(List<IndiceMacroEntity> selic) {
        if (selic.isEmpty()) {
            return new PainelMacroDTO.CicloSelic(null, null, null, null, "Selic meta ausente no cache", null);
        }
        IndiceMacroEntity atual = selic.get(0);
        LocalDate alvo = atual.getData().minusDays(90);
        Optional<IndiceMacroEntity> antes = selic.stream().filter(e -> !e.getData().isAfter(alvo)).findFirst();
        if (antes.isEmpty()) {
            return new PainelMacroDTO.CicloSelic(null, atual.getValor(), null, null,
                    "Historico da Selic nao cobre 90 dias", null);
        }
        BigDecimal anterior = antes.get().getValor();
        BigDecimal dif = atual.getValor().subtract(anterior);
        String ciclo = dif.compareTo(TOLERANCIA_CICLO) > 0 ? "ALTA"
                : dif.compareTo(TOLERANCIA_CICLO.negate()) < 0 ? "QUEDA" : "ESTAVEL";
        String leitura = switch (ciclo) {
            case "ALTA" -> "Selic em alta: " + fmt(anterior) + "% há 90 dias, " + fmt(atual.getValor()) + "% agora.";
            case "QUEDA" -> "Selic em queda: " + fmt(anterior) + "% há 90 dias, " + fmt(atual.getValor()) + "% agora.";
            default -> "Selic estável em " + fmt(atual.getValor()) + "% nos últimos 90 dias.";
        };
        return new PainelMacroDTO.CicloSelic(ciclo, atual.getValor(), anterior, antes.get().getData(), null, leitura);
    }

    // ---------------------------------------------------------------- barreira

    PainelMacroDTO.Barreira barreira(BigDecimal selicMeta) {
        List<AtivoAcima> base;
        try {
            base = jdbc.query(SQL_EARNINGS_YIELD, new MapSqlParameterSource(),
                    (rs, i) -> new AtivoAcima(rs.getString("simbolo"), rs.getBigDecimal("ey")));
            base = base.stream().filter(a -> a.earningsYield() != null).toList();
        } catch (Exception e) {
            log.error("Erro ao ler earnings yield da base: {}", e.getMessage(), e);
            base = null;
        }
        String motivo = null;
        BigDecimal mediano = null;
        List<AtivoAcima> acima = null;
        if (base == null) {
            motivo = "Falha ao ler earnings yield dos insights";
        } else if (base.isEmpty()) {
            motivo = "Sem earnings yield nos insights da base";
        } else {
            mediano = mediana(base.stream().map(AtivoAcima::earningsYield).toList());
            if (selicMeta == null) {
                motivo = "Selic meta ausente: barreira indefinida";
            } else {
                acima = base.stream().filter(a -> a.earningsYield().compareTo(selicMeta) > 0)
                        .sorted(Comparator.comparing(AtivoAcima::earningsYield).reversed()
                                .thenComparing(AtivoAcima::simbolo))
                        .limit(LIMITE_ATIVOS).toList();
            }
        }
        String leitura = selicMeta == null ? null
                : "Barreira de compra: earnings yield mínimo de " + fmt(selicMeta) + "% (Selic meta)"
                + (mediano == null ? "." : "; a mediana da base está em " + fmt(mediano) + "% e "
                + (mediano.compareTo(selicMeta) > 0 ? "supera" : "não supera") + " a barreira.");
        return new PainelMacroDTO.Barreira(selicMeta, mediano, acima,
                base == null || base.isEmpty() ? null : FONTE_EY, motivo, leitura);
    }

    static BigDecimal mediana(List<BigDecimal> valores) {
        List<BigDecimal> o = new ArrayList<>(valores);
        o.sort(Comparator.naturalOrder());
        int n = o.size();
        BigDecimal m = n % 2 == 1 ? o.get(n / 2)
                : o.get(n / 2 - 1).add(o.get(n / 2)).divide(BigDecimal.valueOf(2), MC);
        return escala(m);
    }

    // ------------------------------------------------------------------- apoio

    private List<IndiceMacroEntity> pontos(String codigo) {
        try {
            List<IndiceMacroEntity> lista = repositorio.findByCodigoSerieOrderByDataDesc(codigo);
            return lista == null ? List.of()
                    : lista.stream().filter(e -> e.getData() != null && e.getValor() != null).toList();
        } catch (Exception e) {
            log.error("Erro ao ler serie {}: {}", codigo, e.getMessage(), e);
            return List.of();
        }
    }

    private static BigDecimal escala(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static String fmt(BigDecimal v) {
        return new DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"))).format(v);
    }

    private static String sinal(BigDecimal v) {
        return (v.signum() > 0 ? "+" : "") + fmt(v);
    }
}
