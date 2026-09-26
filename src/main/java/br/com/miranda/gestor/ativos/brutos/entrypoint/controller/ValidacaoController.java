package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.DiarioDeSinaisDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoDiarioDeSinais;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

/**
 * Evidencia de acerto das regras. Hoje: o diario de sinais (paper trading)
 * gravado pelo gerar-insights. O backtest entra aqui quando existir.
 */
@Slf4j
@RestController
@RequestMapping("/validacao")
@RequiredArgsConstructor
public class ValidacaoController {

    private final ServicoDiarioDeSinais servicoDiarioDeSinais;

    /**
     * Placar por recomendacao e horizonte, com a taxa-base ao lado, e a linha
     * do tempo dos sinais mais recentes. {@code simbolo} filtra; {@code limite}
     * (1 a 1000, padrao 120) limita a linha do tempo, nao o placar.
     */
    @GetMapping("/diario")
    public ResponseEntity<DiarioDeSinaisDTO> diario(
            @RequestParam(required = false) String simbolo,
            @RequestParam(required = false) Integer limite) {
        log.info("{}-Diario de sinais | simbolo={} limite={}", CONTROLADOR, simbolo, limite);
        return ResponseEntity.ok(servicoDiarioDeSinais.montar(simbolo, limite));
    }
}
