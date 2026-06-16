package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

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
    public Mono<AiAnalysisResponseDTO> buscarPorSimbolo(
            @PathVariable String simbolo) {

        log.info("{}-Buscando insights e gerando análise IA para simbolo: {}", BRAPI_SERVICE, simbolo);
        List<InsightAcao> sAcaos = service.buscarPorSimbolo(simbolo);

        if (Objects.isNull(sAcaos) || sAcaos.isEmpty()) {
            log.warn("{}-Nenhum insight encontrado para simbolo: {}", BRAPI_SERVICE, simbolo);
            return Mono.just(AiAnalysisResponseDTO.builder()
                    .resumo("Nenhum insight encontrado para o símbolo: " + simbolo)
                    .build());
        }

        InsightConsolidadoDTO consolidado = InsightConsolidator.consolidar(sAcaos);
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
            log.error("(CONTROLLER)-Erro ao parsear resposta da IA: {}", e.getMessage());
            return AiAnalysisResponseDTO.builder()
                    .resumo("Erro no processamento da IA. Conteúdo bruto: " + rawResponse)
                    .build();
        }
    }

}
