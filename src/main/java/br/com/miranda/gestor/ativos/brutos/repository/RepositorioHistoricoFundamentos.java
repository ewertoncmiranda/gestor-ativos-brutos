package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.HistoricoFundamentosDTO.Ponto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RepositorioHistoricoFundamentos {
    private static final List<String> METRICAS = List.of("roe", "roic", "margem_liquida", "divida_liquida",
            "ebitda", "liquidez_corrente", "lpa", "vpa", "receita_liquida", "lucro_liquido");
    private final NamedParameterJdbcTemplate jdbc;

    public List<Ponto> buscar(String simbolo, int anos) {
        try {
            Set<String> colunas = new HashSet<>(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'indicador_fundamentalista'",
                    new MapSqlParameterSource(), String.class));
            if (!colunas.containsAll(List.of("periodo", "tipo_periodo", "simbolo"))) return List.of();
            String metricas = METRICAS.stream().map(c -> (colunas.contains(c) ? c : "NULL") + " AS " + c)
                    .reduce((a, b) -> a + ", " + b).orElse("");
            List<Ponto> consolidados = consultar(simbolo, anos, colunas.contains("grupo") ? "con" : null, metricas);
            List<Ponto> linhas = consolidados.isEmpty() ? consultar(simbolo, anos, null, metricas) : consolidados;
            Map<Date, Ponto> porPeriodo = new LinkedHashMap<>();
            for (Ponto ponto : linhas) {
                Ponto anterior = porPeriodo.get(ponto.periodo());
                if (anterior == null || ("ANUAL".equals(ponto.tipoPeriodo()) && !"ANUAL".equals(anterior.tipoPeriodo()))) {
                    porPeriodo.put(ponto.periodo(), ponto);
                }
            }
            return List.copyOf(porPeriodo.values());
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private List<Ponto> consultar(String simbolo, int anos, String grupo, String metricas) {
        String filtroGrupo = grupo == null ? "" : " AND grupo = :grupo";
        String sql = "SELECT periodo, tipo_periodo, " + metricas + " FROM indicador_fundamentalista "
                + "WHERE simbolo = :simbolo AND tipo_periodo IN ('ANUAL','TTM') "
                + "AND periodo >= DATE_SUB(CURRENT_DATE(), INTERVAL :anos YEAR)" + filtroGrupo
                + " ORDER BY periodo ASC, CASE tipo_periodo WHEN 'ANUAL' THEN 0 ELSE 1 END";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("simbolo", simbolo).addValue("anos", anos);
        if (grupo != null) params.addValue("grupo", grupo);
        return jdbc.query(sql, params, (rs, i) -> {
            Date periodo = rs.getDate("periodo");
            return new Ponto(periodo == null ? null : periodo.toLocalDate(), rs.getString("tipo_periodo"),
                    rs.getBigDecimal("roe"), rs.getBigDecimal("roic"), rs.getBigDecimal("margem_liquida"),
                    rs.getBigDecimal("divida_liquida"), rs.getBigDecimal("ebitda"), rs.getBigDecimal("liquidez_corrente"),
                    rs.getBigDecimal("lpa"), rs.getBigDecimal("vpa"), rs.getBigDecimal("receita_liquida"),
                    rs.getBigDecimal("lucro_liquido"));
        });
    }
}
