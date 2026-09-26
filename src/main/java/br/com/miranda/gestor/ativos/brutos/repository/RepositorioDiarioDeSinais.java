package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Leitura do diario de sinais ({@code sinal_diario}, {@code sinal_resultado};
 * contrato {@code infra#CTR-11}). As tabelas sao do gerar-insights, que e o
 * unico escritor.
 *
 * <p>SQL explicito em vez de entidade JPA de proposito: com
 * {@code ddl-auto=update}, uma entidade aqui faria o Hibernate alterar tabela
 * de outro servico.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioDiarioDeSinais {

    private final NamedParameterJdbcTemplate jdbc;

    public record Sinal(long id, String simbolo, LocalDate dataPregao, String versaoRegra,
                        String recomendacao, String nivelRisco, Integer confiancaScore,
                        BigDecimal precoFechamento) {
    }

    public record Resultado(long sinalId, int horizonte, LocalDate dataSaida, BigDecimal retornoLiquido,
                            BigDecimal excessoCdi, BigDecimal excessoCarteira, Integer ativosNaCarteira,
                            Boolean acerto, boolean eventoSuspeito) {
    }

    public record LinhaPlacar(String versaoRegra, String recomendacao, int horizonte, long avaliados,
                              long comDirecao, long acertos, BigDecimal retornoMedio,
                              BigDecimal excessoMedioCdi, BigDecimal excessoMedioCarteira) {
    }

    public record Totais(long sinais, long ativos, LocalDate primeiroPregao, LocalDate ultimoPregao,
                         long resultados) {
    }

    public Totais totais(String simbolo) {
        MapSqlParameterSource p = filtro(simbolo);
        return jdbc.queryForObject(
                "SELECT COUNT(*) sinais, COUNT(DISTINCT s.simbolo) ativos, MIN(s.data_pregao) primeiro, "
                        + "MAX(s.data_pregao) ultimo, "
                        + "(SELECT COUNT(*) FROM sinal_resultado r JOIN sinal_diario x ON x.id = r.sinal_id "
                        + " WHERE (:simbolo IS NULL OR x.simbolo = :simbolo)) resultados "
                        + "FROM sinal_diario s WHERE (:simbolo IS NULL OR s.simbolo = :simbolo)",
                p,
                (rs, i) -> new Totais(rs.getLong("sinais"), rs.getLong("ativos"), data(rs, "primeiro"),
                        data(rs, "ultimo"), rs.getLong("resultados")));
    }

    public List<Sinal> sinaisRecentes(String simbolo, int limite) {
        MapSqlParameterSource p = filtro(simbolo).addValue("limite", limite);
        return jdbc.query(
                "SELECT id, simbolo, data_pregao, versao_regra, recomendacao, nivel_risco, confianca_score, "
                        + "preco_fechamento FROM sinal_diario WHERE (:simbolo IS NULL OR simbolo = :simbolo) "
                        + "ORDER BY data_pregao DESC, simbolo LIMIT :limite",
                p,
                (rs, i) -> new Sinal(rs.getLong("id"), rs.getString("simbolo"), data(rs, "data_pregao"),
                        rs.getString("versao_regra"), rs.getString("recomendacao"), rs.getString("nivel_risco"),
                        (Integer) rs.getObject("confianca_score", Integer.class), rs.getBigDecimal("preco_fechamento")));
    }

    public List<Resultado> resultadosDe(List<Long> idsDeSinais) {
        if (idsDeSinais.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT sinal_id, horizonte, data_saida, retorno_liquido, excesso_cdi, excesso_carteira, "
                        + "ativos_na_carteira, acerto, evento_suspeito FROM sinal_resultado WHERE sinal_id IN (:ids)",
                Map.of("ids", idsDeSinais),
                (rs, i) -> new Resultado(rs.getLong("sinal_id"), rs.getInt("horizonte"), data(rs, "data_saida"),
                        rs.getBigDecimal("retorno_liquido"), rs.getBigDecimal("excesso_cdi"),
                        rs.getBigDecimal("excesso_carteira"), rs.getObject("ativos_na_carteira", Integer.class),
                        (Boolean) rs.getObject("acerto", Boolean.class), rs.getBoolean("evento_suspeito")));
    }

    /** Janelas suspeitas de desdobramento ficam fora: nao sao acerto nem erro da regra. */
    public List<LinhaPlacar> placar(String simbolo) {
        return jdbc.query(
                "SELECT s.versao_regra, s.recomendacao, r.horizonte, COUNT(*) avaliados, "
                        + "SUM(r.acerto IS NOT NULL) com_direcao, SUM(r.acerto = TRUE) acertos, "
                        + "AVG(r.retorno_liquido) retorno_medio, AVG(r.excesso_cdi) excesso_cdi, "
                        + "AVG(r.excesso_carteira) excesso_carteira "
                        + "FROM sinal_resultado r JOIN sinal_diario s ON s.id = r.sinal_id "
                        + "WHERE r.evento_suspeito = FALSE AND (:simbolo IS NULL OR s.simbolo = :simbolo) "
                        + "GROUP BY s.versao_regra, s.recomendacao, r.horizonte",
                filtro(simbolo),
                (rs, i) -> new LinhaPlacar(rs.getString("versao_regra"), rs.getString("recomendacao"),
                        rs.getInt("horizonte"), rs.getLong("avaliados"), rs.getLong("com_direcao"),
                        rs.getLong("acertos"), rs.getBigDecimal("retorno_medio"),
                        rs.getBigDecimal("excesso_cdi"), rs.getBigDecimal("excesso_carteira")));
    }

    /**
     * Taxa-base por horizonte: fracao de TODAS as janelas avaliadas que
     * terminaram em alta, qualquer que fosse a recomendacao. E a regua do
     * acerto: se 60% das janelas subiram, uma compra que acerta 60% nao
     * acrescenta nada.
     */
    public Map<Integer, BigDecimal> taxaBaseDeAlta(String simbolo) {
        return jdbc.query(
                "SELECT r.horizonte, AVG(r.retorno_liquido > 0) taxa FROM sinal_resultado r "
                        + "JOIN sinal_diario s ON s.id = r.sinal_id "
                        + "WHERE r.evento_suspeito = FALSE AND (:simbolo IS NULL OR s.simbolo = :simbolo) "
                        + "GROUP BY r.horizonte",
                filtro(simbolo),
                rs -> {
                    Map<Integer, BigDecimal> taxas = new java.util.TreeMap<>();
                    while (rs.next()) {
                        taxas.put(rs.getInt("horizonte"), rs.getBigDecimal("taxa"));
                    }
                    return taxas;
                });
    }

    public List<String> simbolosComSinal() {
        return jdbc.queryForList("SELECT DISTINCT simbolo FROM sinal_diario ORDER BY simbolo",
                Map.of(), String.class);
    }

    private static MapSqlParameterSource filtro(String simbolo) {
        return new MapSqlParameterSource("simbolo", simbolo);
    }

    private static LocalDate data(ResultSet rs, String coluna) throws SQLException {
        Date valor = rs.getDate(coluna);
        return valor == null ? null : valor.toLocalDate();
    }
}
