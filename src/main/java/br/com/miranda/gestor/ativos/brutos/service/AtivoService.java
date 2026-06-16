package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.entrypoint.FilaIndisponivelException;
import br.com.miranda.gestor.ativos.brutos.external.Ativo;
import br.com.miranda.gestor.ativos.brutos.external.InsightAcao;
import br.com.miranda.gestor.ativos.brutos.external.dto.AiAnalysisResponseDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.BrapiAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsightConsolidadoDTO;
import br.com.miranda.gestor.ativos.brutos.port.QueueConnectPort;
import br.com.miranda.gestor.ativos.brutos.tools.InsightConsolidator;
import br.com.miranda.gestor.ativos.brutos.tools.PromptBuilderUtils;
import br.com.miranda.gestor.ativos.brutos.tools.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.SERVICE;

@Slf4j
@Service
public class AtivoService {

    private final ConsultaBrApiService consultaBrApiService;
    private final QueueConnectPort queueConnectPort;
    private final InsightAcaoService service;
    private final ModelMapper mapper = new ModelMapper();
    private final GeminiService gemini;
    private final ObjectMapper objectMapper;
    private final S3InsightService s3InsightService;


    public AtivoService(
            ConsultaBrApiService consultaBrApiService,
            QueueConnectPort queueConnectPort,
            S3InsightService s3InsightService,
            InsightAcaoService service,
        GeminiService gemini, ObjectMapper objectMapper
    ) {
        this.consultaBrApiService = consultaBrApiService;
        this.queueConnectPort = queueConnectPort;
        this.service = service;
        this.gemini = gemini;
        this.objectMapper = objectMapper;
        this.s3InsightService = s3InsightService;
    }


    public Ativo processar(String codAtivo) {
        var retorno = consultaBrApiService.executar(codAtivo);
        if (Objects.isNull(retorno) || retorno.getResults().isEmpty()) {
            log.error("{} - Nenhum dado retornado para ativo: {}", SERVICE, codAtivo);
            return null;
        }

        BrapiAtivoDTO brapiDto = retorno.getResults().getFirst();
        Ativo ativo = mapper.map(brapiDto, Ativo.class);
        String payload = Utils.toJson(ativo);

        log.info("{} - Payload JSON gerado com {} bytes", SERVICE, payload.length());

        try {
            queueConnectPort.enviarMensagemParaFila(payload);
            List<InsightAcao> insightAcaos = service.buscarPorSimboloNative(codAtivo);

            InsightConsolidadoDTO consolidado = InsightConsolidator.consolidar(insightAcaos);
            String prompt = PromptBuilderUtils.montarPromptAnaliseQuantitativa(consolidado);
            var resultado = gemini.gerarConteudo(prompt, "gemini-3-flash-preview")
                    .map(this::limparEResolverJson)
                    .doOnNext(analise ->{
                        s3InsightService.salvarInsightNoS3(analise, codAtivo);
                    });

        }catch (FilaIndisponivelException e) {
            log.error("{} - Falha ao enviar mensagem para fila,fluxo indisponivel {}", SERVICE, e.getMessage(), e);
        }

        return ativo;
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