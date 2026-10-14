package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.IndicadorFundamentalistaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Carrega colunas F360 apenas se estiverem presentes na migration da base. */
@Repository
@RequiredArgsConstructor
public class RepositorioCamposFundamentosF360 {
    private static final List<String> COLUNAS = List.of("ebitda", "ativo_circulante", "passivo_circulante",
            "cobertura_juros", "liquidez_corrente", "liquidez_seca", "dso", "graham_number", "pl",
            "ev_ebitda", "pl_setorial_mediana");
    private final NamedParameterJdbcTemplate jdbc;

    public void preencher(IndicadorFundamentalistaEntity entidade) {
        try {
            Set<String> existentes = new HashSet<>(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'indicador_fundamentalista'",
                    new MapSqlParameterSource(), String.class));
            String selecao = COLUNAS.stream().map(c -> (existentes.contains(c) ? c : "NULL") + " AS " + c)
                    .reduce((a, b) -> a + ", " + b).orElseThrow();
            Map<String, Object> campos = jdbc.queryForMap("SELECT " + selecao + " FROM indicador_fundamentalista "
                    + "WHERE simbolo = :simbolo AND periodo = :periodo AND tipo_periodo = :tipo LIMIT 1",
                    new MapSqlParameterSource().addValue("simbolo", entidade.getSimbolo())
                            .addValue("periodo", entidade.getPeriodo()).addValue("tipo", entidade.getTipoPeriodo()));
            entidade.setEbitda((java.math.BigDecimal) campos.get("ebitda"));
            entidade.setAtivoCirculante((java.math.BigDecimal) campos.get("ativo_circulante"));
            entidade.setPassivoCirculante((java.math.BigDecimal) campos.get("passivo_circulante"));
            entidade.setCoberturaJuros((java.math.BigDecimal) campos.get("cobertura_juros"));
            entidade.setLiquidezCorrente((java.math.BigDecimal) campos.get("liquidez_corrente"));
            entidade.setLiquidezSeca((java.math.BigDecimal) campos.get("liquidez_seca"));
            entidade.setDso((java.math.BigDecimal) campos.get("dso"));
            entidade.setGrahamNumber((java.math.BigDecimal) campos.get("graham_number"));
            entidade.setPl((java.math.BigDecimal) campos.get("pl"));
            entidade.setEvEbitda((java.math.BigDecimal) campos.get("ev_ebitda"));
            entidade.setPlSetorialMediana((java.math.BigDecimal) campos.get("pl_setorial_mediana"));
        } catch (DataAccessException ignored) {
            // Uma base sem as colunas (ou a tabela) continua servindo o contrato legado.
        }
    }
}
