package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoOperacionalV2;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * APIs de leitura da Operabilidade V2 para cards enriquecidos e cockpit por ticker. GET-only: o produtor dos
 * dados segue sendo infra/gerar-insights.
 */
@RestController
@RequestMapping("/operacional")
@RequiredArgsConstructor
public class OperacionalV2Controller {

    private final ServicoOperacionalV2 servico;

    @GetMapping("/tickers")
    public OperacionalV2DTO.Tickers tickers(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String liquidez,
                                            @RequestParam(required = false) String setor,
                                            @RequestParam(required = false) String sinal,
                                            @RequestParam(required = false) String confianca,
                                            @RequestParam(required = false) String posicao,
                                            @RequestParam(required = false) String evento,
                                            @RequestParam(required = false) Integer pagina,
                                            @RequestParam(required = false) Integer tamanho) {
        return servico.tickers(status, liquidez, setor, sinal, confianca, posicao, evento, pagina, tamanho);
    }

    @GetMapping("/tickers/{simbolo}")
    public OperacionalV2DTO.TickerDetalhe ticker(@PathVariable String simbolo) {
        return servico.ticker(simbolo);
    }

    @GetMapping("/tickers/{simbolo}/consenso")
    public OperacionalV2DTO.Consenso consenso(@PathVariable String simbolo) {
        return servico.consenso(simbolo);
    }

    @GetMapping("/tickers/{simbolo}/tese")
    public OperacionalV2DTO.Tese tese(@PathVariable String simbolo) {
        return servico.tese(simbolo);
    }

    @GetMapping("/tickers/{simbolo}/timeline")
    public OperacionalV2DTO.Timeline timeline(@PathVariable String simbolo,
                                              @RequestParam(required = false) String tipo,
                                              @RequestParam(required = false) String severidade,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                              @RequestParam(required = false) Integer limite) {
        return servico.timeline(simbolo, tipo, severidade, inicio, fim, limite);
    }

    @GetMapping("/filtros")
    public OperacionalV2DTO.Filtros filtros() {
        return servico.filtros();
    }

    @GetMapping("/alertas")
    public OperacionalV2DTO.Alertas alertas(@RequestParam(required = false) Integer limite) {
        return servico.alertas(limite);
    }
}
