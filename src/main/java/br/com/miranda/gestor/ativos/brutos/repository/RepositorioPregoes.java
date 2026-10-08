package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pregoes diarios de um ativo (contrato infra#CTR-15): o COTAHIST oficial
 * da B3 ({@code cotacao_b3_diaria}, desde 2016) e, para os dias que ele
 * ainda nao trouxe, as velas da BRAPI ({@code candle_diario}). Precos
 * brutos nas duas fontes - a BRAPI tambem tem o fechamento ajustado, mas
 * misturar ajustado com bruto na mesma serie criaria saltos falsos.
 *
 * <p>SQL explicito: as tabelas sao do ETL e da migration, nao deste servico.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioPregoes {

    private final NamedParameterJdbcTemplate jdbc;

    public record Pregao(LocalDate data, String codigo, BigDecimal abertura, BigDecimal maxima,
                         BigDecimal minima, BigDecimal fechamento, Long volume,
                         Integer numeroNegocios, BigDecimal volumeFinanceiro, String fonte) {
    }

    /**
     * Codigos que representam o mesmo papel do canonico: ele mesmo e os
     * antigos com continuidade de preco (ELET3 para AXIA3). BRFS3 nao entra
     * em MBRF3: foi incorporada com relacao de troca, outro papel.
     */
    public List<String> codigosDoPapel(String canonico) {
        List<String> antigos = jdbc.queryForList(
                "SELECT simbolo FROM ativo_identidade WHERE simbolo_canonico = :c AND simbolo <> :c "
                        + "AND continuidade_preco = 1",
                new MapSqlParameterSource("c", canonico), String.class);
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(canonico), antigos.stream()).toList();
    }

    public List<Pregao> oficiais(List<String> codigos, LocalDate de, LocalDate ate) {
        return jdbc.query(
                "SELECT data_pregao, simbolo, abertura, maxima, minima, fechamento, volume, numero_negocios, volume_financeiro "
                        + "FROM cotacao_b3_diaria WHERE simbolo IN (:codigos) "
                        + "AND data_pregao BETWEEN :de AND :ate ORDER BY data_pregao",
                new MapSqlParameterSource("codigos", codigos).addValue("de", de).addValue("ate", ate),
                (rs, i) -> new Pregao(rs.getDate("data_pregao").toLocalDate(), rs.getString("simbolo"),
                        rs.getBigDecimal("abertura"), rs.getBigDecimal("maxima"), rs.getBigDecimal("minima"),
                        rs.getBigDecimal("fechamento"), rs.getObject("volume", Long.class),
                        rs.getObject("numero_negocios", Integer.class), rs.getBigDecimal("volume_financeiro"), "B3_COTAHIST"));
    }

    /** Velas da BRAPI depois de {@code depoisDe} (dias que o COTAHIST ainda nao tem). */
    public List<Pregao> recentesBrapi(String simbolo, LocalDate depoisDe, LocalDate ate) {
        return jdbc.query(
                "SELECT data, simbolo, open, high, low, close, volume FROM candle_diario "
                        + "WHERE simbolo = :s AND data > :depois AND data <= :ate "
                        + "AND open IS NOT NULL AND close IS NOT NULL ORDER BY data",
                new MapSqlParameterSource("s", simbolo).addValue("depois", depoisDe).addValue("ate", ate),
                (rs, i) -> new Pregao(rs.getDate("data").toLocalDate(), rs.getString("simbolo"),
                        rs.getBigDecimal("open"), rs.getBigDecimal("high"), rs.getBigDecimal("low"),
                        rs.getBigDecimal("close"),
                        rs.getBigDecimal("volume") == null ? null : rs.getBigDecimal("volume").longValue(),
                        null, null, "BRAPI"));
    }
}
