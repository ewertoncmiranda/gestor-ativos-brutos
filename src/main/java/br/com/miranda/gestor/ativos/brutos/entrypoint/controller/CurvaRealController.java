package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.CurvaRealDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoPremioRisco;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Curva real (NTN-B); a rota literal tem precedencia sobre /indices-macro/{codigo}. */
@RestController
@RequestMapping("/indices-macro/curva-real")
@RequiredArgsConstructor
public class CurvaRealController {

    private final ServicoPremioRisco servico;

    @GetMapping
    public CurvaRealDTO curvaReal() {
        return servico.curvaReal();
    }
}
