package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Visao do diario de sinais para o painel ({@code GET /validacao/diario}).
 *
 * <p>Taxa de acerto nunca vem sozinha: {@code taxaBase} e a fracao de janelas
 * que subiram no mesmo horizonte, a regua contra a qual o acerto se mede.
 * Abaixo de {@code amostraMinima} avaliacoes a linha vem marcada como
 * insuficiente - o numero existe, mas ainda nao significa nada.
 */
public record DiarioDeSinaisDTO(
        String simbolo,
        long totalSinais,
        long totalAtivos,
        LocalDate primeiroPregao,
        LocalDate ultimoPregao,
        long horizontesAvaliados,
        long horizontesPendentes,
        List<Integer> horizontes,
        int amostraMinima,
        List<String> simbolosDisponiveis,
        List<LinhaPlacar> placar,
        List<SinalNaLinhaDoTempo> linhaDoTempo,
        String aviso) {

    public record LinhaPlacar(
            String versaoRegra,
            String recomendacao,
            int direcao,
            int horizonte,
            long avaliados,
            long comDirecao,
            long acertos,
            BigDecimal taxaAcerto,
            BigDecimal taxaBase,
            BigDecimal retornoMedio,
            BigDecimal excessoMedioCdi,
            BigDecimal excessoMedioCarteira,
            boolean amostraSuficiente) {
    }

    public record SinalNaLinhaDoTempo(
            LocalDate dataPregao,
            String simbolo,
            String recomendacao,
            int direcao,
            String nivelRisco,
            Integer confiancaScore,
            BigDecimal precoFechamento,
            String versaoRegra,
            Map<Integer, ResultadoDoHorizonte> resultados) {
    }

    public record ResultadoDoHorizonte(
            LocalDate dataSaida,
            BigDecimal retornoLiquido,
            BigDecimal excessoCdi,
            // Media simples dos ativos monitorados no mesmo periodo (CTR-11)
            BigDecimal excessoCarteira,
            Integer ativosNaCarteira,
            Boolean acerto,
            boolean eventoSuspeito) {
    }
}
