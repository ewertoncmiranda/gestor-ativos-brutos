package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraFinanceiraDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class RepositorioCarteiraFinanceira {
    private static final String COLUNAS_OPERACAO = "id, tipo, operacao_referenciada_id, simbolo, executada_em, "
            + "quantidade, preco_unitario, valor_bruto, custos, impostos, valor_liquido, moeda, origem, chave_idempotencia";
    private static final String COLUNAS_PROVENTO = "id, simbolo, tipo, data_ex, data_pagamento, quantidade_elegivel, "
            + "valor_por_ativo, valor_bruto, imposto_retido, valor_liquido, moeda, referencia_evento, chave_idempotencia";
    private final JdbcTemplate jdbc;

    public RepositorioCarteiraFinanceira(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean schemaFinanceiroDisponivel() {
        Integer tabelas = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN (
                  'carteira_operacao', 'carteira_provento_recebido',
                  'carteira_ativo_valuation_diaria', 'carteira_rentabilidade_diaria')
                """, Integer.class);
        return tabelas != null && tabelas == 4;
    }

    public boolean carteiraAtiva(String id) {
        Integer quantidade = jdbc.queryForObject("SELECT COUNT(*) FROM carteira WHERE id = ? AND status = 'ATIVA'", Integer.class, id);
        return quantidade != null && quantidade > 0;
    }

    public List<CarteiraFinanceiraDTO.Operacao> operacoes(String carteiraId) {
        return jdbc.query("SELECT " + COLUNAS_OPERACAO + " FROM carteira_operacao WHERE carteira_id = ? ORDER BY executada_em, id",
                (rs, i) -> operacao(rs), carteiraId);
    }

    public Optional<CarteiraFinanceiraDTO.Operacao> operacao(String carteiraId, String id) {
        return jdbc.query("SELECT " + COLUNAS_OPERACAO + " FROM carteira_operacao WHERE carteira_id = ? AND id = ?",
                (rs, i) -> operacao(rs), carteiraId, id).stream().findFirst();
    }

    public Optional<CarteiraFinanceiraDTO.Operacao> operacaoPorChave(String carteiraId, String chave) {
        return jdbc.query("SELECT " + COLUNAS_OPERACAO + " FROM carteira_operacao WHERE carteira_id = ? AND chave_idempotencia = ?",
                (rs, i) -> operacao(rs), carteiraId, chave).stream().findFirst();
    }

    public void inserirOperacao(String carteiraId, CarteiraFinanceiraDTO.Operacao o) {
        jdbc.update("""
                INSERT INTO carteira_operacao (id, carteira_id, tipo, operacao_referenciada_id, simbolo, executada_em,
                  quantidade, preco_unitario, valor_bruto, custos, impostos, valor_liquido, moeda, origem, chave_idempotencia)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, o.id(), carteiraId, o.tipo(), o.operacaoReferenciadaId(), o.simbolo(), o.executadaEm(),
                o.quantidade(), o.precoUnitario(), o.valorBruto(), o.custos(), o.impostos(), o.valorLiquido(),
                o.moeda(), o.origem(), o.chaveIdempotencia());
    }

    public List<CarteiraFinanceiraDTO.Provento> proventos(String carteiraId) {
        return jdbc.query("SELECT " + COLUNAS_PROVENTO + " FROM carteira_provento_recebido WHERE carteira_id = ? ORDER BY data_pagamento, id",
                (rs, i) -> provento(rs), carteiraId);
    }

    public Optional<CarteiraFinanceiraDTO.Provento> proventoPorChave(String carteiraId, String chave) {
        return jdbc.query("SELECT " + COLUNAS_PROVENTO + " FROM carteira_provento_recebido WHERE carteira_id = ? AND chave_idempotencia = ?",
                (rs, i) -> provento(rs), carteiraId, chave).stream().findFirst();
    }

    public void inserirProvento(String carteiraId, CarteiraFinanceiraDTO.Provento p) {
        jdbc.update("""
                INSERT INTO carteira_provento_recebido (id, carteira_id, simbolo, tipo, data_ex, data_pagamento,
                  quantidade_elegivel, valor_por_ativo, valor_bruto, imposto_retido, valor_liquido, moeda,
                  referencia_evento, chave_idempotencia)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, p.id(), carteiraId, p.simbolo(), p.tipo(), p.dataEx(), p.dataPagamento(), p.quantidadeElegivel(),
                p.valorPorAtivo(), p.valorBruto(), p.impostoRetido(), p.valorLiquido(), p.moeda(),
                p.referenciaEvento(), p.chaveIdempotencia());
    }

    public Optional<Cotacao> cotacao(String simbolo, LocalDate ate) {
        return jdbc.query("""
                SELECT data_pregao, fechamento FROM cotacao_b3_diaria
                WHERE simbolo = ? AND data_pregao <= ? AND fechamento IS NOT NULL
                ORDER BY data_pregao DESC LIMIT 1
                """, (rs, i) -> new Cotacao(rs.getObject("data_pregao", LocalDate.class), rs.getBigDecimal("fechamento")),
                simbolo, ate).stream().findFirst();
    }

    public Optional<LocalDate> ultimoPregao(LocalDate ate) {
        return jdbc.query("SELECT MAX(data_pregao) FROM cotacao_b3_diaria WHERE data_pregao <= ?",
                (rs, i) -> rs.getObject(1, LocalDate.class), ate).stream().filter(Objects::nonNull).findFirst();
    }

    public void salvarAvaliacao(String carteiraId, CarteiraFinanceiraDTO.AvaliacaoAtivo a, LocalDate referencia) {
        jdbc.update("""
                INSERT INTO carteira_ativo_valuation_diaria
                  (carteira_id, simbolo, data_referencia, quantidade, preco_fechamento, data_pregao_preco,
                   fonte_preco, valor_mercado, versao_calculo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE quantidade = VALUES(quantidade), preco_fechamento = VALUES(preco_fechamento),
                  data_pregao_preco = VALUES(data_pregao_preco), fonte_preco = VALUES(fonte_preco),
                  valor_mercado = VALUES(valor_mercado), calculado_em = CURRENT_TIMESTAMP
                """, carteiraId, a.simbolo(), referencia, a.quantidade(), a.precoFechamento(), a.dataPregaoPreco(),
                a.fontePreco(), a.valorMercado(), a.versaoCalculo());
    }

    public void salvarRentabilidade(String carteiraId, CarteiraFinanceiraDTO.Rentabilidade r) {
        jdbc.update("""
                INSERT INTO carteira_rentabilidade_diaria
                  (carteira_id, data_referencia, moeda, valor_mercado, saldo_caixa, fluxo_externo,
                   proventos_liquidos, resultado_realizado, resultado_nao_realizado, retorno_periodo,
                   retorno_acumulado, metodo, versao_calculo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE moeda = VALUES(moeda), valor_mercado = VALUES(valor_mercado),
                  saldo_caixa = VALUES(saldo_caixa), fluxo_externo = VALUES(fluxo_externo),
                  proventos_liquidos = VALUES(proventos_liquidos), resultado_realizado = VALUES(resultado_realizado),
                  resultado_nao_realizado = VALUES(resultado_nao_realizado), retorno_periodo = VALUES(retorno_periodo),
                  retorno_acumulado = VALUES(retorno_acumulado), metodo = VALUES(metodo), calculado_em = CURRENT_TIMESTAMP
                """, carteiraId, r.dataReferencia(), r.moeda(), r.valorMercado(), r.saldoCaixa(), r.fluxoExterno(),
                r.proventosLiquidos(), r.resultadoRealizado(), r.resultadoNaoRealizado(), r.retornoPeriodo(),
                r.retornoAcumulado(), r.metodo(), r.versaoCalculo());
    }

    public Optional<CarteiraFinanceiraDTO.Rentabilidade> rentabilidadeAnterior(String carteiraId, LocalDate data, String versao) {
        return jdbc.query("""
                SELECT data_referencia, moeda, valor_mercado, saldo_caixa, fluxo_externo, proventos_liquidos,
                  resultado_realizado, resultado_nao_realizado, retorno_periodo, retorno_acumulado, metodo, versao_calculo, calculado_em
                FROM carteira_rentabilidade_diaria WHERE carteira_id = ? AND data_referencia < ? AND versao_calculo = ?
                ORDER BY data_referencia DESC LIMIT 1
                """, (rs, i) -> rentabilidade(rs), carteiraId, data, versao).stream().findFirst();
    }

    public List<CarteiraFinanceiraDTO.Rentabilidade> rentabilidades(String carteiraId, LocalDate inicio, LocalDate fim, String versao) {
        return jdbc.query("""
                SELECT data_referencia, moeda, valor_mercado, saldo_caixa, fluxo_externo, proventos_liquidos,
                  resultado_realizado, resultado_nao_realizado, retorno_periodo, retorno_acumulado, metodo, versao_calculo, calculado_em
                FROM carteira_rentabilidade_diaria WHERE carteira_id = ?
                  AND data_referencia >= ? AND data_referencia <= ? AND versao_calculo = ?
                ORDER BY data_referencia
                """, (rs, i) -> rentabilidade(rs), carteiraId, inicio, fim, versao);
    }

    public Optional<CarteiraFinanceiraDTO.Rentabilidade> rentabilidade(String carteiraId, LocalDate data, String versao) {
        return jdbc.query("""
                SELECT data_referencia, moeda, valor_mercado, saldo_caixa, fluxo_externo, proventos_liquidos,
                  resultado_realizado, resultado_nao_realizado, retorno_periodo, retorno_acumulado, metodo, versao_calculo, calculado_em
                FROM carteira_rentabilidade_diaria WHERE carteira_id = ? AND data_referencia = ? AND versao_calculo = ?
                """, (rs, i) -> rentabilidade(rs), carteiraId, data, versao).stream().findFirst();
    }

    public List<CarteiraFinanceiraDTO.AvaliacaoAtivo> avaliacoes(String carteiraId, LocalDate data, String versao) {
        return jdbc.query("""
                SELECT simbolo, quantidade, preco_fechamento, data_pregao_preco, fonte_preco, valor_mercado, versao_calculo
                FROM carteira_ativo_valuation_diaria
                WHERE carteira_id = ? AND data_referencia = ? AND versao_calculo = ? ORDER BY simbolo
                """, (rs, i) -> new CarteiraFinanceiraDTO.AvaliacaoAtivo(rs.getString("simbolo"),
                rs.getBigDecimal("quantidade"), rs.getBigDecimal("preco_fechamento"),
                rs.getObject("data_pregao_preco", LocalDate.class), rs.getString("fonte_preco"),
                rs.getBigDecimal("valor_mercado"), rs.getString("versao_calculo")), carteiraId, data, versao);
    }

    private CarteiraFinanceiraDTO.Operacao operacao(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CarteiraFinanceiraDTO.Operacao(rs.getString("id"), rs.getString("tipo"),
                rs.getString("operacao_referenciada_id"), rs.getString("simbolo"),
                rs.getObject("executada_em", LocalDateTime.class), rs.getBigDecimal("quantidade"),
                rs.getBigDecimal("preco_unitario"), rs.getBigDecimal("valor_bruto"), rs.getBigDecimal("custos"),
                rs.getBigDecimal("impostos"), rs.getBigDecimal("valor_liquido"), rs.getString("moeda"),
                rs.getString("origem"), rs.getString("chave_idempotencia"));
    }

    private CarteiraFinanceiraDTO.Provento provento(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CarteiraFinanceiraDTO.Provento(rs.getString("id"), rs.getString("simbolo"), rs.getString("tipo"),
                rs.getObject("data_ex", LocalDate.class), rs.getObject("data_pagamento", LocalDate.class),
                rs.getBigDecimal("quantidade_elegivel"), rs.getBigDecimal("valor_por_ativo"),
                rs.getBigDecimal("valor_bruto"), rs.getBigDecimal("imposto_retido"), rs.getBigDecimal("valor_liquido"),
                rs.getString("moeda"), rs.getString("referencia_evento"), rs.getString("chave_idempotencia"));
    }

    private CarteiraFinanceiraDTO.Rentabilidade rentabilidade(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CarteiraFinanceiraDTO.Rentabilidade(rs.getObject("data_referencia", LocalDate.class),
                rs.getString("moeda"), rs.getBigDecimal("valor_mercado"), rs.getBigDecimal("saldo_caixa"),
                rs.getBigDecimal("fluxo_externo"), rs.getBigDecimal("proventos_liquidos"),
                rs.getBigDecimal("resultado_realizado"), rs.getBigDecimal("resultado_nao_realizado"),
                rs.getBigDecimal("retorno_periodo"), rs.getBigDecimal("retorno_acumulado"), rs.getString("metodo"),
                rs.getString("versao_calculo"), rs.getObject("calculado_em", LocalDateTime.class));
    }

    public record Cotacao(LocalDate dataPregao, BigDecimal fechamento) { }
}
