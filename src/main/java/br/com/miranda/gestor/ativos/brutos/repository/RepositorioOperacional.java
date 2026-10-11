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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Leitura de {@code regra_operacional}, {@code operacao_simulada}, {@code diario_operacional} e
 * {@code evento_operacional} (infra V23, OPR-GES-1; escritor: gerar-insights). Por JDBC, sem entidade JPA,
 * e SO SELECT: o gestor nao grava nada do plano OPR. Sem a V23, cada metodo devolve vazio e avisa uma vez
 * por tabela no log, nunca 500 (mesma razao de {@link RepositorioOpiniaoIa}).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RepositorioOperacional {

    public static final int LIMITE_MAXIMO = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Set<String> tabelasJaAvisadas = ConcurrentHashMap.newKeySet();

    public record Regra(String versaoRegra, String descricao, String status, LocalDate vigenteDesde,
                        JsonNode parametros) {
    }

    public record DiarioLinha(String versaoRegra, LocalDate dataPregao, BigDecimal capitalInicial,
                              BigDecimal patrimonio, BigDecimal caixa, BigDecimal exposicao,
                              Integer posicoesAbertas, Integer operacoesAbertasDia, Integer operacoesFechadasDia,
                              BigDecimal retornoBrutoDia, BigDecimal retornoLiquidoDia,
                              BigDecimal retornoLiquidoAcumulado, BigDecimal cdiDia, BigDecimal cdiAcumulado,
                              BigDecimal cdiLiquidoIrAcumulado, BigDecimal excessoSobreCdiLiquido,
                              BigDecimal drawdown, BigDecimal drawdownMaximo, BigDecimal impostoEstimadoMes,
                              Integer violacoesLiquidez, String statusSistema, JsonNode motivosStatus,
                              LocalDateTime atualizadoEm) {
    }

    public record OperacaoLinha(long id, String versaoRegra, String simbolo, String setorGrupo,
                                int horizontePregoes, String status, LocalDate dataDecisaoEntrada,
                                LocalDate dataEntrada, BigDecimal precoEntrada, Integer quantidade,
                                BigDecimal valorEntrada, BigDecimal stopInicial, BigDecimal stopAtual,
                                String faixaLiquidezEntrada, JsonNode motivoEntrada, LocalDate dataDecisaoSaida,
                                LocalDate dataSaida, BigDecimal precoSaida, String motivoSaida,
                                JsonNode motivoSaidaDetalhe, BigDecimal custosEntrada, BigDecimal custosSaida,
                                BigDecimal resultadoBruto, BigDecimal resultadoPosCustos,
                                BigDecimal impostoEstimado, BigDecimal resultadoPosImpostoEstimado) {
    }

    public record EventoLinha(String eventId, String tipo, String versaoRegra, LocalDate dataPregao,
                              String simbolo, JsonNode payload) {
    }

    // ---------- regra ----------

    public Optional<Regra> regraAtiva() {
        return primeiro(consultar("regra_operacional", "SELECT " + COLUNAS_REGRA + " FROM regra_operacional "
                + "WHERE status = 'ATIVA' ORDER BY vigente_desde DESC, id DESC LIMIT 1", Map.of(), regraMapper()));
    }

    public Optional<Regra> regra(String versao) {
        return primeiro(consultar("regra_operacional", "SELECT " + COLUNAS_REGRA + " FROM regra_operacional "
                + "WHERE versao_regra = :v", Map.of("v", versao), regraMapper()));
    }

    public Optional<String> versaoDoUltimoDiario() {
        return primeiro(consultar("diario_operacional", "SELECT versao_regra FROM diario_operacional "
                + "ORDER BY data_pregao DESC, atualizado_em DESC, id DESC LIMIT 1", Map.of(),
                (rs, i) -> rs.getString(1)));
    }

    public Optional<String> versaoDaUltimaOperacao() {
        return primeiro(consultar("operacao_simulada", "SELECT versao_regra FROM operacao_simulada "
                + "ORDER BY data_decisao_entrada DESC, id DESC LIMIT 1", Map.of(), (rs, i) -> rs.getString(1)));
    }

    /** As quatro tabelas respondem? Falso quando a V23 nao foi aplicada. */
    public boolean tabelasDisponiveis() {
        for (String tabela : List.of("regra_operacional", "operacao_simulada", "diario_operacional",
                "evento_operacional")) {
            try {
                jdbc.query("SELECT 1 FROM " + tabela + " LIMIT 1", Map.of(), (rs, i) -> 1);
            } catch (DataAccessException e) {
                return false;
            }
        }
        return true;
    }

    // ---------- diario ----------

    public Optional<DiarioLinha> ultimoDiario(String versao) {
        return primeiro(consultar("diario_operacional", "SELECT " + COLUNAS_DIARIO + " FROM diario_operacional "
                + "WHERE versao_regra = :v ORDER BY data_pregao DESC LIMIT 1", Map.of("v", versao), diarioMapper()));
    }

    /** Janela do diario em ordem cronologica; sem limites de data, os {@code limite} dias mais recentes. */
    public List<DiarioLinha> diario(String versao, LocalDate inicio, LocalDate fim, int limite) {
        Map<String, Object> p = new HashMap<>(Map.of("v", versao, "n", limiteSeguro(limite)));
        StringBuilder filtro = new StringBuilder("versao_regra = :v");
        if (inicio != null) {
            filtro.append(" AND data_pregao >= :ini");
            p.put("ini", java.sql.Date.valueOf(inicio));
        }
        if (fim != null) {
            filtro.append(" AND data_pregao <= :fim");
            p.put("fim", java.sql.Date.valueOf(fim));
        }
        return consultar("diario_operacional", "SELECT * FROM (SELECT " + COLUNAS_DIARIO + " FROM diario_operacional "
                + "WHERE " + filtro + " ORDER BY data_pregao DESC LIMIT :n) d ORDER BY data_pregao ASC", p, diarioMapper());
    }

    public Optional<LocalDate> primeiraDataDoDiario(String versao) {
        return primeiro(consultar("diario_operacional", "SELECT MIN(data_pregao) FROM diario_operacional "
                + "WHERE versao_regra = :v", Map.of("v", versao),
                (rs, i) -> rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate()))
                .filter(java.util.Objects::nonNull);
    }

    public int contarDiario(String versao) {
        return primeiro(consultar("diario_operacional", "SELECT COUNT(*) FROM diario_operacional WHERE versao_regra = :v",
                Map.of("v", versao), (rs, i) -> rs.getInt(1))).orElse(0);
    }

    public Optional<BigDecimal> drawdownMinimo(String versao) {
        return primeiro(consultar("diario_operacional", "SELECT MIN(drawdown) FROM diario_operacional "
                + "WHERE versao_regra = :v", Map.of("v", versao), (rs, i) -> rs.getBigDecimal(1)))
                .filter(java.util.Objects::nonNull);
    }

    // ---------- operacoes ----------

    public Map<String, Long> contarOperacoesPorStatus(String versao) {
        Map<String, Long> saida = new HashMap<>();
        consultar("operacao_simulada", "SELECT status, COUNT(*) FROM operacao_simulada WHERE versao_regra = :v "
                + "GROUP BY status", Map.of("v", versao), (rs, i) -> {
                    saida.put(rs.getString(1), rs.getLong(2));
                    return 0;
                });
        return saida;
    }

    /** Filtros combinaveis (AND); {@code null} = sem filtro. Datas filtram {@code data_decisao_entrada}. */
    public List<OperacaoLinha> operacoes(String versao, String status, String simbolo, LocalDate inicio,
                                         LocalDate fim, String motivoSaida, int limite) {
        Map<String, Object> p = new HashMap<>(Map.of("v", versao, "n", limiteSeguro(limite)));
        StringBuilder filtro = new StringBuilder("versao_regra = :v");
        if (status != null) {
            filtro.append(" AND status = :status");
            p.put("status", status);
        }
        if (simbolo != null) {
            filtro.append(" AND simbolo = :simbolo");
            p.put("simbolo", simbolo);
        }
        if (inicio != null) {
            filtro.append(" AND data_decisao_entrada >= :ini");
            p.put("ini", java.sql.Date.valueOf(inicio));
        }
        if (fim != null) {
            filtro.append(" AND data_decisao_entrada <= :fim");
            p.put("fim", java.sql.Date.valueOf(fim));
        }
        if (motivoSaida != null) {
            filtro.append(" AND motivo_saida = :motivo");
            p.put("motivo", motivoSaida);
        }
        return consultar("operacao_simulada", "SELECT " + COLUNAS_OPERACAO + " FROM operacao_simulada WHERE " + filtro
                + " ORDER BY data_decisao_entrada DESC, id DESC LIMIT :n", p, operacaoMapper());
    }

    // ---------- eventos ----------

    public Optional<LocalDate> ultimaDataDeBloqueio(String versao) {
        return primeiro(consultar("evento_operacional", "SELECT MAX(data_pregao) FROM evento_operacional "
                + "WHERE versao_regra = :v AND tipo IN ('BLOQUEIO_DADO','BLOQUEIO_LIQUIDEZ')", Map.of("v", versao),
                (rs, i) -> rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate()))
                .filter(java.util.Objects::nonNull);
    }

    public List<EventoLinha> bloqueios(String versao, LocalDate data, String simbolo, int limite) {
        Map<String, Object> p = new HashMap<>(Map.of("v", versao, "data", java.sql.Date.valueOf(data),
                "n", limiteSeguro(limite)));
        StringBuilder filtro = new StringBuilder("versao_regra = :v AND data_pregao = :data "
                + "AND tipo IN ('BLOQUEIO_DADO','BLOQUEIO_LIQUIDEZ')");
        if (simbolo != null) {
            filtro.append(" AND simbolo = :simbolo");
            p.put("simbolo", simbolo);
        }
        return consultar("evento_operacional", "SELECT event_id, tipo, versao_regra, data_pregao, simbolo, payload_json "
                + "FROM evento_operacional WHERE " + filtro + " ORDER BY simbolo, id LIMIT :n", p,
                (rs, i) -> new EventoLinha(rs.getString("event_id"), rs.getString("tipo"),
                        rs.getString("versao_regra"), rs.getDate("data_pregao").toLocalDate(),
                        rs.getString("simbolo"), json(rs.getString("payload_json"))));
    }

    /** Eventos gravados e ainda nao publicados (fila de saida). */
    public long eventosPendentes(String versao) {
        return primeiro(consultar("evento_operacional", "SELECT COUNT(*) FROM evento_operacional "
                + "WHERE versao_regra = :v AND publicado_em IS NULL", Map.of("v", versao),
                (rs, i) -> rs.getLong(1))).orElse(0L);
    }

    /** Ultimo pregao com cotacao da B3 (referencia de "dado do dia completo"). */
    public Optional<LocalDate> ultimoPregaoDisponivel() {
        return primeiro(consultar("cotacao_b3_diaria", "SELECT MAX(data_pregao) FROM cotacao_b3_diaria", Map.of(),
                (rs, i) -> rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate()))
                .filter(java.util.Objects::nonNull);
    }

    // ---------- infraestrutura ----------

    private static final String COLUNAS_REGRA = "versao_regra, descricao, status, vigente_desde, parametros_json";
    private static final String COLUNAS_DIARIO = "versao_regra, data_pregao, capital_inicial, patrimonio, caixa, "
            + "exposicao, posicoes_abertas, operacoes_abertas_dia, operacoes_fechadas_dia, retorno_bruto_dia, "
            + "retorno_liquido_dia, retorno_liquido_acumulado, cdi_dia, cdi_acumulado, cdi_liquido_ir_acumulado, "
            + "excesso_sobre_cdi_liquido, drawdown, drawdown_maximo, imposto_estimado_mes, violacoes_liquidez, "
            + "status_sistema, motivos_status_json, atualizado_em";
    private static final String COLUNAS_OPERACAO = "id, versao_regra, simbolo, setor_grupo, horizonte_pregoes, status, "
            + "data_decisao_entrada, data_entrada, preco_entrada, quantidade, valor_entrada, stop_inicial, "
            + "stop_atual, faixa_liquidez_entrada, motivo_entrada_json, data_decisao_saida, data_saida, "
            + "preco_saida, motivo_saida, motivo_saida_json, custos_entrada, custos_saida, resultado_bruto, "
            + "resultado_pos_custos, imposto_estimado, resultado_pos_imposto_estimado";

    private static int limiteSeguro(int limite) {
        return Math.max(1, Math.min(limite, LIMITE_MAXIMO));
    }

    private static <T> Optional<T> primeiro(List<T> linhas) {
        return linhas.isEmpty() ? Optional.empty() : Optional.ofNullable(linhas.get(0));
    }

    /** Roda o SELECT; tabela ausente (V23 nao aplicada) vira lista vazia e um aviso unico por tabela. */
    private <T> List<T> consultar(String tabela, String sql, Map<String, Object> parametros, RowMapper<T> mapper) {
        try {
            return jdbc.query(sql, new MapSqlParameterSource(parametros), mapper);
        } catch (DataAccessException e) {
            if (tabelasJaAvisadas.add(tabela)) {
                log.warn("(OPERACIONAL)-{} indisponivel (migracao V23 aplicada?); devolvendo vazio: {}", tabela,
                        e.getMostSpecificCause().getMessage());
            }
            return List.of();
        }
    }

    private RowMapper<Regra> regraMapper() {
        return (rs, i) -> new Regra(rs.getString("versao_regra"), rs.getString("descricao"), rs.getString("status"),
                data(rs, "vigente_desde"), json(rs.getString("parametros_json")));
    }

    private RowMapper<DiarioLinha> diarioMapper() {
        return (rs, i) -> new DiarioLinha(rs.getString("versao_regra"), data(rs, "data_pregao"),
                rs.getBigDecimal("capital_inicial"), rs.getBigDecimal("patrimonio"), rs.getBigDecimal("caixa"),
                rs.getBigDecimal("exposicao"), inteiro(rs, "posicoes_abertas"), inteiro(rs, "operacoes_abertas_dia"),
                inteiro(rs, "operacoes_fechadas_dia"), rs.getBigDecimal("retorno_bruto_dia"),
                rs.getBigDecimal("retorno_liquido_dia"), rs.getBigDecimal("retorno_liquido_acumulado"),
                rs.getBigDecimal("cdi_dia"), rs.getBigDecimal("cdi_acumulado"),
                rs.getBigDecimal("cdi_liquido_ir_acumulado"), rs.getBigDecimal("excesso_sobre_cdi_liquido"),
                rs.getBigDecimal("drawdown"), rs.getBigDecimal("drawdown_maximo"),
                rs.getBigDecimal("imposto_estimado_mes"), inteiro(rs, "violacoes_liquidez"),
                rs.getString("status_sistema"), json(rs.getString("motivos_status_json")),
                dataHora(rs.getTimestamp("atualizado_em")));
    }

    private RowMapper<OperacaoLinha> operacaoMapper() {
        return (rs, i) -> new OperacaoLinha(rs.getLong("id"), rs.getString("versao_regra"), rs.getString("simbolo"),
                rs.getString("setor_grupo"), rs.getInt("horizonte_pregoes"), rs.getString("status"),
                data(rs, "data_decisao_entrada"), data(rs, "data_entrada"), rs.getBigDecimal("preco_entrada"),
                inteiro(rs, "quantidade"), rs.getBigDecimal("valor_entrada"), rs.getBigDecimal("stop_inicial"),
                rs.getBigDecimal("stop_atual"), rs.getString("faixa_liquidez_entrada"),
                json(rs.getString("motivo_entrada_json")), data(rs, "data_decisao_saida"), data(rs, "data_saida"),
                rs.getBigDecimal("preco_saida"), rs.getString("motivo_saida"),
                json(rs.getString("motivo_saida_json")), rs.getBigDecimal("custos_entrada"),
                rs.getBigDecimal("custos_saida"), rs.getBigDecimal("resultado_bruto"),
                rs.getBigDecimal("resultado_pos_custos"), rs.getBigDecimal("imposto_estimado"),
                rs.getBigDecimal("resultado_pos_imposto_estimado"));
    }

    private static LocalDate data(ResultSet rs, String coluna) throws SQLException {
        java.sql.Date d = rs.getDate(coluna);
        return d == null ? null : d.toLocalDate();
    }

    private static Integer inteiro(ResultSet rs, String coluna) throws SQLException {
        int v = rs.getInt(coluna);
        return rs.wasNull() ? null : v;
    }

    private static LocalDateTime dataHora(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private JsonNode json(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(texto);
        } catch (Exception e) {
            log.warn("(OPERACIONAL)-JSON invalido ignorado: {}", e.getMessage());
            return null;
        }
    }
}
