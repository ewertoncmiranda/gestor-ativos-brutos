package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitura da tabela unica de ativos do painel (TASK-UX-5). Universo = base
 * (cvm_ticker ativo) uniao monitorados (ativo_monitorado ativo). Duas
 * consultas por pagina, independente do tamanho: as linhas (preco, sinal,
 * ultimo comunicado, flags) e os fechamentos recentes para a sparkline.
 * Por JDBC, sem entidade JPA - mesma linha de {@link RepositorioLacunas}.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioListagemAtivos {

    private static final String FAVORITO = "'COTACAO_E_HISTORICO'";

    /** Universo filtrado; compartilhado pela pagina e pela contagem. */
    private static final String UNIVERSO_FILTRADO = """
            FROM (SELECT simbolo FROM cvm_ticker WHERE ativo = TRUE
                  UNION
                  SELECT simbolo FROM ativo_monitorado WHERE ativo = TRUE) u
            LEFT JOIN cvm_ticker t ON t.simbolo = u.simbolo
            LEFT JOIN cvm_empresa e ON e.cnpj = t.cnpj
            LEFT JOIN ativo_monitorado m ON m.simbolo = u.simbolo AND m.ativo = TRUE
            WHERE (:q IS NULL OR u.simbolo LIKE CONCAT(UPPER(:q), '%') OR e.denominacao LIKE CONCAT('%', :q, '%'))
              AND (:setor IS NULL OR e.setor = :setor)
              AND (:uf IS NULL OR e.uf_municipio = :uf)
              AND (:somenteFavoritos = FALSE OR m.tipo_coleta = {FAVORITO})
              AND (:somenteMonitorados = FALSE OR m.id IS NOT NULL)
            """.replace("{FAVORITO}", FAVORITO);

    // A pagina e cortada antes dos enriquecimentos: as subconsultas correlatas
    // rodam so para as linhas devolvidas, nao para o universo inteiro.
    private static final String PAGINA = """
            SELECT p.simbolo, p.nome, p.setor, p.situacao_registro, p.favorito, p.monitorado,
                   c.fechamento, c.data_pregao,
                   (SELECT c0.fechamento FROM cotacao_b3_diaria c0
                     WHERE c0.simbolo = p.simbolo AND c0.data_pregao < c.data_pregao
                     ORDER BY c0.data_pregao DESC LIMIT 1) AS fechamento_anterior,
                   (SELECT MAX(cm.data_pregao) FROM cotacao_b3_diaria cm) AS ultimo_pregao_mercado,
                   i.recomendacao, i.data_analise,
                   JSON_UNQUOTE(JSON_EXTRACT(i.detalhes_json, '$.versao_regra')) AS versao_regra,
                   cc.categoria, cc.assunto, cc.data_entrega, cc.link_download,
                   EXISTS(SELECT 1 FROM indicador_fundamentalista f WHERE f.simbolo = p.simbolo) AS tem_fundamento
            FROM (
                SELECT u.simbolo, e.denominacao AS nome, e.setor, e.situacao_registro, t.cnpj,
                       COALESCE(m.tipo_coleta = {FAVORITO}, FALSE) AS favorito,
                       (m.id IS NOT NULL) AS monitorado
                {UNIVERSO_FILTRADO}
                ORDER BY u.simbolo
                LIMIT :limite OFFSET :deslocamento
            ) p
            LEFT JOIN cotacao_b3_diaria c
                   ON c.simbolo = p.simbolo
                  AND c.data_pregao = (SELECT MAX(c2.data_pregao) FROM cotacao_b3_diaria c2 WHERE c2.simbolo = p.simbolo)
            LEFT JOIN insight_acao i
                   ON i.id = (SELECT MAX(i2.id) FROM insight_acao i2 WHERE i2.simbolo = p.simbolo)
            LEFT JOIN comunicado_cvm cc
                   ON cc.id = (SELECT c3.id FROM comunicado_cvm c3 WHERE c3.cnpj = p.cnpj
                               ORDER BY c3.data_entrega DESC, c3.id DESC LIMIT 1)
            ORDER BY p.simbolo
            """.replace("{FAVORITO}", FAVORITO).replace("{UNIVERSO_FILTRADO}", UNIVERSO_FILTRADO);

    private static final String CONTAGEM = "SELECT COUNT(*) " + UNIVERSO_FILTRADO;

    // Janela de 60 dias corridos a partir do ultimo pregao do mercado limita o
    // que a funcao de janela le; 20 pregoes cabem nela com folga.
    private static final String FECHAMENTOS_RECENTES = """
            SELECT simbolo, fechamento FROM (
                SELECT simbolo, data_pregao, fechamento,
                       ROW_NUMBER() OVER (PARTITION BY simbolo ORDER BY data_pregao DESC) AS rn
                FROM cotacao_b3_diaria
                WHERE simbolo IN (:simbolos)
                  AND data_pregao >= (SELECT MAX(cm.data_pregao) FROM cotacao_b3_diaria cm) - INTERVAL 60 DAY
            ) x
            WHERE rn <= :quantidade
            ORDER BY simbolo, data_pregao
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public record Filtro(String q, String setor, String uf, boolean somenteFavoritos, boolean somenteMonitorados) {
    }

    public record Linha(String simbolo, String nome, String setor, String situacaoRegistro,
                        boolean favorito, boolean monitorado,
                        BigDecimal fechamento, LocalDate dataPregao, BigDecimal fechamentoAnterior,
                        LocalDate ultimoPregaoMercado, String recomendacao, LocalDateTime dataAnalise,
                        String versaoRegra, String categoriaComunicado, String assuntoComunicado,
                        LocalDate dataEntregaComunicado, String linkComunicado, boolean temFundamento) {
    }

    public List<Linha> pagina(Filtro filtro, int deslocamento, int limite) {
        MapSqlParameterSource parametros = parametros(filtro)
                .addValue("limite", limite)
                .addValue("deslocamento", deslocamento);
        return jdbc.query(PAGINA, parametros, (rs, i) -> new Linha(
                rs.getString("simbolo"), rs.getString("nome"), rs.getString("setor"),
                rs.getString("situacao_registro"),
                rs.getBoolean("favorito"), rs.getBoolean("monitorado"),
                rs.getBigDecimal("fechamento"), data(rs, "data_pregao"), rs.getBigDecimal("fechamento_anterior"),
                data(rs, "ultimo_pregao_mercado"), rs.getString("recomendacao"), dataHora(rs, "data_analise"),
                rs.getString("versao_regra"), rs.getString("categoria"), rs.getString("assunto"),
                data(rs, "data_entrega"), rs.getString("link_download"), rs.getBoolean("tem_fundamento")));
    }

    public long contar(Filtro filtro) {
        Long total = jdbc.queryForObject(CONTAGEM, parametros(filtro), Long.class);
        return total == null ? 0 : total;
    }

    /** Ultimos {@code quantidade} fechamentos de cada simbolo, do mais antigo ao mais recente. */
    public Map<String, List<BigDecimal>> fechamentosRecentes(Collection<String> simbolos, int quantidade) {
        Map<String, List<BigDecimal>> porSimbolo = new LinkedHashMap<>();
        if (simbolos.isEmpty()) return porSimbolo;
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("simbolos", simbolos)
                .addValue("quantidade", quantidade);
        jdbc.query(FECHAMENTOS_RECENTES, parametros, (ResultSet rs) -> {
            porSimbolo.computeIfAbsent(rs.getString("simbolo"), s -> new ArrayList<>())
                    .add(rs.getBigDecimal("fechamento"));
        });
        return porSimbolo;
    }

    private static MapSqlParameterSource parametros(Filtro filtro) {
        return new MapSqlParameterSource()
                .addValue("q", filtro.q())
                .addValue("setor", filtro.setor())
                .addValue("uf", filtro.uf())
                .addValue("somenteFavoritos", filtro.somenteFavoritos())
                .addValue("somenteMonitorados", filtro.somenteMonitorados());
    }

    private static LocalDate data(ResultSet rs, String coluna) throws SQLException {
        java.sql.Date valor = rs.getDate(coluna);
        return valor == null ? null : valor.toLocalDate();
    }

    private static LocalDateTime dataHora(ResultSet rs, String coluna) throws SQLException {
        Timestamp valor = rs.getTimestamp(coluna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
