package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.OpcaoB3DTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.SaudeOpcoesDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Leitura conservadora de {@code opcao_b3_diaria} pelo ativo subjacente.
 *
 * <p>Sem a coluna curada {@code ativo_objeto}, a consulta por prefixo de ticker
 * fica bloqueada: PETR3/PETR4 e bases contaminadas misturam instrumentos.</p>
 */
@Repository
@RequiredArgsConstructor
public class RepositorioOpcoes {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Opcoes do ativo subjacente no pregao mais recente disponivel.
     * vencimento opcional no formato YYYYMM; null = todos os vencimentos.
     */
    public List<OpcaoB3DTO> porAtivo(String simbolo, String vencimento) {
        MapSqlParameterSource params = new MapSqlParameterSource("ativo", simbolo);

        StringBuilder sql = new StringBuilder(
                "SELECT simbolo, bdi, data_pregao, data_vencimento, preco_exercicio, "
                        + "abertura, maxima, minima, fechamento, preco_medio, volume, "
                        + "numero_negocios, volume_financeiro "
                        + "FROM opcao_b3_diaria "
                        + "WHERE ativo_objeto = :ativo "
                        + "AND data_vencimento IS NOT NULL "
                        + "AND YEAR(data_vencimento) < 9999 "
                        + "AND data_pregao = (SELECT MAX(data_pregao) FROM opcao_b3_diaria "
                        + "WHERE ativo_objeto = :ativo AND data_vencimento IS NOT NULL AND YEAR(data_vencimento) < 9999)");

        if (vencimento != null && vencimento.matches("\\d{6}")) {
            int ano = Integer.parseInt(vencimento.substring(0, 4));
            int mes = Integer.parseInt(vencimento.substring(4, 6));
            sql.append(" AND YEAR(data_vencimento) = :ano AND MONTH(data_vencimento) = :mes");
            params.addValue("ano", ano).addValue("mes", mes);
        }

        sql.append(" ORDER BY data_vencimento, bdi, preco_exercicio");

        return jdbc.query(sql.toString(), params,
                (rs, i) -> new OpcaoB3DTO(
                        rs.getString("simbolo"),
                        rs.getString("bdi"),
                        rs.getDate("data_pregao").toLocalDate(),
                        rs.getDate("data_vencimento").toLocalDate(),
                        rs.getBigDecimal("preco_exercicio"),
                        rs.getBigDecimal("abertura"),
                        rs.getBigDecimal("maxima"),
                        rs.getBigDecimal("minima"),
                        rs.getBigDecimal("fechamento"),
                        rs.getBigDecimal("preco_medio"),
                        rs.getObject("volume", Long.class),
                        rs.getObject("numero_negocios", Integer.class),
                        rs.getBigDecimal("volume_financeiro")));
    }

    /** Vencimentos disponiveis para um ativo (formato YYYYMM, ordenado). */
    public List<String> vencimentos(String simbolo) {
        return jdbc.queryForList(
                "SELECT DISTINCT DATE_FORMAT(data_vencimento, '%Y%m') AS vm "
                        + "FROM opcao_b3_diaria WHERE ativo_objeto = :ativo "
                        + "AND data_vencimento IS NOT NULL AND YEAR(data_vencimento) < 9999 ORDER BY vm",
                new MapSqlParameterSource("ativo", simbolo),
                String.class);
    }

