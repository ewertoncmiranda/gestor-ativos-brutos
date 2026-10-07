package br.com.miranda.gestor.ativos.brutos.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leitura de opiniao_ia (infra V22, escritor: gerar-insights) para
 * TASK-OPI-1. Por JDBC, sem entidade JPA - mesma razao de
 * {@link RepositorioLacunas}: sem a V22, devolve vazio e avisa uma vez no
 * log, nunca 500. Devolve todas as linhas do pregao mais recente do ativo;
 * quem escolhe entre modelo e regra e o servico.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RepositorioOpiniaoIa {

    // Uma geracao por pregao: so as linhas da versao de prompt/skills gravada
    // por ultimo naquele pregao (versoes antigas tem defeitos ja corrigidos).
    // "Ultima gravada", nao MAX(versao_prompt): MAX compara texto ("1.10" <
    // "1.2") e a versao vira "skills@<hash>" no servico de IA (DEC-IA-03).
    private static final String DO_ULTIMO_PREGAO = """
            SELECT o.data_pregao, o.horizonte_pregoes, o.opiniao, o.risco, o.justificativa_json, o.invalida_json,
                   o.dados_ausentes_json, o.evidencias_json, o.modelo, o.origem, o.versao_prompt, o.criado_em
            FROM opiniao_ia o
            JOIN (SELECT data_pregao FROM opiniao_ia WHERE simbolo = :simbolo
                  ORDER BY data_pregao DESC LIMIT 1) d ON d.data_pregao = o.data_pregao
            WHERE o.simbolo = :simbolo
              AND o.versao_prompt = (SELECT o3.versao_prompt FROM opiniao_ia o3
                                     WHERE o3.simbolo = :simbolo AND o3.data_pregao = d.data_pregao
                                     ORDER BY o3.criado_em DESC, o3.id DESC LIMIT 1)
            ORDER BY o.horizonte_pregoes, o.criado_em DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AtomicBoolean avisouAusente = new AtomicBoolean(false);

    /** Uma linha de opiniao_ia com as colunas JSON ja lidas como arvore. */
    public record Linha(LocalDate dataPregao, int horizontePregoes, String opiniao, String risco,
                        JsonNode justificativa, JsonNode invalida, JsonNode dadosAusentes, JsonNode evidencias,
                        String modelo, String origem, String versaoPrompt, LocalDateTime criadoEm) {
    }

    public List<Linha> doUltimoPregao(String simbolo) {
        try {
            return jdbc.query(DO_ULTIMO_PREGAO, new MapSqlParameterSource("simbolo", simbolo.toUpperCase()),
                    (rs, i) -> new Linha(
                            rs.getDate("data_pregao").toLocalDate(), rs.getInt("horizonte_pregoes"),
                            rs.getString("opiniao"), rs.getString("risco"),
                            json(rs.getString("justificativa_json")), json(rs.getString("invalida_json")),
                            json(rs.getString("dados_ausentes_json")), json(rs.getString("evidencias_json")),
                            rs.getString("modelo"), rs.getString("origem"), rs.getString("versao_prompt"),
                            rs.getTimestamp("criado_em") == null ? null : rs.getTimestamp("criado_em").toLocalDateTime()));
        } catch (DataAccessException e) {
            if (avisouAusente.compareAndSet(false, true)) {
                log.warn("(OPINIAO)-opiniao_ia indisponivel (migracao V22 aplicada?); devolvendo vazio: {}",
                        e.getMostSpecificCause().getMessage());
            }
            return List.of();
        }
    }

    /** JSON invalido ou ausente vira null (o servico trata como lista vazia), nunca derruba a leitura. */
    private JsonNode json(String texto) {
        if (texto == null) return null;
        try {
            return mapper.readTree(texto);
        } catch (Exception e) {
            return null;
        }
    }
}
