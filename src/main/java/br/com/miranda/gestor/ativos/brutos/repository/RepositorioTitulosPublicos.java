package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.CurvaRealDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Leitura de titulos publicos (V45), earnings yield do ultimo insight e Selic
 * meta. Somente SELECT; as tabelas sao do ETL.
 */
@Repository
public class RepositorioTitulosPublicos {

    static final String TIPOS_NTNB = "('Tesouro IPCA+', 'Tesouro IPCA+ com Juros Semestrais')";

    private final JdbcTemplate jdbc;

    public RepositorioTitulosPublicos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A tabela titulo_publico_taxa (V45) existe no banco. */
    public boolean schemaDisponivel() {
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'titulo_publico_taxa'
                """, Integer.class);
        return total != null && total == 1;
    }

    public Optional<LocalDate> dataBaseMaisRecente() {
        return Optional.ofNullable(jdbc.queryForObject(
                "SELECT MAX(data_base) FROM titulo_publico_taxa WHERE tipo IN " + TIPOS_NTNB,
                LocalDate.class));
    }

    /** NTN-B da data-base, por vencimento (tipo como desempate estavel). */
    public List<CurvaRealDTO.Ponto> ntnbPorVencimento(LocalDate dataBase) {
        return jdbc.query(
                "SELECT tipo, vencimento, taxa_compra, taxa_venda FROM titulo_publico_taxa "
                        + "WHERE data_base = ? AND tipo IN " + TIPOS_NTNB + " ORDER BY vencimento, tipo",
                (rs, i) -> new CurvaRealDTO.Ponto(rs.getString("tipo"),
                        rs.getDate("vencimento").toLocalDate(),
                        rs.getBigDecimal("taxa_compra"), rs.getBigDecimal("taxa_venda")),
                java.sql.Date.valueOf(dataBase));
    }

    /** Earnings yield (%) do ultimo insight do simbolo (MAX(id)); vazio se nao ha insight ou campo. */
    public Optional<BigDecimal> earningsYield(String simbolo) {
        List<BigDecimal> r = jdbc.query("""
                SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(i.detalhes_json, '$.valuation.earnings_yield_percent')) AS DECIMAL(18,6))
                  FROM insight_acao i
                 WHERE i.id = (SELECT MAX(i2.id) FROM insight_acao i2 WHERE i2.simbolo = ?)
                """, (rs, i) -> rs.getBigDecimal(1), simbolo);
        return r.stream().filter(Objects::nonNull).findFirst();
    }

    /** Ultimo ponto SELIC (meta, % a.a.) do cache indice_macro. */
    public Optional<BigDecimal> selicMeta() {
        List<BigDecimal> r = jdbc.query("""
                SELECT valor FROM indice_macro
                 WHERE codigo_serie = 'SELIC' AND valor IS NOT NULL
                 ORDER BY data DESC LIMIT 1
                """, (rs, i) -> rs.getBigDecimal(1));
        return r.stream().findFirst();
    }
}