    public boolean tabelaDisponivel() {
        try {
            Integer total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = 'opcao_b3_diaria'",
                    new MapSqlParameterSource(), Integer.class);
            return total != null && total > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    public long totalRegistros() {
        try {
            Long total = jdbc.queryForObject("SELECT COUNT(*) FROM opcao_b3_diaria",
                    new MapSqlParameterSource(), Long.class);
            return total == null ? 0L : total;
        } catch (DataAccessException e) {
            return 0L;
        }
    }

    public boolean suportaAtivoObjeto() {
        try {
            Integer total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'opcao_b3_diaria' "
                            + "AND column_name = 'ativo_objeto'",
                    new MapSqlParameterSource(), Integer.class);
            return total != null && total > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    /**
     * Le as views de observabilidade da infra e complementa as metricas que
     * dependem do cadastro de ativos monitorados. Ausencia das migrations V30
     * a V32 resulta em Optional vazio para o endpoint explicar o estado.
     */
    public Optional<SaudeOpcoesDTO> saude() {
        try {
            MetricasQualidade qualidade = jdbc.queryForObject(
                    "SELECT total_registros_opcoes, registros_validos, "
                            + "(registros_rejeitados + registros_incompletos + registros_legado_nao_validado) "
                            + "AS registros_invalidos, registros_vencimento_9999, percentual_valido, "
                            + "ultimo_pregao_com_opcoes_validas FROM vw_opcao_b3_qualidade",
                    new MapSqlParameterSource(),
                    (rs, i) -> new MetricasQualidade(
                            rs.getLong("total_registros_opcoes"),
                            rs.getLong("registros_validos"),
                            rs.getLong("registros_invalidos"),
                            rs.getLong("registros_vencimento_9999"),
                            rs.getBigDecimal("percentual_valido"),
                            rs.getDate("ultimo_pregao_com_opcoes_validas") == null
                                    ? null : rs.getDate("ultimo_pregao_com_opcoes_validas").toLocalDate()));

            Monitoramento monitoramento = jdbc.queryForObject(
                    "SELECT SUM(CASE WHEN EXISTS (SELECT 1 FROM opcao_b3_diaria o "
                            + "WHERE o.status_validacao = 'VALIDA' "
                            + "AND o.ativo_objeto = LEFT(COALESCE(i.simbolo_canonico, a.simbolo), 4)) "
                            + "THEN 1 ELSE 0 END) AS com_cadeia, "
                            + "SUM(CASE WHEN EXISTS (SELECT 1 FROM opcao_b3_diaria o "
                            + "WHERE o.status_validacao = 'VALIDA' "
                            + "AND o.ativo_objeto = LEFT(COALESCE(i.simbolo_canonico, a.simbolo), 4)) "
                            + "THEN 0 ELSE 1 END) AS sem_cadeia "
                            + "FROM ativo_monitorado a LEFT JOIN ativo_identidade i ON i.simbolo = a.simbolo "
                            + "WHERE a.ativo = TRUE",
                    new MapSqlParameterSource(),
                    (rs, i) -> new Monitoramento(rs.getLong("com_cadeia"), rs.getLong("sem_cadeia")));

            List<SaudeOpcoesDTO.PrefixoSuspeito> prefixos = jdbc.query(
                    "SELECT LEFT(simbolo, 4) AS prefixo, COUNT(*) AS total_registros "
                            + "FROM opcao_b3_diaria WHERE status_validacao <> 'VALIDA' "
                            + "GROUP BY LEFT(simbolo, 4) ORDER BY total_registros DESC, prefixo LIMIT 10",
                    new MapSqlParameterSource(),
                    (rs, i) -> new SaudeOpcoesDTO.PrefixoSuspeito(
                            rs.getString("prefixo"), rs.getLong("total_registros")));

            if (qualidade == null || monitoramento == null) {
                return Optional.empty();
            }
            var status = qualidade.validos() > 0
                    ? SaudeOpcoesDTO.StatusSaudeOpcoes.OK
                    : SaudeOpcoesDTO.StatusSaudeOpcoes.SEM_CADEIA_VALIDA;
            String motivo = status == SaudeOpcoesDTO.StatusSaudeOpcoes.OK
                    ? "A cadeia possui registros validados; as metricas de rejeicao permanecem visiveis para diagnostico."
                    : "Nenhum registro de opcao validado esta disponivel para os ativos monitorados.";
            return Optional.of(new SaudeOpcoesDTO(
                    status, motivo, qualidade.total(), qualidade.validos(), qualidade.invalidos(),
                    qualidade.vencimento9999(), monitoramento.comCadeia(), monitoramento.semCadeia(),
                    prefixos, qualidade.ultimoPregaoValido(),
                    qualidade.percentualValido() == null ? BigDecimal.ZERO : qualidade.percentualValido()));
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    private record MetricasQualidade(long total, long validos, long invalidos, long vencimento9999,
                                     java.math.BigDecimal percentualValido,
                                     java.time.LocalDate ultimoPregaoValido) {}

    private record Monitoramento(long comCadeia, long semCadeia) {}
}
