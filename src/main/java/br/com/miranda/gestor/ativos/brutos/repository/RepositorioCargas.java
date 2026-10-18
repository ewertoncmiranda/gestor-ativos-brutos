package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.CargaDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Leitura das cargas do ETL (infra V44). Somente SELECT: as tabelas sao do
 * ETL. JSON das colunas *_json sai como arvore (metricas) ou lista de textos
 * (alertas, erros); JSON invalido vira null/lista vazia, nunca erro 500.
 */
@Repository
public class RepositorioCargas {

    private static final String COLUNAS_EXECUCAO = """
            id, tipo, solicitada_por, parametros_json, status, versao_etl, criada_em, iniciada_em,
            finalizada_em, duracao_ms, etapas_ok, etapas_alerta, etapas_falha, mensagem_erro
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RepositorioCargas(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** As duas tabelas e a view da V44 existem no banco. */
    public boolean schemaDisponivel() {
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('carga_execucao', 'carga_etapa', 'vw_carga_ultima')
                """, Integer.class);
        return total != null && total == 3;
    }

    /** Mais recentes primeiro; status e desde sao filtros opcionais. */
    public List<CargaDTO.Execucao> listar(int limite, String status, LocalDateTime desde) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUNAS_EXECUCAO).append(" FROM carga_execucao WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (desde != null) {
            sql.append(" AND criada_em >= ?");
            args.add(Timestamp.valueOf(desde));
        }
        sql.append(" ORDER BY criada_em DESC, id DESC LIMIT ?");
        args.add(limite);
        return jdbc.query(sql.toString(), (rs, i) -> execucao(rs, List.of(), List.of()), args.toArray());
    }

    public Optional<CargaDTO.Execucao> buscar(String id) {
        List<CargaDTO.Etapa> etapas = jdbc.query("""
                SELECT etapa, ordem, status, iniciada_em, finalizada_em, duracao_ms,
                       metricas_json, alertas_json, erros_json
                FROM carga_etapa WHERE execucao_id = ? ORDER BY ordem, id
                """, (rs, i) -> new CargaDTO.Etapa(rs.getString("etapa"), rs.getInt("ordem"), rs.getString("status"),
                instante(rs, "iniciada_em"), instante(rs, "finalizada_em"), longo(rs, "duracao_ms"),
                arvore(rs.getString("metricas_json")), textos(rs.getString("alertas_json")),
                textos(rs.getString("erros_json"))), id);
        List<CargaDTO.Arquivo> arquivos = jdbc.query("""
                SELECT fonte, competencia, arquivo, etag, linhas_carregadas, status,
                       iniciado_em, finalizado_em, mensagem_erro
                FROM etl_execucao WHERE carga_execucao_id = ? ORDER BY id
                """, (rs, i) -> new CargaDTO.Arquivo(rs.getString("fonte"), rs.getString("competencia"),
                rs.getString("arquivo"), rs.getString("etag"), rs.getInt("linhas_carregadas"), rs.getString("status"),
                instante(rs, "iniciado_em"), instante(rs, "finalizado_em"), rs.getString("mensagem_erro")), id);
        List<CargaDTO.Execucao> achada = jdbc.query("SELECT " + COLUNAS_EXECUCAO + " FROM carga_execucao WHERE id = ?",
                (rs, i) -> execucao(rs, etapas, arquivos), id);
        return achada.stream().findFirst();
    }

    public List<CargaDTO.Ultima> ultimas() {
        return jdbc.query("""
                SELECT etapa, ultima_tentativa_em, ultimo_status, ultima_execucao_id,
                       ultimo_sucesso_em, ultimo_sucesso_execucao_id
                FROM vw_carga_ultima ORDER BY etapa
                """, (rs, i) -> new CargaDTO.Ultima(rs.getString("etapa"), instante(rs, "ultima_tentativa_em"),
                rs.getString("ultimo_status"), rs.getString("ultima_execucao_id"),
                instante(rs, "ultimo_sucesso_em"), rs.getString("ultimo_sucesso_execucao_id")));
    }

    private CargaDTO.Execucao execucao(ResultSet rs, List<CargaDTO.Etapa> etapas, List<CargaDTO.Arquivo> arquivos)
            throws SQLException {
        return new CargaDTO.Execucao(rs.getString("id"), rs.getString("tipo"), rs.getString("solicitada_por"),
                arvore(rs.getString("parametros_json")), rs.getString("status"), rs.getString("versao_etl"),
                instante(rs, "criada_em"), instante(rs, "iniciada_em"), instante(rs, "finalizada_em"),
                longo(rs, "duracao_ms"), rs.getInt("etapas_ok"), rs.getInt("etapas_alerta"),
                rs.getInt("etapas_falha"), rs.getString("mensagem_erro"), etapas, arquivos);
    }

    JsonNode arvore(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return json.readTree(texto);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** Lista JSON de textos; item nao textual vira o proprio JSON dele. */
    List<String> textos(String texto) {
        JsonNode no = arvore(texto);
        if (no == null || !no.isArray()) {
            return List.of();
        }
        List<String> saida = new ArrayList<>();
        no.forEach(item -> saida.add(item.isTextual() ? item.asText() : item.toString()));
        return saida;
    }

    private static LocalDateTime instante(ResultSet rs, String coluna) throws SQLException {
        Timestamp valor = rs.getTimestamp(coluna);
        return valor == null ? null : valor.toLocalDateTime();
    }

    private static Long longo(ResultSet rs, String coluna) throws SQLException {
        long valor = rs.getLong(coluna);
        return rs.wasNull() ? null : valor;
    }
}
