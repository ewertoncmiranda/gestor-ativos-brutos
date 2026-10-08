package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.OpcaoB3DTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Leitura de {@code opcao_b3_diaria} pelo ativo subjacente.
 *
 * <p>Na B3, opcoes de PETR4 e PETR3 compartilham o prefixo "PETR" nos primeiros
 * 4 caracteres do ticker de opcao. O filtro usa {@code LIKE 'XXXX%'} sobre os
 * primeiros 4 caracteres do simbolo pedido.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioOpcoes {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Opcoes do ativo subjacente no pregao mais recente disponivel.
     * vencimento opcional no formato YYYYMM; null = todos os vencimentos.
     */
    public List<OpcaoB3DTO> porAtivo(String simbolo, String vencimento) {
        String prefixo = simbolo.substring(0, Math.min(4, simbolo.length())) + "%";
        MapSqlParameterSource params = new MapSqlParameterSource("prefixo", prefixo);

        StringBuilder sql = new StringBuilder(
                "SELECT simbolo, bdi, data_pregao, data_vencimento, preco_exercicio, "
                        + "abertura, maxima, minima, fechamento, preco_medio, volume, "
                        + "numero_negocios, volume_financeiro "
                        + "FROM opcao_b3_diaria "
                        + "WHERE simbolo LIKE :prefixo "
                        + "AND data_pregao = (SELECT MAX(data_pregao) FROM opcao_b3_diaria WHERE simbolo LIKE :prefixo)");

        if (vencimento != null && vencimento.matches("\\d{6}")) {
            int ano = Integer.parseInt(vencimento.substring(0, 4));
            int mes = Integer.parseInt(vencimento.substring(4, 6));
            sql.append(" AND YEAR(data_vencimento) = :ano AND MONTH(data_vencimento) = :mes");
            params.addValue("ano", ano).addValue("mes", mes);
        }

        sql.append(" ORDER BY data_vencimento, bdi, preco_exercicio");

        return jdbc.query(sql.toString(), params,
                (rs, i) -> new OpcaoB3DTO(
                        rs.getString("simbolo"),
                        rs.getString("bdi"),
                        rs.getDate("data_pregao").toLocalDate(),
                        rs.getDate("data_vencimento").toLocalDate(),
                        rs.getBigDecimal("preco_exercicio"),
                        rs.getBigDecimal("abertura"),
                        rs.getBigDecimal("maxima"),
                        rs.getBigDecimal("minima"),
                        rs.getBigDecimal("fechamento"),
                        rs.getBigDecimal("preco_medio"),
                        rs.getObject("volume", Long.class),
                        rs.getObject("numero_negocios", Integer.class),
                        rs.getBigDecimal("volume_financeiro")));
    }

    /** Vencimentos disponiveis para um ativo (formato YYYYMM, ordenado). */
    public List<String> vencimentos(String simbolo) {
        String prefixo = simbolo.substring(0, Math.min(4, simbolo.length())) + "%";
        return jdbc.queryForList(
                "SELECT DISTINCT DATE_FORMAT(data_vencimento, '%Y%m') AS vm "
                        + "FROM opcao_b3_diaria WHERE simbolo LIKE :prefixo ORDER BY vm",
                new MapSqlParameterSource("prefixo", prefixo),
                String.class);
    }
}
