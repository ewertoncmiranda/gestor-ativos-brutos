package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoAtivoNaoEncontrado;
import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoConversaoJson;
import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.Ativo;
import br.com.miranda.gestor.ativos.brutos.external.dto.AnaliseConsolidadaDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaAnaliseIaDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBrApi;
import br.com.miranda.gestor.ativos.brutos.port.PortaFilaMensagens;
import br.com.miranda.gestor.ativos.brutos.tools.ConsolidadorAnaliseAcao;
import br.com.miranda.gestor.ativos.brutos.tools.ConversorJson;
import br.com.miranda.gestor.ativos.brutos.tools.MontadorPromptAnalise;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

@Slf4j
@Service
public class ServicoAtivo {

    private final ClienteBrApi clienteBrApi;
    private final PortaFilaMensagens filaMensagens;
    private final ServicoAnaliseAcao servicoAnaliseAcao;
    private final ModelMapper mapper = new ModelMapper();
    private final ServicoGemini servicoGemini;
    private final ObjectMapper objectMapper;
    private final ServicoArmazenamentoAnalise servicoArmazenamentoAnalise;

    public ServicoAtivo(
            ClienteBrApi clienteBrApi,
            PortaFilaMensagens filaMensagens,
            ServicoArmazenamentoAnalise servicoArmazenamentoAnalise,
            ServicoAnaliseAcao servicoAnaliseAcao,
            ServicoGemini servicoGemini,
            ObjectMapper objectMapper
    ) {
        this.clienteBrApi = clienteBrApi;
        this.filaMensagens = filaMensagens;
        this.servicoAnaliseAcao = servicoAnaliseAcao;
        this.servicoGemini = servicoGemini;
        this.objectMapper = objectMapper;
        this.servicoArmazenamentoAnalise = servicoArmazenamentoAnalise;
    }

    /**
     * Consulta o ativo, publica o payload bruto no SQS e gera análise consolidada quando houver base histórica.
     */
    public Ativo processar(String codigoAtivo) {
        Ativo ativo = consultarAtivo(codigoAtivo);
        publicarAtivoNaFila(ativo);

        List<AnaliseAcaoEntity> analises = servicoAnaliseAcao.buscarPorSimboloConsultaNativa(codigoAtivo);
        AnaliseConsolidadaDTO consolidado = ConsolidadorAnaliseAcao.consolidar(analises);
        if (Objects.isNull(consolidado)) {
            log.warn("{} - Nenhum insight consolidado encontrado para ativo: {}", SERVICO, codigoAtivo);
            return ativo;
        }

        String prompt = MontadorPromptAnalise.montarPromptAnaliseQuantitativa(consolidado);
        RespostaAnaliseIaDTO analise = limparEResolverJson(servicoGemini.gerarConteudo(prompt));
        servicoArmazenamentoAnalise.salvarAnaliseNoS3(analise, codigoAtivo);

        return ativo;
    }

    /**
     * Consulta o ativo na BRAPI e publica os dados brutos na fila, sem gerar análise por IA.
     */
    public Ativo buscarEProcessarAtivo(String codigoAtivo) {
        Ativo ativo = consultarAtivo(codigoAtivo);
        publicarAtivoNaFila(ativo);
        return ativo;
    }

    private Ativo consultarAtivo(String codigoAtivo) {
        var retorno = clienteBrApi.consultarCotacao(codigoAtivo);
        if (Objects.isNull(retorno) || retorno.getResults().isEmpty()) {
            log.error("{} - Nenhum dado retornado para ativo: {}", SERVICO, codigoAtivo);
            throw new ExcecaoAtivoNaoEncontrado(codigoAtivo);
        }

        AtivoBrapiDTO brapiDto = retorno.getResults().getFirst();
        return mapper.map(brapiDto, Ativo.class);
    }

    private void publicarAtivoNaFila(Ativo ativo) {
        String payload = ConversorJson.paraJson(ativo);
        log.info("{} - Payload JSON gerado com {} bytes", SERVICO, payload.length());
        filaMensagens.enviarMensagemParaFila(payload);
    }

    private RespostaAnaliseIaDTO limparEResolverJson(String rawResponse) {
        try {
            String cleanJson = rawResponse
                    .replaceAll("(?i)```json", "")
                    .replace("```", "")
                    .trim();

            return objectMapper.readValue(cleanJson, RespostaAnaliseIaDTO.class);
        } catch (Exception e) {
            log.error("{} - Erro ao parsear resposta da IA: {}", SERVICO, e.getMessage(), e);
            throw new ExcecaoConversaoJson("resposta da IA em formato inesperado", e);
        }
    }
}
