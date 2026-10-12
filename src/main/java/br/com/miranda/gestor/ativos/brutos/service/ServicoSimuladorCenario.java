package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorCenarioDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAnalise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
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

    private final RepositorioSimuladorAnalise repositorio;

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

        List<RepositorioSimuladorAnalise.Cotacao> cotacoes = repositorio.cotacoes(simbolo, dataEntrada);
        RepositorioSimuladorAnalise.Cotacao entrada = cotacoes.isEmpty() ? null : cotacoes.get(0);
        RepositorioSimuladorAnalise.Cotacao saida = cotacoes.size() > janelaPregoesApos
                ? cotacoes.get(janelaPregoesApos) : null;
        LocalDate dataEfetiva = entrada == null ? null : entrada.dataPregao();
        List<RepositorioSimuladorAnalise.Sinal> sinais = dataEfetiva == null
                ? List.of() : repositorio.sinaisNaData(simbolo, dataEfetiva);

        Map<String, BigDecimal> retornoPorEstrategia = new LinkedHashMap<>();
        Map<String, BigDecimal> reaisGanhoPerdaPorEstrategia = new LinkedHashMap<>();
        BigDecimal retorno = retorno(entrada, saida);
        if (retorno != null) {
            for (var sinal : sinais) {
                retornoPorEstrategia.put(sinal.versaoEstrategia(), retorno);
                reaisGanhoPerdaPorEstrategia.put(sinal.versaoEstrategia(),
                        capitalInicial.multiply(retorno).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
        }

        BigDecimal cdiPercentual = saida == null ? null : cdiComposto(
                repositorio.cdi(entrada.dataPregao(), saida.dataPregao()),
                cotacoes.subList(0, janelaPregoesApos + 1).stream().map(RepositorioSimuladorAnalise.Cotacao::dataPregao).toList());
        BigDecimal cdiReais = cdiPercentual == null ? null : capitalInicial.multiply(cdiPercentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        var melhor = retornoPorEstrategia.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        var pior = retornoPorEstrategia.entrySet().stream().min(Map.Entry.comparingByValue()).orElse(null);

        return SimuladorCenarioDTO.builder()
                .simbolo(simbolo)
                .dataEntrada(dataEfetiva)
                .dataSaida(saida == null ? null : saida.dataPregao())
                .janelaPregoesApos(janelaPregoesApos)
                .capitalInicial(capitalInicial)
                .retornoPorEstrategia(retornoPorEstrategia)
                .reaisGanhoPerdaPorEstrategia(reaisGanhoPerdaPorEstrategia)
                .cdiPercentual(cdiPercentual)
                .cdiReais(cdiReais)
                .melhorEstrategia(melhor == null ? null : melhor.getKey())
                .melhorRetorno(melhor == null ? null : melhor.getValue())
                .piorEstrategia(pior == null ? null : pior.getKey())
                .piorRetorno(pior == null ? null : pior.getValue())
                .build();
    }

    private static BigDecimal retorno(RepositorioSimuladorAnalise.Cotacao entrada,
            RepositorioSimuladorAnalise.Cotacao saida) {
        if (entrada == null || saida == null || entrada.fechamento().signum() == 0) {
            return null;
        }
        return saida.fechamento().divide(entrada.fechamento(), 12, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal cdiComposto(List<RepositorioSimuladorAnalise.PontoCdi> taxasDiarias,
            List<LocalDate> pregoesEsperados) {
        if (taxasDiarias == null || taxasDiarias.isEmpty() || pregoesEsperados == null || pregoesEsperados.isEmpty()) {
            return null;
        }
        var datasDisponiveis = new HashSet<>(taxasDiarias.stream().map(RepositorioSimuladorAnalise.PontoCdi::data).toList());
        if (!datasDisponiveis.containsAll(pregoesEsperados)) {
            return null;
        }
        BigDecimal fator = BigDecimal.ONE;
        for (var ponto : taxasDiarias) {
            BigDecimal taxa = ponto.valor();
            fator = fator.multiply(BigDecimal.ONE.add(taxa.divide(BigDecimal.valueOf(100), 16, RoundingMode.HALF_UP)));
        }
        return fator.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
    }
}

