package br.com.miranda.gestor.ativos.brutos.entrypoint.schedule;

import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.AGENDADOR;

@Slf4j
@Component
@EnableScheduling
@AllArgsConstructor
public class AgendadorAtivos {

    private final ServicoAtivo servicoAtivo;
    private final ConcurrentLinkedQueue<String> filaAtivos = new ConcurrentLinkedQueue<>();

    /**
     * Normaliza e adiciona o ativo na fila em memória para processamento posterior.
     */
    public void registrarAtivo(String codigoAtivo) {
        if (codigoAtivo == null || codigoAtivo.isBlank()) {
            return;
        }
        String ativoNormalizado = codigoAtivo.trim().toUpperCase();
        filaAtivos.add(ativoNormalizado);
        log.debug("{} - Ativo registrado na fila: {}", AGENDADOR, ativoNormalizado);
    }

    /**
     * Processa periodicamente os ativos registrados.
     */
    @Scheduled(fixedDelay = 3000)
    public void processarAtivos() {
        log.info("{} - Iniciando processamento em lote", AGENDADOR);
        List<String> ativos = new ArrayList<>();
        String codigoAtivo;

        while ((codigoAtivo = filaAtivos.poll()) != null) {
            ativos.add(codigoAtivo);
        }

        if (ativos.isEmpty()) {
            log.debug("{} - Nenhuma acao encontrada na fila", AGENDADOR);
            return;
        }

        log.info("{} - Total de acoes para processar: {}", AGENDADOR, ativos.size());
        for (String ativo : ativos) {
            try {
                log.info("{} - Processando ativo: {}", AGENDADOR, ativo);
                servicoAtivo.processar(ativo);
                log.info("{} - Ativo processado com sucesso: {}", AGENDADOR, ativo);
            } catch (Exception e) {
                log.error("{} - Erro ao processar ativo: {}", AGENDADOR, ativo, e);
            }
        }

        log.info("{} - Processamento concluido", AGENDADOR);
    }
}
