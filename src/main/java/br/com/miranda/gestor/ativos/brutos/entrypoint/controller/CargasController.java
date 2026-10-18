package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.CargaDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoCargas;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Cargas do ETL (Plano CARGA): GETs leem o que o ETL gravou, sem efeito
 * colateral (CARGA-GES-1); POST repassa o pedido ao servico do ETL e devolve
 * o status dele - 202 aceita, 409 ja ha carga rodando (CARGA-GES-2).
 */
@RestController
@RequestMapping("/cargas")
public class CargasController {

    private final ServicoCargas servico;

    public CargasController(ServicoCargas servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<CargaDTO.Execucao> listar(
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde) {
        return servico.listar(limite, status, desde);
    }

    @GetMapping("/ultimas")
    public List<CargaDTO.Ultima> ultimas() {
        return servico.ultimas();
    }

    @GetMapping("/{id}")
    public CargaDTO.Execucao buscar(@PathVariable String id) {
        return servico.buscar(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> disparar(@RequestBody(required = false) CargaDTO.Disparo pedido) {
        ServicoCargas.RespostaDisparo resposta = servico.disparar(pedido);
        return ResponseEntity.status(resposta.status()).body(resposta.corpo());
    }
}
