package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.ConfiabilidadeDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoConfiabilidade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Selos de confiabilidade do ativo (FONTES-GES-4). */
@RestController
@RequestMapping("/ativos")
@RequiredArgsConstructor
public class ConfiabilidadeController {

    private final ServicoConfiabilidade servico;

    @GetMapping("/{simbolo}/confiabilidade")
    public ConfiabilidadeDTO confiabilidade(@PathVariable String simbolo) {
        return servico.confiabilidade(simbolo);
    }
}
