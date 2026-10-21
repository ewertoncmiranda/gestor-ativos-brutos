package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.DatasExDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoDatasEx;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Calendario de datas ex do ativo (MICRO-GES-2). */
@RestController
@RequestMapping("/ativos")
@RequiredArgsConstructor
public class DatasExController {

    private final ServicoDatasEx servicoDatasEx;

    @GetMapping("/{simbolo}/datas-ex")
    public DatasExDTO datasEx(@PathVariable String simbolo,
                              @RequestParam(required = false) String desde) {
        LocalDate inicio = null;
        if (desde != null && !desde.isBlank()) {
            try {
                inicio = LocalDate.parse(desde.trim());
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "desde deve ser uma data ISO (AAAA-MM-DD)");
            }
        }
        return servicoDatasEx.calendario(simbolo, inicio);
    }
}
