package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.ProventosResumoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoProventosResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ativos")
public class ProventosResumoController {
    private final ServicoProventosResumo servico;

    @GetMapping("/{simbolo}/proventos-resumo")
    public ProventosResumoDTO resumo(@PathVariable String simbolo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        LocalDate fim = ate == null ? LocalDate.now() : ate;
        LocalDate inicio = de == null ? fim.minusMonths(6) : de;
        if (inicio.isAfter(fim) || fim.isAfter(LocalDate.now()) || inicio.isBefore(ServicoProventosResumo.INICIO)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Período deve estar entre 2016-01-01 e hoje, com de anterior ou igual a ate.");
        }
        return servico.resumo(simbolo, inicio, fim);
    }
}
