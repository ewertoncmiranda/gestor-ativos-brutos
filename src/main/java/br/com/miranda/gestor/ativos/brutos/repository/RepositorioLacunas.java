package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.FatorAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.ProventoContabilDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leituras do Plano LAC por ativo (LAC-GES-2 fatores, LAC-GES-3 proventos
 * contabeis; {@code fator_valor}, {@code fator_definicao},
 * {@code provento_contabil}, infra V16). Por JDBC, sem entidade JPA - mesma
 * razao de {@link RepositorioConsumoBrapi}: sem a V16, devolve lista vazia e
 * avisa uma vez no log, nunca 500.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RepositorioLacunas {

    private final NamedParameterJdbcTemplate jdbc;
    private final AtomicBoolean avisouAusente = new AtomicBoolean(false);

    public List<FatorAtivoDTO> fatoresPorSimbolo(String simbolo) {
        try {
            return jdbc.query(
                    "SELECT fv.fator_codigo, fd.familia, fd.descricao, fd.direcao_esperada, "
                            + " fv.data_referencia, fv.valor, fv.percentil_universo, fv.percentil_setor, fv.grupo_setor "
                            + "FROM fator_valor fv "
                            + "JOIN fator_definicao fd ON fd.codigo = fv.fator_codigo AND fd.ativo = TRUE "
                            + "WHERE fv.simbolo = :simbolo AND fv.data_referencia = ("
                            + "  SELECT MAX(fv2.data_referencia) FROM fator_valor fv2 "
                            + "  WHERE fv2.simbolo = fv.simbolo AND fv2.fator_codigo = fv.fator_codigo) "
                            + "ORDER BY fd.familia, fd.codigo",
                    new MapSqlParameterSource("simbolo", simbolo.toUpperCase()),
                    (rs, i) -> new FatorAtivoDTO(rs.getString("fator_codigo"), rs.getString("familia"),
                            rs.getString("descricao"), rs.getInt("direcao_esperada"),
                            rs.getDate("data_referencia") == null ? null : rs.getDate("data_referencia").toLocalDate(),
                            rs.getBigDecimal("valor"), rs.getBigDecimal("percentil_universo"),
                            rs.getBigDecimal("percentil_setor"), rs.getString("grupo_setor")));
        } catch (DataAccessException e) {
            avisarUmaVez(e);
            return List.of();
        }
    }

    /** cvm_ticker liga simbolo (PETR4) a cnpj, chave de provento_contabil (por emissora, nao por especie). */
    public List<ProventoContabilDTO> proventosContabeisPorSimbolo(String simbolo) {
        try {
            return jdbc.query(
                    "SELECT pc.tipo_doc, pc.dt_ini_exerc, pc.dt_fim_exerc, pc.data_entrega, "
                            + " pc.jcp, pc.dividendos, pc.total, pc.por_acao, pc.origem "
                            + "FROM provento_contabil pc "
                            + "JOIN cvm_ticker t ON t.cnpj = pc.cnpj "
                            + "WHERE t.simbolo = :simbolo "
                            + "ORDER BY pc.dt_fim_exerc DESC",
                    new MapSqlParameterSource("simbolo", simbolo.toUpperCase()),
                    (rs, i) -> new ProventoContabilDTO(rs.getString("tipo_doc"),
                            data(rs, "dt_ini_exerc"), data(rs, "dt_fim_exerc"), data(rs, "data_entrega"),
                            rs.getBigDecimal("jcp"), rs.getBigDecimal("dividendos"), rs.getBigDecimal("total"),
                            rs.getBigDecimal("por_acao"), rs.getString("origem")));
        } catch (DataAccessException e) {
            avisarUmaVez(e);
            return List.of();
        }
    }

    /** Idade dos dois calculos novos (Plano LAC, LAC-GES-4) - direto nas tabelas, nao ha job em etl_execucao ainda. */
    public Map<String, java.time.LocalDateTime> ultimosCalculos() {
        try {
            java.time.LocalDateTime fatores = jdbc.getJdbcTemplate()
                    .queryForObject("SELECT MAX(calculado_em) FROM fator_valor", java.time.LocalDateTime.class);
            java.time.LocalDateTime eventos = jdbc.getJdbcTemplate()
                    .queryForObject("SELECT MAX(atualizado_em) FROM evento_corporativo", java.time.LocalDateTime.class);
            // Map.of() nao aceita valor null (MAX sem linha nenhuma devolve null);
            // HashMap aceita - "sem calculo ainda" precisa continuar distinguivel
            // de "indisponivel" (mapa vazio, tabela ausente).
            Map<String, java.time.LocalDateTime> saida = new java.util.HashMap<>();
            saida.put("FATORES", fatores);
            saida.put("EVENTOS_CORPORATIVOS", eventos);
            return saida;
        } catch (DataAccessException e) {
            avisarUmaVez(e);
            return Map.of();
        }
    }

    /** Quantas empresas distintas ja tem ao menos um provento contabil (DVA) carregado. */
    public long coberturaProventosContabeis() {
        try {
            Long total = jdbc.getJdbcTemplate()
                    .queryForObject("SELECT COUNT(DISTINCT cnpj) FROM provento_contabil", Long.class);
            return total == null ? 0 : total;
        } catch (DataAccessException e) {
            avisarUmaVez(e);
            return 0;
        }
    }

    private void avisarUmaVez(DataAccessException e) {
        if (avisouAusente.compareAndSet(false, true)) {
            log.warn("(PLANO-LAC)-tabelas do Plano LAC indisponiveis (migracao V16 aplicada?); "
                    + "devolvendo vazio: {}", e.getMostSpecificCause().getMessage());
        }
    }

    private static java.time.LocalDate data(java.sql.ResultSet rs, String coluna) throws java.sql.SQLException {
        java.sql.Date valor = rs.getDate(coluna);
        return valor == null ? null : valor.toLocalDate();
    }
}
