package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Leitura do sistema operavel em MODO SIMULADO (plano OPR, OPR-GES-2..4), no formato do contrato CTR-OPR-PAI
 * (SPEC do painel, "Contrato /operacional/*"), que a tela de Operabilidade ja consome. Campos alem do contrato
 * ({@code modo}, {@code aviso}, {@code avisos}, {@code caixa}...) sao extras e nao atrapalham o painel.
 * Tabelas da V23 escritas pelo gerar-insights; o gestor so le. Nada aqui e ordem, recomendacao ou
 * autorizacao de capital real.
 *
 * <p>Valores monetarios em R$; fracoes (retornos, exposicao, drawdown) em decimal, ex.: 0.0052 = 0,52%;
 * {@code drawdown} e queda e nunca passa de zero. Ausente e {@code null}, nunca zero.
 */
public final class OperacionalDTO {

    public static final String MODO = "SIMULADO";
    public static final String AVISO =
            "Simulação (paper trading) com regra experimental. Não é ordem nem recomendação de investimento.";

    private OperacionalDTO() {
    }

    /** Codigo estavel + texto para o usuario (motivos de status e violacoes). */
    public record Motivo(String codigo, String descricao) {
    }

    /**
     * GET /operacional/resumo. {@code pregoesMinimos} e a trava da regra (ex.: 63), nao o que falta;
     * {@code pregoesAvaliados} e quantos dias de diario existem.
     */
    public record Resumo(String modo, String aviso, String versaoRegra, String statusRegra, String statusSistema,
                         List<Motivo> motivosStatus, LocalDate dataInicio, LocalDate ultimoPregao,
                         int pregoesAvaliados, Integer pregoesMinimos, BigDecimal capitalInicial,
                         BigDecimal patrimonio, BigDecimal caixa, BigDecimal exposicao, Integer posicoesAbertas,
                         BigDecimal retornoLiquidoAcumulado, BigDecimal cdiAcumulado,
                         BigDecimal cdiLiquidoIrAcumulado, BigDecimal excessoSobreCdiLiquido, BigDecimal drawdown,
                         BigDecimal drawdownMaximo, BigDecimal impostoEstimadoMes, Integer violacoesLiquidez,
                         long operacoesAbertas, long operacoesFechadas, List<String> avisos) {
    }

    /** Um dia do diario operacional. */
    public record PontoDiario(LocalDate dataPregao, BigDecimal capitalInicial, BigDecimal patrimonio,
                              BigDecimal caixa, BigDecimal exposicao, Integer posicoesAbertas,
                              Integer operacoesAbertasDia, Integer operacoesFechadasDia,
                              BigDecimal retornoBrutoDia, BigDecimal retornoLiquidoDia,
                              BigDecimal retornoLiquidoAcumulado, BigDecimal cdiDia, BigDecimal cdiAcumulado,
                              BigDecimal cdiLiquidoIrAcumulado, BigDecimal excessoSobreCdiLiquido,
                              BigDecimal drawdown, BigDecimal drawdownMaximo, BigDecimal impostoEstimadoMes,
                              Integer violacoesLiquidez, String statusSistema, List<Motivo> motivosStatus) {
    }

    /** GET /operacional/diario. */
    public record Diario(String modo, String aviso, String versaoRegra, LocalDate inicio, LocalDate fim,
                         List<PontoDiario> itens, List<String> avisos) {
    }

    /** Uma operacao simulada; {@code motivoSaidaLegivel} e {@code custosTotais} sao derivados na leitura. */
    public record Operacao(long id, String simbolo, String setorGrupo, int horizontePregoes, String status,
                           LocalDate dataDecisaoEntrada, LocalDate dataEntrada, BigDecimal precoEntrada,
                           Integer quantidade, BigDecimal valorEntrada, BigDecimal stopInicial,
                           BigDecimal stopAtual, String faixaLiquidezEntrada, JsonNode motivoEntrada,
                           LocalDate dataDecisaoSaida, LocalDate dataSaida, BigDecimal precoSaida,
                           String motivoSaida, String motivoSaidaLegivel, JsonNode motivoSaidaDetalhe,
                           BigDecimal custosEntrada, BigDecimal custosSaida, BigDecimal custosTotais,
                           BigDecimal resultadoBruto, BigDecimal resultadoPosCustos, BigDecimal impostoEstimado,
                           BigDecimal resultadoPosImpostoEstimado) {
    }

    /** GET /operacional/operacoes. */
    public record Operacoes(String modo, String aviso, String versaoRegra, int limite, int total,
                            List<Operacao> itens, List<String> avisos) {
    }

    /**
     * Um motivo de bloqueio. {@code codigo} e o da elegibilidade (OPR-INS-1) ou do sizing (OPR-INS-2);
     * {@code descricao} e o texto legivel; {@code categoria}: DADO | LIQUIDEZ.
     */
    public record MotivoBloqueio(String codigo, String descricao, String detalhe, JsonNode valor, JsonNode limite,
                                 String categoria, String eventId) {
    }

    /** Sinais rejeitados de um ativo no pregao, com todos os motivos; {@code faixa} = faixa de liquidez, se gravada. */
    public record ItemBloqueio(String simbolo, String faixa, List<MotivoBloqueio> motivos) {
    }

    /** GET /operacional/bloqueios, agrupado por ativo. */
    public record Bloqueios(String modo, String aviso, String versaoRegra, LocalDate dataPregao, int total,
                            List<ItemBloqueio> itens, List<String> avisos) {
    }

    /** Violacao de regra que bloqueia o sistema. {@code tipo} segue o alerta-risco.v1. */
    public record Violacao(String tipo, String mensagem, JsonNode valor, JsonNode limite) {
    }

    /**
     * GET /operacional/saude (painel e alerta). {@code status} e o do contrato status-sistema.v1;
     * {@code violacoes} e a lista do CTR-OPR-PAI e {@code violacoesDetalhe} traz valor e limite;
     * {@code ultimaExecucao} e a hora em que o diario mais recente foi gravado.
     */
    public record Saude(String modo, String aviso, String versaoRegra, String status, String statusDiario,
                        LocalDateTime ultimaExecucao, LocalDate ultimaAvaliacao, LocalDate ultimoPregaoDisponivel,
                        Boolean dadosDoDiaCompletos, List<Motivo> violacoes, List<Violacao> violacoesDetalhe,
                        long eventosPendentes, boolean tabelasDisponiveis, List<String> avisos) {
    }
}
