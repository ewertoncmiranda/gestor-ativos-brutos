package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Leitura das fontes da V45 (cvm_cadastro, cvm_auditor, cvm_divida_valor_mobiliario, fundo_posicao_acao) e do payout. */
@Repository
@RequiredArgsConstructor
public class RepositorioConfiabilidade {

    public static final List<String> TABELAS_V45 =
            List.of("cvm_cadastro", "cvm_auditor", "cvm_divida_valor_mobiliario", "fundo_posicao_acao");

    public record Cadastro(String situacao, LocalDate dataSituacao, LocalDate dataCancelamento, String motivoCancelamento) {
    }

    public record Auditor(String cnpjAuditor, String nome, LocalDate dataInicio, LocalDate dataFim, String justificativa) {
    }

    public record Divida(LocalDate dataReferencia, int titulos, BigDecimal saldoTotal, BigDecimal saldoAte12Meses) {
    }

    public record CompetenciaFundos(LocalDate competencia, int fundos, BigDecimal quantidade) {
    }

    public record MovimentoFundos(int entraram, int sairam) {
    }

    /** Dividendos+JCP do ultimo DFP e lucro liquido anual do mesmo periodo (pode faltar). */
    public record Proventos(LocalDate periodo, BigDecimal total, BigDecimal lucroLiquido) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** Quais das tabelas da V45 existem no schema atual. */
    public Set<String> tabelasV45Existentes() {
        List<String> nomes = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name IN (:t)",
                new MapSqlParameterSource("t", TABELAS_V45), String.class);
        Set<String> lower = new HashSet<>();
        nomes.forEach(n -> lower.add(n.toLowerCase()));
        return lower;
    }

    public Optional<String> cnpjPorSimbolo(String simbolo) {
        List<String> r = jdbc.queryForList("SELECT cnpj FROM cvm_ticker WHERE simbolo = :s LIMIT 1",
                new MapSqlParameterSource("s", simbolo), String.class);
        return r.isEmpty() ? Optional.empty() : Optional.ofNullable(r.get(0));
    }

    public Optional<Cadastro> cadastro(String cnpj) {
        List<Cadastro> r = jdbc.query(
                "SELECT situacao, data_situacao, data_cancelamento, motivo_cancelamento FROM cvm_cadastro WHERE cnpj = :c",
                new MapSqlParameterSource("c", cnpj),
                (rs, i) -> new Cadastro(rs.getString("situacao"), data(rs, "data_situacao"),
                        data(rs, "data_cancelamento"), rs.getString("motivo_cancelamento")));
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    /** Auditores (mais recente primeiro). */
    public List<Auditor> auditores(String cnpj) {
        return jdbc.query(
                "SELECT cnpj_auditor, auditor, data_inicio, data_fim, justificativa_substituicao FROM cvm_auditor "
                        + "WHERE cnpj = :c ORDER BY data_inicio DESC, data_referencia DESC, versao DESC LIMIT 50",
                new MapSqlParameterSource("c", cnpj),
                (rs, i) -> new Auditor(rs.getString("cnpj_auditor"), rs.getString("auditor"),
                        data(rs, "data_inicio"), data(rs, "data_fim"), rs.getString("justificativa_substituicao")));
    }

    /** Dividas da referencia/versao mais recente; vazio quando a empresa nao tem linhas. */
    public Optional<Divida> divida(String cnpj, LocalDate hoje) {
        List<Divida> r = jdbc.query(
                "SELECT d.data_referencia, COUNT(*) AS titulos, COALESCE(SUM(d.saldo_devedor), 0) AS total, "
                        + "COALESCE(SUM(CASE WHEN d.data_vencimento >= :hoje AND d.data_vencimento <= :limite "
                        + "THEN d.saldo_devedor ELSE 0 END), 0) AS ate12 "
                        + "FROM cvm_divida_valor_mobiliario d "
                        + "JOIN (SELECT data_referencia, MAX(versao) AS versao FROM cvm_divida_valor_mobiliario "
                        + "      WHERE cnpj = :c AND data_referencia = (SELECT MAX(data_referencia) "
                        + "      FROM cvm_divida_valor_mobiliario WHERE cnpj = :c) GROUP BY data_referencia) u "
                        + "  ON u.data_referencia = d.data_referencia AND u.versao = d.versao "
                        + "WHERE d.cnpj = :c GROUP BY d.data_referencia",
                new MapSqlParameterSource("c", cnpj).addValue("hoje", Date.valueOf(hoje))
                        .addValue("limite", Date.valueOf(hoje.plusMonths(12))),
                (rs, i) -> new Divida(data(rs, "data_referencia"), rs.getInt("titulos"),
                        rs.getBigDecimal("total"), rs.getBigDecimal("ate12")));
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    /** Ate duas competencias mais recentes com o ativo, da mais nova para a mais antiga. */
    public List<CompetenciaFundos> competenciasFundos(String simbolo) {
        return jdbc.query(
                "SELECT competencia, COUNT(DISTINCT cnpj_fundo) AS fundos, COALESCE(SUM(quantidade), 0) AS qtd "
                        + "FROM fundo_posicao_acao WHERE simbolo = :s GROUP BY competencia "
                        + "ORDER BY competencia DESC LIMIT 2",
                new MapSqlParameterSource("s", simbolo),
                (rs, i) -> new CompetenciaFundos(data(rs, "competencia"), rs.getInt("fundos"), rs.getBigDecimal("qtd")));
    }

    public MovimentoFundos movimentoFundos(String simbolo, LocalDate atual, LocalDate anterior) {
        MapSqlParameterSource p = new MapSqlParameterSource("s", simbolo)
                .addValue("a", Date.valueOf(atual)).addValue("b", Date.valueOf(anterior));
        String base = "SELECT COUNT(DISTINCT x.cnpj_fundo) FROM fundo_posicao_acao x WHERE x.simbolo = :s "
                + "AND x.competencia = :%s AND x.cnpj_fundo NOT IN (SELECT y.cnpj_fundo FROM fundo_posicao_acao y "
                + "WHERE y.simbolo = :s AND y.competencia = :%s)";
        Integer entraram = jdbc.queryForObject(String.format(base, "a", "b"), p, Integer.class);
        Integer sairam = jdbc.queryForObject(String.format(base, "b", "a"), p, Integer.class);
        return new MovimentoFundos(entraram == null ? 0 : entraram, sairam == null ? 0 : sairam);
    }

    /**
     * Reaproveita as tabelas das leituras existentes: provento_contabil (/proventos-contabeis) e
     * indicador_fundamentalista (fundamentos CVM, ANUAL). Falha de schema = sem dado.
     */
    public Optional<Proventos> proventosEPayout(String simbolo) {
        try {
            List<Proventos> r = jdbc.query(
                    "SELECT pc.dt_fim_exerc, pc.total, "
                            + "(SELECT i.lucro_liquido FROM indicador_fundamentalista i WHERE i.simbolo = :s "
                            + " AND i.tipo_periodo = 'ANUAL' AND i.periodo = pc.dt_fim_exerc "
                            + " AND i.lucro_liquido IS NOT NULL LIMIT 1) AS lucro "
                            + "FROM provento_contabil pc JOIN cvm_ticker t ON t.cnpj = pc.cnpj "
                            + "WHERE t.simbolo = :s AND pc.tipo_doc = 'DFP' "
                            + "ORDER BY pc.dt_fim_exerc DESC, pc.versao DESC LIMIT 1",
                    new MapSqlParameterSource("s", simbolo),
                    (rs, i) -> new Proventos(data(rs, "dt_fim_exerc"), rs.getBigDecimal("total"),
                            rs.getBigDecimal("lucro")));
            return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    private static LocalDate data(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }
}
