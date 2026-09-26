package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.Ativo;
import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoMonitoradoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCotacaoAtual;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivo;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivoMonitorado;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

@Slf4j
@RestController
@RequestMapping("/ativos")
public class AtivoController {

    private final ServicoAtivo servicoAtivo;
    private final ServicoAtivoMonitorado servicoAtivoMonitorado;
    private final RepositorioCotacaoAtual repositorioCotacaoAtual;
    private final ServicoAtualizacaoCache servicoAtualizacaoCache;

    public AtivoController(
            ServicoAtivo servicoAtivo,
            ServicoAtivoMonitorado servicoAtivoMonitorado,
            RepositorioCotacaoAtual repositorioCotacaoAtual,
            ServicoAtualizacaoCache servicoAtualizacaoCache
    ) {
        this.servicoAtivo = servicoAtivo;
        this.servicoAtivoMonitorado = servicoAtivoMonitorado;
        this.repositorioCotacaoAtual = repositorioCotacaoAtual;
        this.servicoAtualizacaoCache = servicoAtualizacaoCache;
    }

    /**
     * Le a cotacao do cache (cotacao_atual, mantido pelo AgendadorCacheAtivos).
     * So chama a BRAPI ao vivo se o simbolo nunca foi visto (busca livre de um
     * ticker fora da carteira monitorada) - e nesse caso aquece o cache pra
     * proxima leitura nao precisar bater na BRAPI de novo.
     */
    @GetMapping("/{ativo}")
    public ResponseEntity<Ativo> buscarPorSimbolo(@PathVariable String ativo) {
        log.info("{}-Requisicao recebida para buscar ativo: {}", CONTROLADOR, ativo);
        return ResponseEntity.ok(buscarComFallback(ativo));
    }

    /**
     * Mesmo contrato de resposta do endpoint acima - o "robusto" (tambem buscar
     * historico) so importava pro efeito colateral de publicar na fila pro
     * gerar-insights; pra leitura, os dois sempre foram o mesmo Ativo.
     */
    @GetMapping("/robusto/{ativo}")
    public ResponseEntity<Ativo> buscarPorSimboloComSerieHistorica(@PathVariable String ativo) {
        log.info("{}-Requisicao robusta recebida para buscar ativo: {}", CONTROLADOR, ativo);
        return ResponseEntity.ok(buscarComFallback(ativo));
    }

    private Ativo buscarComFallback(String simbolo) {
        return repositorioCotacaoAtual.findBySimbolo(simbolo)
                .map(Ativo::de)
                .orElseGet(() -> {
                    log.info("{}-Simbolo {} nao esta em cache; buscando ao vivo e aquecendo o cache", CONTROLADOR, simbolo);
                    Ativo ativoAoVivo = servicoAtivo.processarRobusto(simbolo);
                    servicoAtualizacaoCache.persistirCotacaoDoFallback(ativoAoVivo);
                    return ativoAoVivo;
                });
    }

    /**
     * Registra um ativo para monitoramento recorrente (cotacao + serie historica a cada 30s,
     * via AgendadorCacheAtivos) e ja dispara a primeira coleta robusta agora, sem esperar o proximo ciclo.
     */
    @PostMapping("/registrar/{ativo}")
    public ResponseEntity<Void> registrarAtivo(@PathVariable String ativo) {
        servicoAtivoMonitorado.registrar(ativo);
        try {
            servicoAtivo.processarRobusto(ativo);
        } catch (Exception e) {
            // O registro em si (a parte que importa: o ativo entrar em monitoramento)
            // ja foi persistido acima. Essa primeira coleta e so uma tentativa de
            // adiantar o resultado - se falhar (BRAPI fora do ar, chave invalida etc.),
            // o AgendadorAtivos tenta de novo sozinho no proximo ciclo. Nao faz sentido
            // devolver erro pro cliente por causa disso.
            log.warn("{}-Falha na coleta imediata ao registrar {}; o agendador tentara novamente: {}",
                    CONTROLADOR, ativo, e.getMessage());
        }
        log.info("{}-Ativo registrado para monitoramento recorrente: {}", CONTROLADOR, ativo);
        return ResponseEntity.accepted().build();
    }

    /**
     * Lista os ativos cadastrados para monitoramento recorrente.
     */
    @GetMapping("/registrados")
    public ResponseEntity<List<AtivoMonitoradoDTO>> listarRegistrados() {
        List<AtivoMonitoradoDTO> registrados = servicoAtivoMonitorado.listar().stream()
                .map(AtivoMonitoradoDTO::de)
                .toList();
        return ResponseEntity.ok(registrados);
    }
}
