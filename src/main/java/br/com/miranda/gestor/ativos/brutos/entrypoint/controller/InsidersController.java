package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.InsidersDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RecomprasDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoInsiders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ativos")
@RequiredArgsConstructor
public class InsidersController {
    private final ServicoInsiders servico;

    @GetMapping("/{simbolo}/insiders")
    public InsidersDTO insiders(@PathVariable String simbolo) {
        return servico.insiders(simbolo);
    }

    @GetMapping("/{simbolo}/recompras")
    public RecomprasDTO recompras(@PathVariable String simbolo) {
        return servico.recompras(simbolo);
    }
}
