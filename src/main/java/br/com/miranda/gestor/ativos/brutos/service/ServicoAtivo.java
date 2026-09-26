package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoAtivoNaoEncontrado;
import br.com.miranda.gestor.ativos.brutos.external.Ativo;
import br.com.miranda.gestor.ativos.brutos.external.dto.*;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBrApi;
import br.com.miranda.gestor.ativos.brutos.port.PortaFilaMensagens;
import br.com.miranda.gestor.ativos.brutos.tools.ConversorJson;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

@Slf4j
@Service
public class ServicoAtivo {

    private final ClienteBrApi clienteBrApi;
    private final PortaFilaMensagens filaMensagens;
    private final ModelMapper mapper = new ModelMapper();

    @Value("${aws.sqs.historical-series.queue.url}")
    private String filaSeriesHistoricasUrl;

    // "1y" so retorna 200 pros tickers de demonstracao da BRAPI (ex.: PETR4, MGLU3) sem
    // token; com uma chave real (plano Free), a API rejeita range > 3mo com 400 INVALID_RANGE
    // pra qualquer outro ticker. "3mo" e o maior intervalo aceito no plano Free.
    @Value("${brapi.historico.range:3mo}")
    private String rangeHistorico;

    public ServicoAtivo(
            ClienteBrApi clienteBrApi,
            PortaFilaMensagens filaMensagens
    ) {
        this.clienteBrApi = clienteBrApi;
        this.filaMensagens = filaMensagens;
    }

    /**
     * Consulta o ativo na BRAPI e publica o payload bruto na fila.
     */
    public Ativo processar(String codigoAtivo) {
        Ativo ativo = consultarAtivoAPI(codigoAtivo);
        publicarAtivoNaFila(ativo);
        return ativo;
    }

    /**
     * Processamento mais robusto do ativo, trazendo tambem a serie historica.
     */
    public Ativo processarRobusto(String codigoAtivo) {
        Ativo ativo = consultarAtivoAPI(codigoAtivo);
        RespostaHistoricoAcoesDTO historico = consultarSerieAtivosAPI(new ConsultaHistoricoAcoesDTO(codigoAtivo, rangeHistorico, "1d", null, null, "asc"));
        publicarAtivoNaFila(ativo);
        publicarSerieHistoricaNaFila(historico);
        return ativo;
    }

    /**
     * Consulta o ativo na BRAPI e publica os dados brutos na fila.
     */
    public Ativo buscarEProcessarAtivo(String codigoAtivo) {
        Ativo ativo = consultarAtivoAPI(codigoAtivo);
        publicarAtivoNaFila(ativo);
        return ativo;
    }

    private Ativo consultarAtivoAPI(String codigoAtivo) {
        var retorno = clienteBrApi.consultarCotacao(codigoAtivo);
        if (Objects.isNull(retorno) || retorno.getResults().isEmpty()) {
            log.error("{} - Nenhum dado retornado para ativo: {}", SERVICO, codigoAtivo);
            throw new ExcecaoAtivoNaoEncontrado(codigoAtivo);
        }

        AtivoBrapiDTO brapiDto = retorno.getResults().getFirst();
        return mapper.map(brapiDto, Ativo.class);
    }


    private RespostaHistoricoAcoesDTO consultarSerieAtivosAPI(ConsultaHistoricoAcoesDTO consultaHistorico) {

        var retorno = clienteBrApi.consultarHistorico(consultaHistorico);


        if (Objects.isNull(retorno)) {
            log.error("{} - Nenhum dado historico retornado", SERVICO);
            throw new RuntimeException("Nenhum dado historico retornado");
        }

        return retorno ;
    }

    /**
     * Consulta o perfil da empresa (setor, industria, resumo do negocio) na BRAPI,
     * pra anexar as informacoes de insights ja geradas. E um enriquecimento, nao um
     * dado essencial: qualquer falha (BRAPI fora, ticker sem perfil, etc.) degrada
     * pra Optional vazio em vez de quebrar a resposta que a chama.
     */
    public Optional<PerfilEmpresaBrapiDTO> buscarPerfilEmpresa(String codigoAtivo) {
        try {
            var resposta = clienteBrApi.consultarPerfilEmpresa(codigoAtivo);
            if (Objects.isNull(resposta) || resposta.getResults() == null || resposta.getResults().isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(resposta.getResults().getFirst().getData());
        } catch (Exception e) {
            log.warn("{} - Falha ao buscar perfil da empresa para {}: {}", SERVICO, codigoAtivo, e.getMessage());
            return Optional.empty();
        }
    }

    private void publicarAtivoNaFila(Ativo ativo) {
        String payload = ConversorJson.paraJson(ativo);
        log.info("{} - Payload JSON gerado com {} bytes", SERVICO, payload.length());
        filaMensagens.enviarMensagemParaFila(payload);
    }

    private void publicarSerieHistoricaNaFila(RespostaHistoricoAcoesDTO historico) {
        String payload = ConversorJson.paraJson(historico);
        log.info("{} - Payload JSON de serie historica gerado com {} bytes", SERVICO, payload.length());
        filaMensagens.enviarMensagemParaFila(payload, filaSeriesHistoricasUrl);
    }
}
