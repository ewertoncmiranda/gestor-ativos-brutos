package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.ComposicaoCapitalDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Leitura de {@code cvm_composicao_capital} via {@code cvm_ticker}.
 * Prefere o DFP anual mais recente; cai para ITR quando nao ha DFP.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioComposicaoCapital {

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<ComposicaoCapitalDTO> porSimbolo(String simbolo) {
        List<ComposicaoCapitalDTO> rows = jdbc.query(
                "SELECT t.simbolo, e.cnpj, c.dt_refer, c.tipo_doc, "
                        + "c.qt_acao_ordinaria, c.qt_acao_preferencial, c.qt_acao_total, c.qt_acao_ex_tesouraria "
                        + "FROM cvm_ticker t "
                        + "JOIN cvm_empresa e ON e.cnpj = t.cnpj "
                        + "JOIN cvm_composicao_capital c ON c.cnpj = e.cnpj "
                        + "WHERE t.simbolo = :s "
                        + "ORDER BY FIELD(c.tipo_doc, 'DFP', 'ITR'), c.dt_refer DESC "
                        + "LIMIT 1",
                new MapSqlParameterSource("s", simbolo),
                (rs, i) -> new ComposicaoCapitalDTO(
                        rs.getString("simbolo"),
                        rs.getString("cnpj"),
                        rs.getDate("dt_refer").toLocalDate(),
                        rs.getString("tipo_doc"),
                        rs.getObject("qt_acao_ordinaria", Long.class),
                        rs.getObject("qt_acao_preferencial", Long.class),
                        rs.getObject("qt_acao_total", Long.class),
                        rs.getObject("qt_acao_ex_tesouraria", Long.class)));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
