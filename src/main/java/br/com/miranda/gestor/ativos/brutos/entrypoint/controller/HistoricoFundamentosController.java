package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.HistoricoFundamentosDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoHistoricoFundamentos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/analises")
@RequiredArgsConstructor
public class HistoricoFundamentosController {
    private final ServicoHistoricoFundamentos servico;

    @GetMapping("/{simbolo}/fundamentos-cvm/historico")
    public HistoricoFundamentosDTO buscar(@PathVariable String simbolo,
            @RequestParam(defaultValue = "5") int anos) {
        if (anos < 1 || anos > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "anos deve ficar entre 1 e 10");
        return servico.buscar(simbolo, anos);
    }
}
