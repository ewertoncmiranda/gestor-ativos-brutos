package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Leituras para a saude dos dados (contrato CTR-12): idade de cada fonte,
 * cobertura por ativo e checagem cruzada BRAPI x COTAHIST. So le; as tabelas
 * sao de varios donos (gestor, ETL, gerar-insights), por isso SQL explicito.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioSaudeDados {

    private final NamedParameterJdbcTemplate jdbc;

    public record CoberturaAtivo(String simbolo, String tipoColeta, String cnpj, LocalDate ultimaVela,
                                 LocalDateTime cotacaoEm, LocalDate periodoAnual, LocalDate entregaAnual,
                                 LocalDate periodoTtm, LocalDateTime ultimoInsight, LocalDate ultimoPregaoB3) {
    }

    public record Divergencia(String simbolo, LocalDate data, BigDecimal fechamentoBrapi,
                              BigDecimal fechamentoB3, BigDecimal diferenca) {
    }

    public record Alias(String simbolo, String canonico) {
    }

    /** Momento da ultima atualizacao de cada fonte; null quando nunca houve dado. */
    public Map<String, LocalDateTime> ultimasAtualizacoes() {
        Map<String, LocalDateTime> saida = new java.util.LinkedHashMap<>();
        saida.put("COTACAO", instante("SELECT MAX(atualizado_em) FROM cotacao_atual"));
        saida.put("VELAS", inicioDoDia("SELECT MAX(data) FROM candle_diario"));
        saida.put("CDI", inicioDoDia("SELECT MAX(data) FROM indice_macro WHERE codigo_serie = 'CDI'"));
        saida.put("DIARIO", instante("SELECT MAX(registrado_em) FROM sinal_diario"));
        // Camada Base (infra V13): insight diario sobre o preco oficial.
        saida.put("INSIGHTS_BASE", instante("SELECT MAX(data_analise) FROM insight_acao "
                + "WHERE JSON_UNQUOTE(JSON_EXTRACT(detalhes_json, '$.fonte_preco')) = 'B3_COTAHIST'"));
        for (String fonte : List.of("CVM_DFP", "CVM_TTM", "CVM_IPE", "B3_COTAHIST", "BACKUP_MYSQL", "BACKTEST")) {
            saida.put(fonte, instante(
                    "SELECT MAX(finalizado_em) FROM etl_execucao WHERE fonte = '" + fonte + "' "
                            + "AND status IN ('SUCESSO', 'PULADO')"));
        }
        return saida;
    }

    /**
     * Ultimo pregao do COTAHIST e ultimo pregao que ja tem insight da camada
     * Base. A idade de MAX(data_analise) nao basta: com o consumidor parado ela
     * continua "recente" por dias dentro do prazo, e o buraco nao aparece.
     */
    public record CoberturaInsightsBase(LocalDate ultimoPregaoB3, LocalDate ultimoPregaoComInsight) {
    }

    public CoberturaInsightsBase coberturaInsightsBase() {
        Date pregao = jdbc.getJdbcTemplate().queryForObject("SELECT MAX(data_pregao) FROM cotacao_b3_diaria", Date.class);
        Date comInsight = jdbc.getJdbcTemplate().queryForObject(
                "SELECT MAX(CAST(JSON_UNQUOTE(JSON_EXTRACT(detalhes_json, '$.data_pregao_referencia')) AS DATE)) "
                        + "FROM insight_acao "
                        + "WHERE JSON_UNQUOTE(JSON_EXTRACT(detalhes_json, '$.fonte_preco')) = 'B3_COTAHIST'",
                Date.class);
        return new CoberturaInsightsBase(pregao == null ? null : pregao.toLocalDate(),
                comInsight == null ? null : comInsight.toLocalDate());
    }

    /** Ultima falha registrada por fonte, para a tela dizer o que quebrou. */
    public Map<String, String> ultimosErros() {
        return jdbc.query(
                "SELECT e.fonte, e.mensagem_erro FROM etl_execucao e "
                        + "JOIN (SELECT fonte, MAX(id) id FROM etl_execucao GROUP BY fonte) u ON u.id = e.id "
                        + "WHERE e.status = 'ERRO'",
                Map.of(),
                rs -> {
                    Map<String, String> erros = new java.util.HashMap<>();
                    while (rs.next()) {
                        erros.put(rs.getString("fonte"), rs.getString("mensagem_erro"));
                    }
                    return erros;
                });
    }

    public List<CoberturaAtivo> cobertura() {
        return jdbc.query(
                "SELECT a.simbolo, a.tipo_coleta, t.cnpj, "
                        // vela da BRAPI (favoritos) ou fechamento oficial (todos, infra V13)
                        + " NULLIF(GREATEST("
                        + "   COALESCE((SELECT MAX(c.data) FROM candle_diario c WHERE c.simbolo = a.simbolo), '1900-01-01'),"
                        + "   COALESCE((SELECT MAX(b.data_pregao) FROM cotacao_b3_diaria b WHERE b.simbolo = a.simbolo), '1900-01-01')"
                        + " ), '1900-01-01') ultima_vela, "
                        + " (SELECT q.atualizado_em FROM cotacao_atual q WHERE q.simbolo = a.simbolo) cotacao_em, "
                        + " (SELECT MAX(f.periodo) FROM indicador_fundamentalista f "
                        + "   WHERE f.simbolo = a.simbolo AND f.tipo_periodo = 'ANUAL') periodo_anual, "
                        + " (SELECT MAX(f.data_entrega) FROM indicador_fundamentalista f "
                        + "   WHERE f.simbolo = a.simbolo AND f.tipo_periodo = 'ANUAL') entrega_anual, "
                        + " (SELECT MAX(f.periodo) FROM indicador_fundamentalista f "
                        + "   WHERE f.simbolo = a.simbolo AND f.tipo_periodo = 'TTM') periodo_ttm, "
                        + " (SELECT MAX(i.data_analise) FROM insight_acao i WHERE i.simbolo = a.simbolo) ultimo_insight, "
                        + " (SELECT MAX(b.data_pregao) FROM cotacao_b3_diaria b WHERE b.simbolo = a.simbolo) ultimo_b3 "
                        + "FROM ativo_monitorado a LEFT JOIN cvm_ticker t ON t.simbolo = a.simbolo "
                        + "WHERE a.ativo = TRUE ORDER BY a.simbolo",
                Map.of(),
                (rs, i) -> new CoberturaAtivo(rs.getString("simbolo"), rs.getString("tipo_coleta"),
                        rs.getString("cnpj"), data(rs, "ultima_vela"), instante(rs, "cotacao_em"),
                        data(rs, "periodo_anual"), data(rs, "entrega_anual"), data(rs, "periodo_ttm"),
                        instante(rs, "ultimo_insight"), data(rs, "ultimo_b3")));
    }

    /**
     * Fechamento da BRAPI contra o oficial do COTAHIST nas datas em comum dos
     * ultimos {@code dias} dias. O COTAHIST guarda o codigo do dia; a
     * identidade leva ELET3 de volta para AXIA3.
     */
    public List<Divergencia> comparacaoDePrecos(int dias, BigDecimal limite) {
        return jdbc.query(
                "SELECT c.simbolo, c.data, c.close brapi, b.fechamento b3, "
                        + " ABS(c.close - b.fechamento) / b.fechamento diferenca "
                        + "FROM candle_diario c "
                        + "JOIN cotacao_b3_diaria b ON b.data_pregao = c.data "
                        + " AND (b.simbolo = c.simbolo OR b.simbolo IN "
                        + "   (SELECT i.simbolo FROM ativo_identidade i "
                        + "     WHERE i.simbolo_canonico = c.simbolo AND i.continuidade_preco = 1)) "
                        + "WHERE c.data >= CURDATE() - INTERVAL :dias DAY AND b.fechamento > 0 "
                        + " AND ABS(c.close - b.fechamento) / b.fechamento > :limite "
                        + "ORDER BY diferenca DESC LIMIT 50",
                new MapSqlParameterSource("dias", dias).addValue("limite", limite),
                (rs, i) -> new Divergencia(rs.getString("simbolo"), data(rs, "data"),
                        rs.getBigDecimal("brapi"), rs.getBigDecimal("b3"), rs.getBigDecimal("diferenca")));
    }

    /** Quantos pares (ativo, dia) as duas fontes tem em comum na janela. */
    public long paresComparados(int dias) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM candle_diario c "
                        + "JOIN cotacao_b3_diaria b ON b.data_pregao = c.data "
                        + " AND (b.simbolo = c.simbolo OR b.simbolo IN "
                        + "   (SELECT i.simbolo FROM ativo_identidade i "
                        + "     WHERE i.simbolo_canonico = c.simbolo AND i.continuidade_preco = 1)) "
                        + "WHERE c.data >= CURDATE() - INTERVAL :dias DAY",
                new MapSqlParameterSource("dias", dias), Long.class);
        return total == null ? 0 : total;
    }

    /** Codigo antigo ainda no universo: o registro devia ter sido canonizado. */
    public List<Alias> aliasesNoUniverso() {
        return jdbc.query(
                "SELECT a.simbolo, i.simbolo_canonico FROM ativo_monitorado a "
                        + "JOIN ativo_identidade i ON i.simbolo = a.simbolo "
                        + "WHERE a.ativo = TRUE AND i.simbolo <> i.simbolo_canonico",
                Map.of(), (rs, i) -> new Alias(rs.getString(1), rs.getString(2)));
    }

    private LocalDateTime instante(String sql) {
        Timestamp valor = jdbc.getJdbcTemplate().queryForObject(sql, Timestamp.class);
        return valor == null ? null : valor.toLocalDateTime();
    }

    private LocalDateTime inicioDoDia(String sql) {
        Date valor = jdbc.getJdbcTemplate().queryForObject(sql, Date.class);
        return valor == null ? null : valor.toLocalDate().atStartOfDay();
    }

    private static LocalDate data(ResultSet rs, String coluna) throws SQLException {
        Date valor = rs.getDate(coluna);
        return valor == null ? null : valor.toLocalDate();
    }

    private static LocalDateTime instante(ResultSet rs, String coluna) throws SQLException {
        Timestamp valor = rs.getTimestamp(coluna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
