package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorCenarioDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorResultado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

/**
 * SIM-GES-7: Cenário pontual (what-if) - P&L real para uma data e janela.
 * Retorna resultado por estratégia, CDI do período e ganho/perda em R$.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoSimuladorCenario {

    private final RepositorioSimuladorResultado repositorioResultado;
    // TODO: Adicionar repositório de COTAHIST para preços
    // TODO: Adicionar repositório de índices para CDI

    /**
     * SIM-GES-7: Simula P&L de todas as 7 estratégias entre data_entrada e janela.
     * @param simbolo Ticker
     * @param dataEntrada Data de entrada
     * @param janelaPregoesApos Dias úteis de espera (21/63/126)
     * @param capitalInicial Capital investido
     * @return DTO com P&L por estratégia, CDI e ganho/perda absoluto
     */
    public SimuladorCenarioDTO simular(String simbolo, LocalDate dataEntrada,
                                       Integer janelaPregoesApos, BigDecimal capitalInicial) {
        log.info("{} - Simulando cenário: {} de {} por {} pregões", SERVICO, simbolo, dataEntrada, janelaPregoesApos);

        // TODO: Implementar:
        // 1. Buscar preço em dataEntrada
        // 2. Avançar janelaPregoesApos dias úteis → dataSaida
        // 3. Buscar preço em dataSaida
        // 4. Calcular P&L % e R$ para cada estratégia
        // 5. Buscar CDI do período (via RepositorioIndiceMacro ou similar)

        Map<String, BigDecimal> retornoPorEstrategia = new HashMap<>();
        Map<String, BigDecimal> reaisGanhoPerdaPorEstrategia = new HashMap<>();

        // Stub: 7 estratégias vazias
        for (String est : new String[]{"MOMENTO_PURO", "BAIXO_SPREAD", "REGRA_PRINCIPAL",
                                        "RANKING_V2", "IA_MODELO", "LONGO_PRAZO", "ARBITRAGEM"}) {
            retornoPorEstrategia.put(est, BigDecimal.ZERO);
            reaisGanhoPerdaPorEstrategia.put(est, BigDecimal.ZERO);
        }

        return SimuladorCenarioDTO.builder()
                .simbolo(simbolo)
                .dataEntrada(dataEntrada)
                .dataSaida(dataEntrada.plusDays(janelaPregoesApos))
                .janelaPregoesApos(janelaPregoesApos)
                .capitalInicial(capitalInicial)
                .retornoPorEstrategia(retornoPorEstrategia)
                .reaisGanhoPerdaPorEstrategia(reaisGanhoPerdaPorEstrategia)
                .cdiPercentual(BigDecimal.ZERO)
                .cdiReais(BigDecimal.ZERO)
                .melhorEstrategia("—")
                .melhorRetorno(BigDecimal.ZERO)
                .piorEstrategia("—")
                .piorRetorno(BigDecimal.ZERO)
                .build();
    }
}

