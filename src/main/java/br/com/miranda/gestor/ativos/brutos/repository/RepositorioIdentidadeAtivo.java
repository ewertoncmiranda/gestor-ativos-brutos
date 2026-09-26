package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Leitura de {@code ativo_identidade} (infra V6, contrato CTR-12): o codigo
 * canonico de cada empresa quando o ticker muda (ELET3 -> AXIA3). A BRAPI ja
 * responde com o codigo novo; registrar o antigo partia a empresa em dois
 * tickers - preco num, balanco no outro.
 *
 * <p>SQL explicito, sem entidade JPA: com {@code ddl-auto=update} o Hibernate
 * passaria a mexer numa tabela cujo dono e a migration.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioIdentidadeAtivo {

    private final NamedParameterJdbcTemplate jdbc;

    /** Codigo canonico do simbolo; o proprio simbolo quando nao ha curadoria. */
    public String canonico(String simbolo) {
        List<String> canonicos = jdbc.queryForList(
                "SELECT simbolo_canonico FROM ativo_identidade WHERE simbolo = :s",
                new MapSqlParameterSource("s", simbolo), String.class);
        return canonicos.isEmpty() ? simbolo : canonicos.get(0);
    }
}
