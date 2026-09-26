package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.CandleDiarioEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.ConsultaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBrApi;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCandleDiario;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoCache;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

@Slf4j
@RestController
@RequestMapping("/api/v2/stocks/historical")
@RequiredArgsConstructor
public class HistoricoAcoesController {

    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");

    private final ClienteBrApi clienteBrApi;
    private final RepositorioCandleDiario repositorioCandleDiario;
    private final ServicoAtualizacaoCache servicoAtualizacaoCache;

    /**
     * Retorna series historicas OHLCV pra um ou mais ativos B3, lendo do cache
     * (candle_diario, mantido pelo AgendadorCacheAtivos). Um simbolo que nunca
     * foi visto cai pra uma chamada ao vivo pontual, que tambem aquece o
     * cache - a proxima leitura do mesmo simbolo ja vem do banco.
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

        List<String> simbolos = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        String rangeEfetivo = StringUtils.hasText(range) ? range : "1mo";
        LocalDate desde = calcularDesde(rangeEfetivo);
        List<RespostaHistoricoAcoesDTO.ResultadoHistoricoAcaoDTO> resultados = new ArrayList<>();

        for (String simbolo : simbolos) {
            List<CandleDiarioEntity> candles = repositorioCandleDiario.findBySimboloOrderByDataAsc(simbolo);

            if (candles.isEmpty()) {
                log.info("{}-Simbolo {} sem historico em cache; buscando ao vivo e aquecendo o cache", CONTROLADOR, simbolo);
                RespostaHistoricoAcoesDTO respostaAoVivo = clienteBrApi.consultarHistorico(
                        new ConsultaHistoricoAcoesDTO(simbolo, rangeEfetivo, interval, startDate, endDate, sortOrder));
                if (respostaAoVivo != null && respostaAoVivo.results() != null) {
                    resultados.addAll(respostaAoVivo.results());
                    servicoAtualizacaoCache.persistirCandlesDoResultado(respostaAoVivo);
                }
                continue;
            }

            List<CandleDiarioEntity> noRange = candles.stream()
                    .filter(c -> !c.getData().isBefore(desde))
                    .toList();
            resultados.add(montarResultado(simbolo, rangeEfetivo, interval, noRange));
        }

        return ResponseEntity.ok(new RespostaHistoricoAcoesDTO(resultados, null, null));
    }

    private RespostaHistoricoAcoesDTO.ResultadoHistoricoAcaoDTO montarResultado(
            String simbolo, String range, String interval, List<CandleDiarioEntity> candles
    ) {
        List<RespostaHistoricoAcoesDTO.PrecoHistoricoAcaoDTO> precos = candles.stream()
                .map(c -> new RespostaHistoricoAcoesDTO.PrecoHistoricoAcaoDTO(
                        c.getData().atStartOfDay(ZONA_BRASIL).toEpochSecond(),
                        c.getOpen(),
                        c.getHigh(),
                        c.getLow(),
                        c.getClose(),
                        c.getVolume(),
                        c.getAdjustedClose()
                ))
                .toList();

        var serie = new RespostaHistoricoAcoesDTO.SerieHistoricaAcaoDTO(
                StringUtils.hasText(interval) ? interval : "1d",
                range,
                precos
        );
        return new RespostaHistoricoAcoesDTO.ResultadoHistoricoAcaoDTO(simbolo, simbolo, false, serie);
    }

    /**
     * Aproxima o range pedido (5d/1mo/3mo) em dias corridos pra filtrar o que
     * ja esta em candle_diario - nao precisa ser exato: e so pra nao devolver
     * 10 anos de candle quando o pedido foi "5 dias".
     */
    private static LocalDate calcularDesde(String range) {
        int diasCorridos = switch (range) {
            case "5d" -> 7;
            case "1mo" -> 31;
            case "3mo" -> 92;
            case "1d" -> 1;
            default -> 31;
        };
        return LocalDate.now(ZONA_BRASIL).minusDays(diasCorridos);
    }
}
