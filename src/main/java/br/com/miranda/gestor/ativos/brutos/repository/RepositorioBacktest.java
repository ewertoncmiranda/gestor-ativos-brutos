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
import java.util.Optional;

/**
 * Leitura do backtest walk-forward ({@code backtest_execucao},
 * {@code backtest_placar}; contrato CTR-14). O gerar-insights e o unico
 * escritor; aqui so se le a execucao bem-sucedida mais recente.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioBacktest {

    private final NamedParameterJdbcTemplate jdbc;

    public record Execucao(long id, LocalDateTime iniciadoEm, LocalDateTime finalizadoEm, LocalDate inicioPeriodo,
                           LocalDate fimPeriodo, LocalDate corteCalibracao, int ativos, int sinais,
                           String parametrosJson, String observacoes) {
    }

    public record Linha(String versaoRegra, String periodo, String recomendacao, int horizonte, int avaliados,
                        Integer acertos, BigDecimal taxaBase, BigDecimal retornoMedio,
                        BigDecimal excessoMedioCdi, BigDecimal excessoMedioCarteira,
                        Long nExcessoCdi, BigDecimal desvioExcessoCdi,
                        Long nExcessoCarteira, BigDecimal desvioExcessoCarteira, int janelasComProvento,
                        BigDecimal icAcertoInferior, BigDecimal icAcertoSuperior,
                        BigDecimal icExcessoCarteiraInferior, BigDecimal icExcessoCarteiraSuperior,
                        Integer mesesBootstrap) {
    }

    public Optional<Execucao> ultimaExecucao() {
        return jdbc.query(
                "SELECT * FROM backtest_execucao WHERE status = 'SUCESSO' ORDER BY id DESC LIMIT 1",
                Map.of(),
                (rs, i) -> new Execucao(rs.getLong("id"), instante(rs, "iniciado_em"), instante(rs, "finalizado_em"),
                        data(rs, "inicio_periodo"), data(rs, "fim_periodo"), data(rs, "corte_calibracao"),
                        rs.getInt("ativos"), rs.getInt("sinais"), rs.getString("parametros_json"),
                        rs.getString("observacoes"))).stream().findFirst();
    }

    public List<Linha> placar(long execucaoId) {
        return jdbc.query(
                "SELECT * FROM backtest_placar WHERE execucao_id = :id "
                        + "ORDER BY versao_regra DESC, periodo, recomendacao, horizonte",
                new MapSqlParameterSource("id", execucaoId),
                (rs, i) -> new Linha(rs.getString("versao_regra"), rs.getString("periodo"),
                        rs.getString("recomendacao"), rs.getInt("horizonte"), rs.getInt("avaliados"),
                        rs.getObject("acertos", Integer.class), rs.getBigDecimal("taxa_base"),
                        rs.getBigDecimal("retorno_medio"), rs.getBigDecimal("excesso_medio_cdi"),
                        rs.getBigDecimal("excesso_medio_carteira"),
                        rs.getObject("n_excesso_cdi", Long.class), rs.getBigDecimal("desvio_excesso_cdi"),
                        rs.getObject("n_excesso_carteira", Long.class), rs.getBigDecimal("desvio_excesso_carteira"),
                        rs.getInt("janelas_com_provento"),
                        rs.getBigDecimal("ic_acerto_inferior"), rs.getBigDecimal("ic_acerto_superior"),
                        rs.getBigDecimal("ic_excesso_carteira_inferior"),
                        rs.getBigDecimal("ic_excesso_carteira_superior"),
                        rs.getObject("meses_bootstrap", Integer.class)));
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
