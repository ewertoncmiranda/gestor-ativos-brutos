package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.entrypoint.schedule.AgendadorAtivos;
import br.com.miranda.gestor.ativos.brutos.external.Ativo;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

@Slf4j
@RestController
@RequestMapping("/ativos")
public class ControladorAtivo {

    private final ServicoAtivo servicoAtivo;
    private final AgendadorAtivos agendadorAtivos;

    public ControladorAtivo(ServicoAtivo servicoAtivo, AgendadorAtivos agendadorAtivos) {
        this.servicoAtivo = servicoAtivo;
        this.agendadorAtivos = agendadorAtivos;
    }

    /**
     * Consulta um ativo na BRAPI, publica o payload bruto na fila SQS e devolve os dados recebidos.
     */
    @GetMapping("/{ativo}")
    public ResponseEntity<Ativo> buscarPorSimbolo(@PathVariable String ativo) {
        log.info("{}-Requisicao recebida para buscar ativo: {}", CONTROLADOR, ativo);
        Ativo ativoProcessado = servicoAtivo.buscarEProcessarAtivo(ativo);
        log.info("{}-Resposta preparada para ativo: {}", CONTROLADOR, ativo);
        return ResponseEntity.ok(ativoProcessado);
    }

    /**
     * Registra um ativo para processamento assíncrono pelo agendador interno.
     */
    @PostMapping("/registrar/{ativo}")
    public ResponseEntity<Void> registrarAtivo(@PathVariable String ativo) {
        agendadorAtivos.registrarAtivo(ativo);
        log.info("{}-Ativo registrado para processamento assincrono: {}", CONTROLADOR, ativo);
        return ResponseEntity.accepted().build();
    }
}
