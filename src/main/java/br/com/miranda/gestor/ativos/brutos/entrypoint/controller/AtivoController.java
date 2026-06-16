package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.entrypoint.schedule.ScheduleJob;
import br.com.miranda.gestor.ativos.brutos.external.Ativo;
import br.com.miranda.gestor.ativos.brutos.service.AtivoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.CONTROLLER;

@Slf4j
@RestController
@RequestMapping("/ativos")
public class AtivoController {

    private final AtivoService service;
    private final ScheduleJob scheduleJob;

    public AtivoController(AtivoService service, ScheduleJob scheduleJob) {
        this.service = service;
        this.scheduleJob = scheduleJob;
    }

    @GetMapping("/{ativo}")
    public ResponseEntity<Ativo> buscarPorSymbol(@PathVariable String ativo) {
        log.info("{}-Requisicao recebida para buscar ativo: {}", CONTROLLER, ativo);
        var ativoProcessado = service.processar(ativo);
        log.info("{}-Resposta preparada para ativo: {}", CONTROLLER, ativo);
        return ResponseEntity.ok(ativoProcessado);
    }

    @PostMapping("/registrar/{ativo}")
    public ResponseEntity<Void> registrarAtivo(@PathVariable String ativo) {
        scheduleJob.registerAtivo(ativo);
        log.info("{}-Ativo registrado para processamento assincrono: {}", CONTROLLER, ativo);
        return ResponseEntity.accepted().build();
    }
}
