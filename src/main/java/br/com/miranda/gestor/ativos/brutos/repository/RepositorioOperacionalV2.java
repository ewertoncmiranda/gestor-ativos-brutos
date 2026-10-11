package br.com.miranda.gestor.ativos.brutos.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Leitura da Operabilidade V2. Tolera a tabela ausente e tambem o periodo de transicao entre a migration V25
 * inicial e o produtor V2 mais rico: campos que nao existirem sao derivados de {@code payload_json}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RepositorioOperacionalV2 {

    public static final int LIMITE_MAXIMO = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Set<String> tabelasJaAvisadas = ConcurrentHashMap.newKeySet();

    public record Snapshot(String simbolo, LocalDate dataPregao, String versaoRegra, String statusOperacional,
                           String faixaLiquidez, String setor, String sinalPrincipal, JsonNode sinalTecnico,
                           String opiniaoIa, String confiancaFaixa, String posicaoStatus, String ultimoEventoTipo,
                           JsonNode payload, JsonNode consenso, JsonNode tese, LocalDateTime atualizadoEm) {
    }

    public record TimelineLinha(String eventId, String simbolo, LocalDate dataEvento, String versaoRegra,
                                String tipo, String severidade, String titulo, JsonNode payload,
                                LocalDateTime criadoEm) {
    }

    public List<Snapshot> snapshotsRecentes() {
        return consultar("ticker_operacional_snapshot",
                "WITH atuais AS (SELECT s.*, ROW_NUMBER() OVER (PARTITION BY simbolo "
                        + "ORDER BY data_pregao DESC, atualizado_em DESC, id DESC) AS rn "
                        + "FROM ticker_operacional_snapshot s) "
                        + "SELECT * FROM atuais WHERE rn = 1 ORDER BY data_pregao DESC, simbolo",
                Map.of(), snapshotMapper());
    }

    public Optional<Snapshot> snapshot(String simbolo) {
        return primeiro(consultar("ticker_operacional_snapshot",
                "SELECT * FROM ticker_operacional_snapshot WHERE simbolo = :simbolo "
                        + "ORDER BY data_pregao DESC, atualizado_em DESC LIMIT 1",
                Map.of("simbolo", simbolo), snapshotMapper()));
    }

    public List<TimelineLinha> timeline(String simbolo, String tipo, String severidade, LocalDate inicio,
                                        LocalDate fim, int limite) {
        Set<String> colunas = colunas("ticker_operacional_timeline");
        if (colunas.isEmpty()) {
            return List.of();
        }
        String dataColuna = colunas.contains("data_evento") ? "data_evento" : "data_pregao";
        String tipoColuna = colunas.contains("tipo") ? "tipo" : "tipo_evento";
        Map<String, Object> p = new HashMap<>(Map.of("simbolo", simbolo, "n", limiteSeguro(limite)));
        StringBuilder filtro = new StringBuilder("simbolo = :simbolo");
        if (tipo != null) {
            filtro.append(" AND ").append(tipoColuna).append(" = :tipo");
            p.put("tipo", tipo);
        }
        if (severidade != null) {
            filtro.append(" AND severidade = :sev");
            p.put("sev", severidade);
        }
        if (inicio != null) {
            filtro.append(" AND ").append(dataColuna).append(" >= :ini");
            p.put("ini", java.sql.Date.valueOf(inicio));
        }
        if (fim != null) {
            filtro.append(" AND ").append(dataColuna).append(" <= :fim");
            p.put("fim", java.sql.Date.valueOf(fim));
        }
        return consultar("ticker_operacional_timeline",
                "SELECT * FROM ticker_operacional_timeline WHERE " + filtro
                        + " ORDER BY " + dataColuna + " DESC, criado_em DESC LIMIT :n",
                p, timelineMapper());
    }

    public List<TimelineLinha> alertas(int limite) {
        Set<String> colunas = colunas("ticker_operacional_timeline");
        if (colunas.isEmpty()) {
            return List.of();
        }
        String dataColuna = colunas.contains("data_evento") ? "data_evento" : "data_pregao";
        String tipoColuna = colunas.contains("tipo") ? "tipo" : "tipo_evento";
        String filtroAlerta = colunas.contains("categoria")
                ? "(severidade IN ('ATENCAO','CRITICO') OR categoria IN ('RISCO','SISTEMA'))"
                : "(severidade IN ('ATENCAO','CRITICO') OR " + tipoColuna + " = 'ALERTA')";
        return consultar("ticker_operacional_timeline",
                "SELECT * FROM ticker_operacional_timeline WHERE " + filtroAlerta
                        + " ORDER BY " + dataColuna + " DESC, criado_em DESC LIMIT :n",
                Map.of("n", limiteSeguro(limite)), timelineMapper());
    }

    private RowMapper<Snapshot> snapshotMapper() {
        return (rs, i) -> {
            Set<String> colunas = colunas(rs);
            JsonNode payload = json(texto(rs, colunas, "payload_json"));
            JsonNode consenso = colunas.contains("consenso_json") ? json(texto(rs, colunas, "consenso_json"))
                    : no(payload, "consenso");
            JsonNode tese = colunas.contains("tese_json") ? json(texto(rs, colunas, "tese_json")) : no(payload, "tese");
            JsonNode tecnico = colunas.contains("sinal_tecnico") ? json(texto(rs, colunas, "sinal_tecnico"))
                    : no(payload, "sinalTecnico");
            return new Snapshot(texto(rs, colunas, "simbolo"), data(rs, colunas, "data_pregao"),
                    texto(rs, colunas, "versao_regra"),
                    primeiroTexto(rs, colunas, payload, "status_operacional", "status_sistema", "statusOperacional"),
                    primeiroTexto(rs, colunas, payload, "faixa_liquidez", "faixaLiquidez"),
                    primeiroPayload(payload, "setor", "setorGrupo", "setor_grupo"),
                    primeiroTexto(rs, colunas, payload, "sinal_principal", "sinalPrincipal"), tecnico,
                    primeiroTexto(rs, colunas, payload, "opiniao_ia", "opiniaoIa"),
                    primeiroTexto(rs, colunas, payload, "confianca_faixa", "confiancaFaixa"),
                    primeiroTexto(rs, colunas, payload, "posicao_status", "posicaoStatus"),
                    primeiroTexto(rs, colunas, payload, "ultimo_evento_tipo", "ultimoEventoTipo"),
                    payload, consenso, tese, dataHora(timestamp(rs, colunas, "atualizado_em")));
        };
    }

    private RowMapper<TimelineLinha> timelineMapper() {
        return (rs, i) -> {
            Set<String> colunas = colunas(rs);
            JsonNode payload = json(texto(rs, colunas, "payload_json"));
            String tipo = primeiroTexto(rs, colunas, payload, "tipo", "tipo_evento");
            String titulo = primeiroTexto(rs, colunas, payload, "titulo", "title", "codigo");
            return new TimelineLinha(texto(rs, colunas, "event_id"), texto(rs, colunas, "simbolo"),
                    data(rs, colunas, colunas.contains("data_evento") ? "data_evento" : "data_pregao"),
                    texto(rs, colunas, "versao_regra"), tipo, texto(rs, colunas, "severidade"),
                    titulo, payload, dataHora(timestamp(rs, colunas, "criado_em")));
        };
    }

    private Set<String> colunas(String tabela) {
        try {
            List<String> nomes = jdbc.query("SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = :t",
                    new MapSqlParameterSource(Map.of("t", tabela)), (rs, i) -> rs.getString(1));
            Set<String> saida = new HashSet<>();
            nomes.forEach(n -> saida.add(n.toLowerCase(Locale.ROOT)));
            if (saida.isEmpty()) {
                avisarTabela(tabela, new IllegalStateException("sem colunas em information_schema"));
            }
            return saida;
        } catch (DataAccessException e) {
            avisarTabela(tabela, e);
            return Set.of();
        }
    }

    private <T> List<T> consultar(String tabela, String sql, Map<String, Object> parametros, RowMapper<T> mapper) {
        try {
            return jdbc.query(sql, new MapSqlParameterSource(parametros), mapper);
        } catch (DataAccessException e) {
            avisarTabela(tabela, e);
            return List.of();
        }
    }

    private void avisarTabela(String tabela, Exception e) {
        if (tabelasJaAvisadas.add(tabela)) {
            log.warn("(OPERACIONAL-V2)-{} indisponivel; devolvendo vazio: {}", tabela,
                    e instanceof DataAccessException dae ? dae.getMostSpecificCause().getMessage() : e.getMessage());
        }
    }

    private static int limiteSeguro(int limite) {
        return Math.max(1, Math.min(limite, LIMITE_MAXIMO));
    }

    private static <T> Optional<T> primeiro(List<T> linhas) {
        return linhas.isEmpty() ? Optional.empty() : Optional.ofNullable(linhas.get(0));
    }

    private JsonNode json(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(texto);
        } catch (Exception e) {
            log.warn("(OPERACIONAL-V2)-JSON invalido ignorado: {}", e.getMessage());
            return null;
        }
    }

    private static Set<String> colunas(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Set<String> saida = new HashSet<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            saida.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
        }
        return saida;
    }

    private static String texto(ResultSet rs, Set<String> colunas, String coluna) throws SQLException {
        return colunas.contains(coluna) ? rs.getString(coluna) : null;
    }

    private static LocalDate data(ResultSet rs, Set<String> colunas, String coluna) throws SQLException {
        if (!colunas.contains(coluna)) {
            return null;
        }
        java.sql.Date data = rs.getDate(coluna);
        return data == null ? null : data.toLocalDate();
    }

    private static Timestamp timestamp(ResultSet rs, Set<String> colunas, String coluna) throws SQLException {
        return colunas.contains(coluna) ? rs.getTimestamp(coluna) : null;
    }

    private static LocalDateTime dataHora(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static JsonNode no(JsonNode raiz, String campo) {
        if (raiz == null || !raiz.has(campo)) {
            return null;
        }
        return raiz.get(campo);
    }

    private static String primeiroTexto(ResultSet rs, Set<String> colunas, JsonNode payload, String... nomes)
            throws SQLException {
        for (String nome : nomes) {
            String coluna = nome.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            String valor = texto(rs, colunas, coluna);
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
            valor = primeiroPayload(payload, nome, coluna);
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    private static String primeiroPayload(JsonNode payload, String... campos) {
        if (payload == null) {
            return null;
        }
        for (String campo : campos) {
            JsonNode no = payload.path(campo);
            if (no.isTextual() && !no.asText().isBlank()) {
                return no.asText();
            }
            if (no.isObject()) {
                JsonNode status = no.path("status");
                if (status.isTextual() && !status.asText().isBlank()) {
                    return status.asText();
                }
                JsonNode tipo = no.path("tipo");
                if (tipo.isTextual() && !tipo.asText().isBlank()) {
                    return tipo.asText();
                }
            }
        }
        return null;
    }
}
