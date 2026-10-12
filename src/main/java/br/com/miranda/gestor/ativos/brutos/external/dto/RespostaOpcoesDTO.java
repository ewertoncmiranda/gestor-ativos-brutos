package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.util.List;

/**
 * Contrato conservador para cadeia de opcoes.
 *
 * <p>Quando a base ainda nao permite uma cadeia confiavel, a resposta continua
 * 200/GET-only, mas explica o vazio em vez de inferir por prefixo de ticker.</p>
 */
public record RespostaOpcoesDTO(
        String simboloSolicitado,
        String simboloCanonico,
        boolean aliasAplicado,
        StatusOpcoes status,
        String motivo,
        List<String> vencimentos,
        List<OpcaoB3DTO> opcoes) {

    public enum StatusOpcoes {
        OK,
        SEM_CADEIA_VALIDA,
        CARGA_OPCOES_AUSENTE,
        BASE_OPCOES_CONTAMINADA,
        TICKER_NORMALIZADO
    }
}
