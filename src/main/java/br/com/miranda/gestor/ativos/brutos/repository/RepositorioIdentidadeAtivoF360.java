package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.IdentidadeAtivoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.HashSet;
import java.util.Set;

/** Lê a identidade sem mapear colunas opcionais no Hibernate. */
@Repository
@RequiredArgsConstructor
public class RepositorioIdentidadeAtivoF360 {
    private final NamedParameterJdbcTemplate jdbc;

    public IdentidadeAtivoDTO buscar(String simbolo) {
        try {
            Set<String> empresa = colunas("cvm_empresa");
            Set<String> ticker = colunas("cvm_ticker");
            if (empresa.isEmpty() || ticker.isEmpty()) return IdentidadeAtivoDTO.vazio(simbolo);
            String select = "SELECT t.simbolo, " + expr(ticker, "t", "isin") + " AS isin, "
                    + expr(ticker, "t", "free_float") + " AS free_float, "
                    + expr(empresa, "e", "denominacao") + " AS razao_social, "
                    + expr(empresa, "e", "cnae") + " AS cnae, "
                    + expr(empresa, "e", "grupo_economico") + " AS grupo_economico, "
                    + expr(empresa, "e", "situacao_registro") + " AS situacao_registro, "
                    + expr(empresa, "e", "data_constituicao") + " AS data_constituicao, "
                    + expr(empresa, "e", "uf_municipio") + " AS uf_municipio, "
                    + "t.cnpj FROM cvm_ticker t LEFT JOIN cvm_empresa e ON e.cnpj = t.cnpj WHERE t.simbolo = :simbolo LIMIT 1";
            return jdbc.query(select, new MapSqlParameterSource("simbolo", simbolo), rs -> {
                if (!rs.next()) return IdentidadeAtivoDTO.vazio(simbolo);
                Date data = rs.getDate("data_constituicao");
                return new IdentidadeAtivoDTO(rs.getString("simbolo"), rs.getString("cnpj"),
                        rs.getString("razao_social"), rs.getString("isin"), rs.getString("cnae"),
                        rs.getString("grupo_economico"), rs.getString("situacao_registro"),
                        data == null ? null : data.toLocalDate(), rs.getString("uf_municipio"),
                        rs.getBigDecimal("free_float"));
            });
        } catch (DataAccessException e) {
            return IdentidadeAtivoDTO.vazio(simbolo);
        }
    }

    private Set<String> colunas(String tabela) {
        return new HashSet<>(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = :tabela",
                new MapSqlParameterSource("tabela", tabela), String.class));
    }

    private static String expr(Set<String> colunas, String alias, String coluna) {
        return colunas.contains(coluna) ? alias + "." + coluna : "NULL";
    }
}
