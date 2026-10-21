package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Leitura das datas ex: {@code data_ex_b3} (V45, escrita pelo ETL) e, como
 * fallback, o calculo ao vivo sobre {@code cotacao_b3_diaria.marca_ex}.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioDatasEx {

    public static final String SQL_TABELA = """
            SELECT data_ex, marca, tipo, fechamento_anterior, abertura_ex, ajuste_implicito,
                   ajuste_percentual, valor_declarado, origem_valor
            FROM data_ex_b3
            WHERE simbolo = :s AND data_ex >= :desde
            ORDER BY data_ex, marca
            """;

    public static final String SQL_AO_VIVO = """
            SELECT data_pregao, marca_ex, fechamento_anterior, abertura
            FROM (
                SELECT data_pregao, marca_ex, abertura,
                       LAG(fechamento) OVER (ORDER BY data_pregao) AS fechamento_anterior
                FROM cotacao_b3_diaria
                WHERE simbolo = :s
            ) t
            WHERE marca_ex IS NOT NULL AND TRIM(marca_ex) <> '' AND data_pregao >= :desde
            ORDER BY data_pregao
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public record LinhaDataEx(LocalDate dataEx, String marca, String tipo, BigDecimal fechamentoAnterior,
                              BigDecimal aberturaEx, BigDecimal ajusteImplicito, BigDecimal ajustePercentual,
                              BigDecimal valorDeclarado, String origemValor) {
    }

    /** Linhas de data_ex_b3; lista vazia se a tabela nao existe (V45 ausente). */
    public List<LinhaDataEx> daTabela(String simbolo, LocalDate desde) {
        try {
            return jdbc.query(SQL_TABELA, params(simbolo, desde), (rs, i) -> new LinhaDataEx(
                    rs.getDate("data_ex").toLocalDate(), rs.getString("marca"), rs.getString("tipo"),
                    rs.getBigDecimal("fechamento_anterior"), rs.getBigDecimal("abertura_ex"),
                    rs.getBigDecimal("ajuste_implicito"), rs.getBigDecimal("ajuste_percentual"),
                    rs.getBigDecimal("valor_declarado"), rs.getString("origem_valor")));
        } catch (BadSqlGrammarException tabelaAusente) {
            return List.of();
        }
    }

    /** Dias com marca_ex, fechamento do pregao anterior (LAG no historico do simbolo) e abertura do dia. */
    public List<LinhaDataEx> aoVivo(String simbolo, LocalDate desde) {
        return jdbc.query(SQL_AO_VIVO, params(simbolo, desde), (rs, i) -> new LinhaDataEx(
                rs.getDate("data_pregao").toLocalDate(), rs.getString("marca_ex").trim(), null,
                rs.getBigDecimal("fechamento_anterior"), rs.getBigDecimal("abertura"),
                null, null, null, null));
    }

    private static MapSqlParameterSource params(String simbolo, LocalDate desde) {
        return new MapSqlParameterSource().addValue("s", simbolo).addValue("desde", Date.valueOf(desde));
    }
}
