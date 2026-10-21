package br.com.miranda.gestor.ativos.brutos.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Leitura do panorama do mercado (Plano MERC, MERC-GES-1). Somente SELECT em
 * cotacao_b3_diaria, ativo_liquidez_diaria, cvm_ticker/cvm_empresa,
 * setor_grupo e evento_corporativo. Aqui fica so a agregacao por pregao
 * (fechamentos dos extremos, soma de volume e de negocios, media de 21
 * pregoes); mediana, coesao, desvio, ordenacao e corte ficam no servico.
 */
@Repository
public class RepositorioPanoramaMercado {

    /**
     * Uma linha por ativo com liquidez calculada no ultimo pregao. Nome e setor
     * pela mesma regra de /base/ativos (cvm_ticker ativo -> cvm_empresa); ativo
     * sem cadastro CVM vem com nome/setor nulos. Fechamento ajustado por
     * fator_cotacao (preco por acao). O indice (especificacao IBO*) fica fora:
     * nao e ativo negociavel.
     */
    private static final String ATIVOS = """
            SELECT l.simbolo, e.denominacao AS nome, e.setor, g.grupo_setor AS grupo,
                   l.volume_financeiro_medio_63d, l.presenca_63d, l.spread_estimado_63d,
                   cf.fechamento / NULLIF(cf.fator_cotacao, 0) AS fechamento_final,
                   cb.fechamento / NULLIF(cb.fator_cotacao, 0) AS fechamento_base,
                   p.volume_periodo, p.negocios_periodo, m.media_21,
                   EXISTS(SELECT 1 FROM evento_corporativo ev
                           WHERE (ev.simbolo = l.simbolo OR ev.cnpj = t.cnpj)
                             AND ev.data_efeito > ? AND ev.data_efeito <= ?) AS evento_periodo,
                   EXISTS(SELECT 1 FROM evento_corporativo ev
                           WHERE (ev.simbolo = l.simbolo OR ev.cnpj = t.cnpj)
                             AND ev.data_efeito > ? AND ev.data_efeito <= ?) AS evento_21
            FROM ativo_liquidez_diaria l
            LEFT JOIN cvm_ticker t ON t.simbolo = l.simbolo AND t.ativo = TRUE
            LEFT JOIN cvm_empresa e ON e.cnpj = t.cnpj
            LEFT JOIN setor_grupo g ON g.setor_cvm = e.setor
            LEFT JOIN cotacao_b3_diaria cf ON cf.simbolo = l.simbolo AND cf.data_pregao = ?
            LEFT JOIN cotacao_b3_diaria cb ON cb.simbolo = l.simbolo AND cb.data_pregao = ?
            LEFT JOIN (SELECT simbolo, SUM(volume_financeiro) AS volume_periodo,
                              SUM(numero_negocios) AS negocios_periodo
                         FROM cotacao_b3_diaria
                        WHERE data_pregao BETWEEN ? AND ?
                        GROUP BY simbolo) p ON p.simbolo = l.simbolo
            LEFT JOIN (SELECT simbolo, AVG(fechamento / NULLIF(fator_cotacao, 0)) AS media_21
                         FROM cotacao_b3_diaria
                        WHERE data_pregao BETWEEN ? AND ?
                        GROUP BY simbolo) m ON m.simbolo = l.simbolo
            WHERE l.data_pregao = ?
              AND (cf.especificacao IS NULL OR cf.especificacao NOT LIKE 'IBO%')
            """;

    private final JdbcTemplate jdbc;

