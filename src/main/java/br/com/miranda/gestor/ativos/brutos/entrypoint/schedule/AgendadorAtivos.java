package br.com.miranda.gestor.ativos.brutos.entrypoint.schedule;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivo;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivoMonitorado;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.AGENDADOR;

/**
 * Verifica periodicamente os ativos monitorados (tabela ativo_monitorado) e
 * reprocessa cada um quando seu intervaloSegundos ja passou desde o ultimo
 * processamento. O tick roda a cada 5s para dar precisao real ao intervalo
 * por ativo (ex.: 30s), sem exigir um agendador dedicado por linha.
 */
@Slf4j
@Component
@EnableScheduling
@AllArgsConstructor
public class AgendadorAtivos {

    private final ServicoAtivo servicoAtivo;
    private final ServicoAtivoMonitorado servicoAtivoMonitorado;

    @Scheduled(fixedDelay = 5000)
    public void processarAtivosMonitorados() {
        List<AtivoMonitoradoEntity> ativosMonitorados = servicoAtivoMonitorado.listarAtivosParaMonitorar();

        if (ativosMonitorados.isEmpty()) {
            log.debug("{} - Nenhum ativo monitorado", AGENDADOR);
            return;
        }

        for (AtivoMonitoradoEntity entidade : ativosMonitorados) {
            if (!estaDevido(entidade)) {
                continue;
            }

            try {
                log.info("{} - Processando ativo monitorado: {} ({})", AGENDADOR, entidade.getSimbolo(), entidade.getTipoColeta());
                if (entidade.getTipoColeta() == TipoColeta.COTACAO_E_HISTORICO) {
                    servicoAtivo.processarRobusto(entidade.getSimbolo());
                } else {
                    servicoAtivo.processar(entidade.getSimbolo());
                }
                servicoAtivoMonitorado.marcarProcessado(entidade);
                log.info("{} - Ativo monitorado processado com sucesso: {}", AGENDADOR, entidade.getSimbolo());
            } catch (Exception e) {
                log.error("{} - Erro ao processar ativo monitorado: {}", AGENDADOR, entidade.getSimbolo(), e);
            }
        }
    }

    private boolean estaDevido(AtivoMonitoradoEntity entidade) {
        if (entidade.getAtualizadoEm() == null) {
            return true;
        }
        LocalDateTime proximoProcessamento = entidade.getAtualizadoEm().plusSeconds(entidade.getIntervaloSegundos());
        return LocalDateTime.now().isAfter(proximoProcessamento);
    }
}
