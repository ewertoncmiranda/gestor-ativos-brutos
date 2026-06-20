package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.dto.AnaliseConsolidadaDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public final class MontadorPromptAnalise {

    private MontadorPromptAnalise() {
    }

    /**
     * Monta o prompt compacto enviado ao Gemini a partir da análise consolidada.
     */
    public static String montarPromptAnaliseQuantitativa(AnaliseConsolidadaDTO dados) {
        try {
            return String.format(
                    "Analise indicadores; gere sentimento, forca_sinal, risco 0-100, confianca_analise 0-100, resumo <=200 caracteres, analise_tecnica, analise_fundamentalista, possivel_cenario e recomendacao. Dados: %s",
                    compactarDados(dados)
            );
        } catch (Exception e) {
            log.error("(PROMPT-BUILDER)-Erro ao compactar DTO no prompt: {}", e.getMessage());
            return "Erro ao gerar prompt";
        }
    }

    private static String compactarDados(AnaliseConsolidadaDTO dados) {
        String janelaInicio = dados.getJanelaAnalise() == null ? "" : dados.getJanelaAnalise().getInicio();
        String janelaFim = dados.getJanelaAnalise() == null ? "" : dados.getJanelaAnalise().getFim();

        return String.join(";",
                "ativo=" + valor(dados.getAtivo()),
                "janela=" + valor(janelaInicio) + ".." + valor(janelaFim),
                "registros=" + valor(dados.getQuantidadeRegistros()),
                "sinal=" + valor(dados.getSinalPredominante()),
                "perc_venda=" + valor(dados.getPercentualSinaisVenda()),
                "var_media=" + valor(dados.getVariacaoMedia()),
                "indicadores=" + compactarIndicadores(dados.getIndicadores())
        );
    }

    private static String compactarIndicadores(Map<String, Object> indicadores) {
        if (indicadores == null || indicadores.isEmpty()) {
            return "";
        }

        return indicadores.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + valor(entry.getValue()))
                .collect(Collectors.joining(","));
    }

    private static String valor(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }
}
