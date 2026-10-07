package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leitura do backtest por ranking (Plano LAC, LAC-GES-1: {@code
 * backtest_execucao.metodo='RANKING'}, {@code backtest_ranking_mes},
 * {@code backtest_ranking_quintil}, infra V16). Por JDBC, sem entidade JPA -
 * com {@code ddl-auto=validate} uma entidade para tabela/coluna ainda nao
 * criada derrubaria o gestor na subida (mesma razao de
 * {@link RepositorioConsumoBrapi}). Sem a V16, cada metodo devolve vazio com
 * um aviso no log, nunca lanca pra o controller.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RepositorioBacktestRanking {

    private final NamedParameterJdbcTemplate jdbc;
    private final AtomicBoolean avisouAusente = new AtomicBoolean(false);

    public record Execucao(long id, LocalDateTime finalizadoEm, String hipotese, Integer numeroTentativa,
                           String esquemaValidacao) {
    }

    public record LinhaJanela(String versaoRegra, String janela, int horizonte,
                              BigDecimal icSpearmanMedio, BigDecimal icSpearmanDesvio, int meses,
                              Integer mediaAtivos) {
    }

    public record LinhaQuintil(String versaoRegra, String janela, int horizonte, int quintil,
                               BigDecimal retornoMedio, int ativos) {
    }

    public Optional<Execucao> ultimaExecucao() {
        try {
            return jdbc.query(
                    "SELECT id, finalizado_em, hipotese, numero_tentativa, esquema_validacao "
                            + "FROM backtest_execucao WHERE metodo = 'RANKING' AND status = 'SUCESSO' "
                            + "ORDER BY id DESC LIMIT 1",
                    java.util.Map.of(),
                    (rs, i) -> new Execucao(rs.getLong("id"), instante(rs, "finalizado_em"),
                            rs.getString("hipotese"), rs.getObject("numero_tentativa", Integer.class),
                            rs.getString("esquema_validacao"))).stream().findFirst();
        } catch (DataAccessException e) {
            avisarUmaVez(e);
            return Optional.empty();
        }
    }

    public List<LinhaJanela> janelas(long execucaoId) {
        try {
            return jdbc.query(
                    "SELECT versao_regra, janela, horizonte, "
                            + " AVG(ic_spearman) ic_medio, STDDEV_SAMP(ic_spearman) ic_desvio, "
                            + " COUNT(ic_spearman) meses, ROUND(AVG(n_ativos)) media_ativos "
                            + "FROM backtest_ranking_mes WHERE execucao_id = :id "
                            + "GROUP BY versao_regra, janela, horizonte "
                            + "ORDER BY versao_regra DESC, janela, horizonte",
                    new MapSqlParameterSource("id", execucaoId),
                    (rs, i) -> new LinhaJanela(rs.getString("versao_regra"), rs.getString("janela"),
                            rs.getInt("horizonte"), rs.getBigDecimal("ic_medio"), rs.getBigDecimal("ic_desvio"),
                            rs.getInt("meses"), rs.getObject("media_ativos", Integer.class)));
        } catch (DataAccessException e) {
            avisarUmaVez(e);
            return List.of();
        }
    }

    public List<LinhaQuintil> quintis(long execucaoId) {
        try {
            return jdbc.query(
                    "SELECT rm.versao_regra, rm.janela, rm.horizonte, rq.quintil, "
                            + " AVG(rq.retorno_medio) retorno_medio, SUM(rq.n_ativos) ativos "
                            + "FROM backtest_ranking_quintil rq "
                            + "JOIN backtest_ranking_mes rm ON rm.id = rq.ranking_mes_id "
                            + "WHERE rm.execucao_id = :id "
                            + "GROUP BY rm.versao_regra, rm.janela, rm.horizonte, rq.quintil "
                            + "ORDER BY rm.versao_regra DESC, rm.janela, rm.horizonte, rq.quintil",
                    new MapSqlParameterSource("id", execucaoId),
                    (rs, i) -> new LinhaQuintil(rs.getString("versao_regra"), rs.getString("janela"),
                            rs.getInt("horizonte"), rs.getInt("quintil"), rs.getBigDecimal("retorno_medio"),
                            rs.getInt("ativos")));
        } catch (DataAccessException e) {
            avisarUmaVez(e);
            return List.of();
        }
    }

    private void avisarUmaVez(DataAccessException e) {
        if (avisouAusente.compareAndSet(false, true)) {
            log.warn("(BACKTEST-RANKING)-tabelas do metodo RANKING indisponiveis (migracao V16 aplicada?); "
                    + "devolvendo vazio: {}", e.getMostSpecificCause().getMessage());
        }
    }

    private static LocalDateTime instante(ResultSet rs, String coluna) throws SQLException {
        Timestamp valor = rs.getTimestamp(coluna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
