package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Diagnostico GET-only da qualidade da cadeia de opcoes (OPT-GES-3). */
public record SaudeOpcoesDTO(
        StatusSaudeOpcoes status,
        String motivo,
        long totalRegistrosOpcoes,
        long registrosValidos,
        long registrosInvalidos,
        long registrosVencimento9999,
        long ativosMonitoradosComCadeia,
        long ativosMonitoradosSemCadeia,
        List<PrefixoSuspeito> prefixosSuspeitos,
        LocalDate ultimoPregaoComOpcoesValidas,
        BigDecimal percentualValido) {

    public enum StatusSaudeOpcoes {
        OK,
        SEM_CADEIA_VALIDA,
        BASE_INDISPONIVEL
    }

    public record PrefixoSuspeito(String prefixo, long totalRegistros) {}
}
