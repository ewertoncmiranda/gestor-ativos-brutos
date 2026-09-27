package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoPregoes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

/**
 * Velas de qualquer periodo desde 2016 (contrato infra#CTR-15), lidas do
 * banco - nenhuma chamada a BRAPI, entao nao gasta cota.
 */
@Slf4j
@RestController
@RequestMapping("/ativos")
@RequiredArgsConstructor
public class PregoesController {

    private final ServicoPregoes servicoPregoes;

    /**
     * {@code de}/{@code ate} em AAAA-MM-DD (padrao: ultimo ano ate hoje);
     * {@code intervalo} dia, semana ou mes. Codigo antigo (ELET3) responde com
     * o canonico (AXIA3) e a serie emendada.
     */
    @GetMapping("/{ativo}/pregoes")
    public ResponseEntity<PregoesDTO> pregoes(
            @PathVariable String ativo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false, defaultValue = "dia") String intervalo) {
        log.info("{}-Pregoes | ativo={} de={} ate={} intervalo={}", CONTROLADOR, ativo, de, ate, intervalo);
        return ResponseEntity.ok(servicoPregoes.montar(ativo, de, ate, intervalo));
    }
}
