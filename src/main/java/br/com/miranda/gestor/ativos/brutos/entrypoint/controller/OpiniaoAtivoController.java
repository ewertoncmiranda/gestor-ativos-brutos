package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.OpiniaoAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoOpiniaoIa;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opiniao por horizonte do ativo (TASK-OPI-1): curto (21 pregoes), medio (63)
 * e longo (126), gerada pelo gerar-insights (modelo local ou regra). Classe
 * propria no prefixo {@code /ativos}, como {@link LacunasAtivoController}.
 * GET sem efeito colateral: nao grava nem publica.
 */
@RestController
@RequestMapping("/ativos")
@RequiredArgsConstructor
public class OpiniaoAtivoController {

    private final ServicoOpiniaoIa servico;

    @GetMapping("/{simbolo}/opiniao")
    public OpiniaoAtivoDTO opiniao(@PathVariable String simbolo) {
        return servico.opiniao(simbolo);
    }
}
