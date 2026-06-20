package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoConversaoJson;
import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.AnaliseConsolidadaDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaAnaliseIaDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAnaliseAcao;
import br.com.miranda.gestor.ativos.brutos.service.ServicoGemini;
import br.com.miranda.gestor.ativos.brutos.tools.ConsolidadorAnaliseAcao;
import br.com.miranda.gestor.ativos.brutos.tools.MontadorPromptAnalise;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.BRAPI_SERVICE;

@Slf4j
@RestController
@RequestMapping("/analises")
@RequiredArgsConstructor
public class ControladorAnaliseAcao {

    private final ServicoAnaliseAcao servicoAnaliseAcao;
    private final ServicoGemini servicoGemini;
    private final ObjectMapper objectMapper;

    /**
     * Consolida as análises persistidas de um ativo e gera interpretação estruturada por IA.
     */
    @GetMapping("/{simbolo}/analise")
    public RespostaAnaliseIaDTO buscarPorSimbolo(@PathVariable String simbolo) {
        log.info("{}-Buscando analises e gerando analise IA para simbolo: {}", BRAPI_SERVICE, simbolo);
        List<AnaliseAcaoEntity> analises = servicoAnaliseAcao.buscarPorSimbolo(simbolo);

        if (Objects.isNull(analises) || analises.isEmpty()) {
            log.warn("{}-Nenhuma analise encontrada para simbolo: {}", BRAPI_SERVICE, simbolo);
            return RespostaAnaliseIaDTO.builder()
                    .resumo("Nenhuma analise encontrada para o simbolo: " + simbolo)
                    .build();
        }

        AnaliseConsolidadaDTO consolidado = ConsolidadorAnaliseAcao.consolidar(analises);
        String prompt = MontadorPromptAnalise.montarPromptAnaliseQuantitativa(consolidado);
        String resposta = servicoGemini.gerarConteudo(prompt);
        return limparEResolverJson(resposta);
    }

    private RespostaAnaliseIaDTO limparEResolverJson(String rawResponse) {
        try {
            String cleanJson = rawResponse
                    .replaceAll("(?i)```json", "")
                    .replace("```", "")
                    .trim();

            return objectMapper.readValue(cleanJson, RespostaAnaliseIaDTO.class);
        } catch (Exception e) {
            log.error("(CONTROLADOR)-Erro ao parsear resposta da IA: {}", e.getMessage(), e);
            throw new ExcecaoConversaoJson("resposta da IA em formato inesperado", e);
        }
    }
}
