package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.PanoramaMercadoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoPanoramaMercado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panorama do mercado para o carrossel da Inicio (Plano MERC, MERC-GES-1).
 * Somente leitura. janela = 1d, 5d (padrao), 21d ou ano; fora disso, 400.
 * Bloco sem dado vem vazio e listado em indisponivel, com 200.
 */
@RestController
@RequestMapping("/mercado")
public class MercadoController {

    private final ServicoPanoramaMercado servico;

    public MercadoController(ServicoPanoramaMercado servico) {
        this.servico = servico;
    }

    @GetMapping("/panorama")
    public PanoramaMercadoDTO.Panorama panorama(@RequestParam(required = false) String janela) {
        return servico.panorama(janela);
    }
}
