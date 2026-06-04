package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.entrypoint.FilaIndisponivelException;
import br.com.miranda.gestor.ativos.brutos.external.Ativo;
import br.com.miranda.gestor.ativos.brutos.external.dto.BrapiAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.port.QueueConnectPort;
import br.com.miranda.gestor.ativos.brutos.tools.Utils;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.SERVICE;

@Slf4j
@Service
public class AtivoService {

    private final ConsultaBrApiService consultaBrApiService;

    private final QueueConnectPort queueConnectPort;

    private final ModelMapper mapper = new ModelMapper();


    public AtivoService(
            ConsultaBrApiService consultaBrApiService,
            QueueConnectPort queueConnectPort
    ) {
        this.consultaBrApiService = consultaBrApiService;
        this.queueConnectPort = queueConnectPort;
    }


    public Ativo processar(String codAtivo) {
        log.info("{} - Iniciando processamento do ativo: {}", SERVICE, codAtivo);
        var retorno = consultaBrApiService.executar(codAtivo);
        if (Objects.isNull(retorno) || retorno.getResults().isEmpty()) {
            log.error("{} - Nenhum dado retornado para ativo: {}", SERVICE, codAtivo);
            return null;
        }

        log.debug("{} - Total de resultados recebidos: {}", SERVICE, retorno.getResults().size());
        BrapiAtivoDTO brapiDto = retorno.getResults().getFirst();

        log.debug("{} - Resultado selecionado: symbol={}, name={}", SERVICE, brapiDto.getSymbol(), brapiDto.getLongName());
        Ativo ativo = mapper.map(brapiDto, Ativo.class);

        log.debug("{} - Ativo convertido para domínio: {}", SERVICE, ativo.getSymbol());
        String payload = Utils.toJson(ativo);

        log.info("{} - Payload JSON gerado com {} bytes", SERVICE, payload.length());

        try {
            queueConnectPort.enviarMensagemParaFila(payload);
        }catch (FilaIndisponivelException e) {
            log.error("{} - Falha ao enviar mensagem para fila,fluxo indisponivel {}", SERVICE, e.getMessage(), e);
        }

        return ativo;
    }
}