package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Backtest walk-forward (contrato infra#CTR-14). {@code execucao} e null
 * enquanto nenhum backtest rodou com sucesso.
 */
public record BacktestDTO(Execucao execucao, List<Linha> placar, int amostraMinima, String aviso) {

    public record Execucao(long id, LocalDateTime finalizadoEm, LocalDate inicioPeriodo, LocalDate fimPeriodo,
                           LocalDate corteCalibracao, int ativos, int sinais, JsonNode parametros,
                           String observacoes) {
    }

    /** periodo: CALIBRACAO (ate o corte) ou TESTE (depois dele, congelado). */
    public record Linha(String versaoRegra, String periodo, String recomendacao, int direcao, int horizonte,
                        int avaliados, BigDecimal taxaAcerto, BigDecimal taxaBase, BigDecimal retornoMedio,
                        BigDecimal excessoMedioCdi, BigDecimal excessoMedioCarteira, boolean amostraSuficiente) {
    }
}
