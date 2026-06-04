package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.dto.InsightConsolidadoDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PromptBuilderUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static String montarPromptAnaliseQuantitativa(InsightConsolidadoDTO dados) {
        try {
            String jsonDados = MAPPER.writeValueAsString(dados);

            return String.format("""
                Você é um analista quantitativo especializado em análise de ativos da bolsa brasileira.

                Analise os dados consolidados do ativo abaixo.

                Considere:
                - persistência do sinal
                - consistência temporal
                - indicadores fundamentalistas
                - intensidade da variação
                - possíveis riscos
                - força do movimento

                Não invente informações.
                Baseie-se SOMENTE nos dados fornecidos.

                Responda obrigatoriamente no formato JSON.

                Estrutura esperada:

                {
                  "ativo": "",
                  "sentimento": "",
                  "forca_sinal": "",
                  "risco": "",
                  "confianca_analise": 0,
                  "resumo": "",
                  "analise_tecnica": "",
                  "analise_fundamentalista": "",
                  "possivel_cenario": "",
                  "recomendacao": ""
                }

                Dados consolidados:

                %s
                """, jsonDados);

        } catch (Exception e) {
            log.error("(PROMPT-BUILDER)-Erro ao converter DTO para JSON no prompt: {}", e.getMessage());
            return "Erro ao gerar prompt";
        }
    }
}
