package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoSimuladorAtivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

@Slf4j
@RestController
@RequestMapping("/simulador/ativos")
@RequiredArgsConstructor
public class SimuladorController {

    private final ServicoSimuladorAtivo servicoSimuladorAtivo;

    /**
     * GET /simulador/ativos - Lista ativos do simulador que estão ativos.
     * Sem efeito colateral (GET puro).
     */
    @GetMapping
    public ResponseEntity<List<SimuladorAtivoDTO>> listarAtivos() {
        log.info("{} - Requisição recebida para listar ativos do simulador", CONTROLADOR);
        return ResponseEntity.ok(servicoSimuladorAtivo.listarAtivos());
    }

    /**
     * POST /simulador/ativos?simbolo=PETR4 - Registra ou reativa um ativo no simulador.
     * Valida symbol no cadastro de ativos (cvm_ticker/ativo_identidade).
     * Retorna 201 se novo, 200 se reativado.
     */
    @PostMapping
    public ResponseEntity<SimuladorAtivoDTO> registrarAtivo(@RequestParam String simbolo) {
        log.info("{} - Requisição recebida para registrar ativo no simulador: {}", CONTROLADOR, simbolo);
        try {
            SimuladorAtivoDTO ativo = servicoSimuladorAtivo.registrar(simbolo);
            return ResponseEntity.status(HttpStatus.CREATED).body(ativo);
        } catch (IllegalArgumentException e) {
            log.warn("{} - Símbolo inválido: {}", CONTROLADOR, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DELETE /simulador/ativos?simbolo=PETR4 - Desativa um ativo no simulador (soft delete).
     * Marca ativo=false, nunca apaga a linha.
     */
    @DeleteMapping
    public ResponseEntity<Void> desativarAtivo(@RequestParam String simbolo) {
        log.info("{} - Requisição recebida para desativar ativo no simulador: {}", CONTROLADOR, simbolo);
        servicoSimuladorAtivo.desativar(simbolo);
        return ResponseEntity.noContent().build();
    }
}

