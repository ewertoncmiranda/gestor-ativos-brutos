package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Leitura de cvm_negociacao_administrador (V45) e das contas de tesouraria em fato_contabil. */
@Repository
@RequiredArgsConstructor
public class RepositorioInsiders {

    public static final List<String> CONTAS_TESOURARIA = List.of("2.03.02.05", "2.03.04.09", "2.07.01.02.05",
            "2.07.01.04.09", "2.08.02.05", "2.08.04.09");

    private final NamedParameterJdbcTemplate jdbc;

    public record Negociacao(String cargo, String movimentacao, String ativo, LocalDate dataMovimentacao,
                             LocalDate dataEntrega, BigDecimal quantidade, BigDecimal volume) {
    }

    public record SaldoTesouraria(LocalDate dataBase, String tipoDoc, String grupo, BigDecimal saldo) {
    }

    public boolean schemaDisponivel() {
        Integer total = jdbc.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'cvm_negociacao_administrador'
                """, Integer.class);
        return total != null && total == 1;
    }

    public Optional<String> cnpj(String simbolo) {
        return jdbc.queryForList("SELECT cnpj FROM cvm_ticker WHERE simbolo = :s LIMIT 1",
                new MapSqlParameterSource("s", simbolo), String.class).stream().findFirst();
    }

    /** Ultima versao por (cnpj, data_referencia); filtros de tipo ficam no servico (acentos). */
    public List<Negociacao> negociacoes(String cnpj, LocalDate desde) {
        return jdbc.query("""
                SELECT n.tipo_cargo, n.tipo_movimentacao, n.tipo_ativo, n.data_movimentacao, n.data_entrega,
                       n.quantidade, n.volume
                FROM cvm_negociacao_administrador n
                WHERE n.cnpj = :cnpj AND n.data_movimentacao >= :desde
                  AND n.versao = (SELECT MAX(m.versao) FROM cvm_negociacao_administrador m
                                  WHERE m.cnpj = n.cnpj AND m.data_referencia = n.data_referencia)
                """, new MapSqlParameterSource("cnpj", cnpj).addValue("desde", java.sql.Date.valueOf(desde)),
                (rs, i) -> new Negociacao(rs.getString(1), rs.getString(2), rs.getString(3),
                        data(rs.getDate(4)), data(rs.getDate(5)), rs.getBigDecimal(6), rs.getBigDecimal(7)));
    }

    /** Saldo somado das contas de tesouraria do BPP por data-base, DFP/ITR, grupo con/ind. */
    public List<SaldoTesouraria> saldosTesouraria(String cnpj, LocalDate desde) {
        return jdbc.query("""
                SELECT dt_fim_exerc, tipo_doc, grupo, SUM(vl_conta)
                FROM fato_contabil
                WHERE cnpj = :cnpj AND demonstracao = 'BPP' AND tipo_doc IN ('DFP', 'ITR')
                  AND cd_conta IN (:contas) AND dt_fim_exerc >= :desde
                GROUP BY dt_fim_exerc, tipo_doc, grupo
                ORDER BY dt_fim_exerc
                """, new MapSqlParameterSource("cnpj", cnpj).addValue("contas", CONTAS_TESOURARIA)
                        .addValue("desde", java.sql.Date.valueOf(desde)),
                (rs, i) -> new SaldoTesouraria(data(rs.getDate(1)), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4)));
    }

    private static LocalDate data(java.sql.Date d) {
        return d == null ? null : d.toLocalDate();
    }
}
