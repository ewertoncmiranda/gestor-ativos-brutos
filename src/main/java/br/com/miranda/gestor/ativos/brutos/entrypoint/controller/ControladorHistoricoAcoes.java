package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.ConsultaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBrApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

@Slf4j
@RestController
@RequestMapping("/api/v2/stocks/historical")
@RequiredArgsConstructor
public class ControladorHistoricoAcoes {

    private final ClienteBrApi clienteBrApi;

    /**
     * Retorna séries históricas OHLCV para um ou mais ativos B3.
     */
    @GetMapping
    public ResponseEntity<RespostaHistoricoAcoesDTO> buscarHistorico(
            @RequestParam String symbols,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sortOrder
    ) {
        if (!StringUtils.hasText(symbols)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O parametro symbols e obrigatorio");
        }

        ConsultaHistoricoAcoesDTO filtros = new ConsultaHistoricoAcoesDTO(
                symbols,
                range,
                interval,
                startDate,
                endDate,
                sortOrder
        );

        log.info("{}-Requisicao recebida para historico OHLCV. Filtros: {}", CONTROLADOR, filtros);
        return ResponseEntity.ok(clienteBrApi.consultarHistorico(filtros));
    }
}