    public RepositorioPanoramaMercado(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ultimo pregao = MAX(data_pregao) de cotacao_b3_diaria. */
    public Optional<LocalDate> ultimoPregao() {
        Date data = jdbc.queryForObject("SELECT MAX(data_pregao) FROM cotacao_b3_diaria", Date.class);
        return Optional.ofNullable(data).map(Date::toLocalDate);
    }

    /** Os {@code quantidade} pregoes mais recentes ate {@code ate} (inclusive), do mais novo ao mais antigo. */
    public List<LocalDate> pregoesAte(LocalDate ate, int quantidade) {
        return jdbc.query("""
                SELECT DISTINCT data_pregao FROM cotacao_b3_diaria
                WHERE data_pregao <= ? ORDER BY data_pregao DESC LIMIT ?
                """, (rs, i) -> rs.getDate(1).toLocalDate(), Date.valueOf(ate), quantidade);
    }

    /** Primeiro pregao em [desde, ate]. */
    public Optional<LocalDate> primeiroPregaoEntre(LocalDate desde, LocalDate ate) {
        Date data = jdbc.queryForObject(
                "SELECT MIN(data_pregao) FROM cotacao_b3_diaria WHERE data_pregao >= ? AND data_pregao <= ?",
                Date.class, Date.valueOf(desde), Date.valueOf(ate));
        return Optional.ofNullable(data).map(Date::toLocalDate);
    }

    /** Pregao imediatamente anterior a {@code data}. */
    public Optional<LocalDate> pregaoAnterior(LocalDate data) {
        Date anterior = jdbc.queryForObject(
                "SELECT MAX(data_pregao) FROM cotacao_b3_diaria WHERE data_pregao < ?", Date.class,
                Date.valueOf(data));
        return Optional.ofNullable(anterior).map(Date::toLocalDate);
    }

    /**
     * Ativos com liquidez em {@code dataPregao}, com os agregados do periodo.
     *
     * @param dataBase     pregao de referencia (fechamento inicial da variacao); pode ser nulo
     * @param dataInicial  primeiro pregao do periodo (inicio da soma de volume e negocios)
     * @param dataPregao   ultimo pregao
     * @param inicioMedia21 primeiro dos 21 pregoes da media movel
     * @param baseMedia21  pregao anterior a {@code inicioMedia21} (limite do evento na janela da media); pode ser nulo
     */
    public List<LinhaAtivo> ativos(LocalDate dataBase, LocalDate dataInicial, LocalDate dataPregao,
                                   LocalDate inicioMedia21, LocalDate baseMedia21) {
        Date base = dataBase == null ? null : Date.valueOf(dataBase);
        Date inicial = Date.valueOf(dataInicial);
        Date pregao = Date.valueOf(dataPregao);
        Date inicio21 = Date.valueOf(inicioMedia21);
        Date base21 = Date.valueOf(baseMedia21 == null ? inicioMedia21.minusDays(1) : baseMedia21);
        return jdbc.query(ATIVOS, (rs, i) -> linha(rs),
                base, pregao, base21, pregao, pregao, base, inicial, pregao, inicio21, pregao, pregao);
    }

    private static LinhaAtivo linha(ResultSet rs) throws SQLException {
        Long negocios = rs.getObject("negocios_periodo") == null ? null : rs.getLong("negocios_periodo");
        return new LinhaAtivo(rs.getString("simbolo"), rs.getString("nome"), rs.getString("setor"),
                rs.getString("grupo"), real(rs, "volume_financeiro_medio_63d"), real(rs, "presenca_63d"),
                real(rs, "spread_estimado_63d"), real(rs, "fechamento_final"), real(rs, "fechamento_base"),
                valorOuZero(real(rs, "volume_periodo")), negocios == null ? 0L : negocios, real(rs, "media_21"),
                rs.getBoolean("evento_periodo"), rs.getBoolean("evento_21"));
    }

    private static Double real(ResultSet rs, String coluna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(coluna);
        return valor == null ? null : valor.doubleValue();
    }

    private static double valorOuZero(Double valor) {
        return valor == null ? 0.0 : valor;
    }

    /**
     * Um ativo no ultimo pregao. Fechamentos ja ajustados por fator_cotacao;
     * volume e negocios somados no periodo; eventoPeriodo/evento21 = ha evento
     * corporativo (nao ajustado na serie) dentro do periodo / dos 21 pregoes.
     */
    public record LinhaAtivo(String simbolo, String nome, String setor, String grupo, Double volumeMedio63d,
                             Double presenca, Double spreadEstimado, Double fechamentoFinal, Double fechamentoBase,
                             double volumePeriodo, long negociosPeriodo, Double media21, boolean eventoPeriodo,
                             boolean evento21) {
    }

    /** Volume do pregao e media de 21 pregoes para calcular volume anormal (MERC-GES-2). */
    public record LinhaVolumeAnormal(String simbolo, double volumePregao, double media21) {
    }

    /**
     * Indicadores macro ja convertidos para fracao onde aplicavel: selic e
     * cdiPeriodo como fracao anual/periodo (0.15 = 15%), dolar em BRL,
     * ipca12m como fracao do produto composto dos ultimos 12 meses mensais
     * (MERC-GES-2).
     */
    public record LinhaMacro(Double selic, Double cdiPeriodo, Double dolar, Double ipca12m) {
    }

    /** Evento corporativo recente (MERC-GES-2). */
    public record LinhaEvento(String simbolo, String tipo, LocalDate data, String fator) {
    }

    // -------------------------------------------------------------------------
    // MERC-GES-2: volume anormal, macro, eventos
    // -------------------------------------------------------------------------

    /**
     * Volume financeiro do pregao e media dos 21 pregoes ({@code dataInicial21}
     * ate {@code dataPregao}) para cada ativo do universo liquido. A razao
     * (volumePregao / media21) e calculada no servico.
     */
    public List<LinhaVolumeAnormal> volumeAnormal(LocalDate dataPregao, LocalDate dataInicial21) {
        return jdbc.query("""
                SELECT c.simbolo,
                       c.volume_financeiro AS volume_pregao,
                       m.media_21
                FROM cotacao_b3_diaria c
                JOIN ativo_liquidez_diaria l
                     ON l.simbolo = c.simbolo AND l.data_pregao = ?
                JOIN (SELECT simbolo, AVG(volume_financeiro) AS media_21
                        FROM cotacao_b3_diaria
                       WHERE data_pregao BETWEEN ? AND ?
                       GROUP BY simbolo) m ON m.simbolo = c.simbolo
                WHERE c.data_pregao = ?
                  AND l.volume_financeiro_medio_63d >= 1000000
                  AND l.presenca_63d >= 0.80
                """,
                (rs, i) -> {
                    BigDecimal vol = rs.getBigDecimal("volume_pregao");
                    BigDecimal med = rs.getBigDecimal("media_21");
                    return new LinhaVolumeAnormal(
                            rs.getString("simbolo"),
                            vol == null ? 0.0 : vol.doubleValue(),
                            med == null ? 0.0 : med.doubleValue());
                },
                Date.valueOf(dataPregao),
                Date.valueOf(dataInicial21), Date.valueOf(dataPregao),
                Date.valueOf(dataPregao));
    }

    /**
     * Indicadores macroeconomicos para o bloco macro do panorama.
     *
     * <ul>
     *   <li>SELIC: ultimo valor ate {@code dataPregao}, convertido para fracao
     *       (15.0 -> 0.15)</li>
     *   <li>CDI acumulado: soma das taxas diarias de {@code dataInicial} ate
     *       {@code dataPregao}, convertida para fracao</li>
     *   <li>Dolar: ultimo valor ate {@code dataPregao} em BRL</li>
     *   <li>IPCA 12m: produto composto dos ultimos 12 pontos mensais ate
     *       {@code dataPregao}, retornado como fracao</li>
     * </ul>
     */
    public LinhaMacro macro(LocalDate dataPregao, LocalDate dataInicial) {
        Double selicRaw = ultimoValor("SELIC", dataPregao);
        Double selic = selicRaw == null ? null : selicRaw / 100.0;

        BigDecimal cdiSoma = jdbc.query(
                "SELECT SUM(valor) FROM indice_macro WHERE codigo_serie = 'CDI' AND data BETWEEN ? AND ?",
                (rs, i) -> rs.getBigDecimal(1),
                Date.valueOf(dataInicial), Date.valueOf(dataPregao))
                .stream().findFirst().orElse(null);
        Double cdiPeriodo = (cdiSoma == null) ? null : cdiSoma.doubleValue() / 100.0;

        Double dolar = ultimoValor("DOLAR", dataPregao);

        BigDecimal ipca = jdbc.query("""
                SELECT EXP(SUM(LOG(1 + valor / 100))) - 1
                FROM (SELECT valor FROM indice_macro
                       WHERE codigo_serie = 'IPCA' AND data <= ?
                       ORDER BY data DESC LIMIT 12) sub
                """,
                (rs, i) -> rs.getBigDecimal(1),
                Date.valueOf(dataPregao))
                .stream().findFirst().orElse(null);
        Double ipca12m = (ipca == null) ? null : ipca.doubleValue();

        return new LinhaMacro(selic, cdiPeriodo, dolar, ipca12m);
    }

    private Double ultimoValor(String codigoSerie, LocalDate ate) {
        return jdbc.query(
                "SELECT valor FROM indice_macro WHERE codigo_serie = ? AND data <= ? ORDER BY data DESC LIMIT 1",
                (rs, i) -> rs.getBigDecimal(1),
                codigoSerie, Date.valueOf(ate))
                .stream().findFirst()
                .map(v -> v == null ? null : v.doubleValue())
                .orElse(null);
    }

    /**
     * Os 10 eventos corporativos mais recentes com {@code data_efeito <=
     * dataPregao}, ordenados por data descendente e simbolo ascendente. O
     * campo fator usa a representacao legivel do evidencia_json quando
     * disponivel (ex: "11/10"), senao o valor numerico de fator_acoes.
     */
    public List<LinhaEvento> eventos(LocalDate dataPregao) {
        return jdbc.query("""
                SELECT simbolo, tipo, data_efeito,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(evidencia_json, '$.fator')),
                                CAST(fator_acoes AS CHAR)) AS fator
                FROM evento_corporativo
                WHERE data_efeito <= ?
                ORDER BY data_efeito DESC, simbolo ASC
                LIMIT 10
                """,
                (rs, i) -> new LinhaEvento(
                        rs.getString("simbolo"),
                        rs.getString("tipo"),
                        rs.getDate("data_efeito").toLocalDate(),
                        rs.getString("fator")),
                Date.valueOf(dataPregao));
    }
}
