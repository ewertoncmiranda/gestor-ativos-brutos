package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.exceptions.JsonConversionException;
import br.com.miranda.gestor.ativos.brutos.external.InsightAcao;
import br.com.miranda.gestor.ativos.brutos.external.dto.AiAnalysisResponseDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsightConsolidadoDTO;
import br.com.miranda.gestor.ativos.brutos.service.GeminiService;
import br.com.miranda.gestor.ativos.brutos.service.InsightAcaoService;
import br.com.miranda.gestor.ativos.brutos.tools.InsightConsolidator;
import br.com.miranda.gestor.ativos.brutos.tools.PromptBuilderUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.BRAPI_SERVICE;

@Slf4j
@RestController
@RequestMapping("/insights")
@RequiredArgsConstructor
public class InsightAcaoController {

    private final InsightAcaoService service;
    private final GeminiService gemini;
    private final ObjectMapper objectMapper;

    @GetMapping("/{simbolo}/analise")
    public Mono<AiAnalysisResponseDTO> buscarPorSimbolo(@PathVariable String simbolo) {
        log.info("{}-Buscando insights e gerando analise IA para simbolo: {}", BRAPI_SERVICE, simbolo);
        List<InsightAcao> insights = service.buscarPorSimbolo(simbolo);

        if (Objects.isNull(insights) || insights.isEmpty()) {
            log.warn("{}-Nenhum insight encontrado para simbolo: {}", BRAPI_SERVICE, simbolo);
            return Mono.just(AiAnalysisResponseDTO.builder()
                    .resumo("Nenhum insight encontrado para o simbolo: " + simbolo)
                    .build());
        }

        InsightConsolidadoDTO consolidado = InsightConsolidator.consolidar(insights);
        String prompt = PromptBuilderUtils.montarPromptAnaliseQuantitativa(consolidado);
        return gemini.gerarConteudo(prompt, "gemini-3-flash-preview")
                .map(this::limparEResolverJson);
    }

    private AiAnalysisResponseDTO limparEResolverJson(String rawResponse) {
        try {
            String cleanJson = rawResponse
                    .replaceAll("(?i)```json", "")
                    .replace("```", "")
                    .trim();

            return objectMapper.readValue(cleanJson, AiAnalysisResponseDTO.class);
        } catch (Exception e) {
            log.error("(CONTROLLER)-Erro ao parsear resposta da IA: {}", e.getMessage(), e);
            throw new JsonConversionException("resposta da IA em formato inesperado", e);
        }
    }
}
