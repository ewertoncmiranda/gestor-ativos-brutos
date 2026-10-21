package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.PremioRiscoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoPremioRisco;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ativos")
@RequiredArgsConstructor
public class PremioRiscoController {

    private final ServicoPremioRisco servico;

    @GetMapping("/{simbolo}/premio-risco")
    public PremioRiscoDTO premioRisco(@PathVariable String simbolo) {
        return servico.premioRisco(simbolo);
    }
}
