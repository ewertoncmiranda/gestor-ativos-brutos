package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.BancoDTO;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Leitura de banco_indicador_trimestral e cvm_cadastro (V45); so leitura. */
@Repository
public class RepositorioBancoIndicadores {

    private final JdbcTemplate jdbc;

    public RepositorioBancoIndicadores(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean schemaDisponivel() {
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('banco_indicador_trimestral', 'cvm_cadastro')
                """, Integer.class);
        return total != null && total == 2;
    }

    /** CNPJ (so digitos) do simbolo, pela mesma ligacao do gestor: cvm_ticker. */
    public Optional<String> cnpjDoSimbolo(String simbolo) {
        return jdbc.query("SELECT REGEXP_REPLACE(cnpj, '[^0-9]', '') FROM cvm_ticker WHERE simbolo = ?",
                (rs, i) -> rs.getString(1), simbolo).stream().findFirst();
    }

    public Optional<String> setorCvm(String cnpj) {
        return jdbc.query("SELECT setor_cvm FROM cvm_cadastro WHERE cnpj = ?",
                (rs, i) -> rs.getString(1), cnpj).stream().filter(Objects::nonNull).findFirst();
    }

    /** Trimestres mais recentes primeiro. */
    public List<BancoDTO.Trimestre> ultimosTrimestres(String cnpj, int limite) {
        return jdbc.query("""
                SELECT data_base, basileia, indice_capital_principal, inadimplencia, roe
                  FROM banco_indicador_trimestral
                 WHERE cnpj = ?
                 ORDER BY data_base DESC
                 LIMIT ?
                """, (rs, i) -> new BancoDTO.Trimestre(rs.getDate("data_base").toLocalDate(),
                rs.getBigDecimal("basileia"), rs.getBigDecimal("indice_capital_principal"),
                rs.getBigDecimal("inadimplencia"), rs.getBigDecimal("roe")), cnpj, limite);
    }

    /** Selic meta (serie SELIC, % a.a.) mais recente; vazio se a tabela/serie faltar. */
    public Optional<BigDecimal> selicMeta() {
        try {
            return jdbc.query("""
                    SELECT valor FROM indice_macro
                     WHERE codigo_serie = 'SELIC' AND valor IS NOT NULL
                     ORDER BY data DESC LIMIT 1
                    """, (rs, i) -> rs.getBigDecimal(1)).stream().findFirst();
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }
}
