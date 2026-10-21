package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.BancoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoBanco;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Indicadores de banco (FONTES-GES-2). */
@RestController
public class BancoController {

    private final ServicoBanco servico;

    public BancoController(ServicoBanco servico) {
        this.servico = servico;
    }

    @GetMapping("/ativos/{simbolo}/banco")
    public BancoDTO banco(@PathVariable String simbolo) {
        return servico.buscar(simbolo);
    }
}
