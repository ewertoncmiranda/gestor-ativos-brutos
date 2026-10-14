package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.IdentidadeAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoIdentidadeAtivo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ativos")
@RequiredArgsConstructor
public class IdentidadeAtivoController {
    private final ServicoIdentidadeAtivo servico;

    @GetMapping("/{simbolo}/identidade")
    public IdentidadeAtivoDTO buscar(@PathVariable String simbolo) {
        return servico.buscar(simbolo);
    }
}
