package br.com.miranda.gestor.ativos.brutos.entrypoint.schedule;

import br.com.miranda.gestor.ativos.brutos.service.AtivoService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.SCHEDULER;

@Slf4j
@Component
@EnableScheduling
@AllArgsConstructor
public class ScheduleJob {

    private final AtivoService servicePort;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    public void registerAtivo(String codigoAtivo) {
        if (codigoAtivo == null || codigoAtivo.isBlank()) {
            return;
        }
        String ativoNormalizado = codigoAtivo.trim().toUpperCase();
        queue.add(ativoNormalizado);
        log.debug("{} - Ativo registrado na fila: {}", SCHEDULER, ativoNormalizado);
    }


    @Scheduled(fixedDelay = 9000)
    public void processarAcoes() {
        log.info("{} - Iniciando processamento em lote", SCHEDULER);
        List<String> acoes = new ArrayList<>();
        String codigoAcao;

        while ((codigoAcao = queue.poll()) != null) {
            acoes.add(codigoAcao);
        }

        if (acoes.isEmpty()) {
            log.debug("{} - Nenhuma ação encontrada na fila", SCHEDULER);
            return;
        }
        log.info("{} - Total de ações para processar: {}", SCHEDULER, acoes.size());
        for (String ativo : acoes) {
            try {
                log.info("{} - Processando ativo: {}", SCHEDULER, ativo);
                servicePort.processar(ativo);
                log.info("{} - Ativo processado com sucesso: {}", SCHEDULER, ativo);
            } catch (Exception e) {
                log.error("{} - Erro ao processar ativo: {}", SCHEDULER, ativo, e);
            }
        }

        log.info("{} - Processamento concluído", SCHEDULER);
    }
}