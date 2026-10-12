package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorRetroativoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorRetroativoEntradaDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAnalise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

/**
 * SIM-GES-6: Retroativo - valida estratégias históricas contra retorno real.
 * Cruza quintis históricos com preços reais do COTAHIST.
 * Retorna taxa de acerto e retorno médio por estratégia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoSimuladorRetroativo {

    private final RepositorioSimuladorAnalise repositorio;

    /**
     * SIM-GES-6: Analisa retroativo de uma estratégia em um período.
     * @param simbolo Ticker
     * @param estrategia Tipo (MOMENTO_PURO, BAIXO_SPREAD, etc)
     * @param versao Versão da regra
     * @param janelaPregoesApos Dias úteis de espera (21/63/126)
     * @param dataInicio Primeira data a considerar
     * @return DTO com taxa de acerto, retorno médio e lista de entradas
     */
    public SimuladorRetroativoDTO analisar(String simbolo, String estrategia, String versao,
                                           Integer janelaPregoesApos, LocalDate dataInicio) {
        log.info("{} - Analisando retroativo: {} {} {} desde {}", SERVICO, simbolo, estrategia, versao, dataInicio);

        List<RepositorioSimuladorAnalise.Cotacao> cotacoes = repositorio.cotacoes(simbolo, dataInicio);
        List<SimuladorRetroativoEntradaDTO> entradas = repositorio.sinais(simbolo, dataInicio).stream()
                .filter(sinal -> corresponde(sinal.versaoEstrategia(), estrategia, versao))
                .map(sinal -> entrada(sinal, cotacoes, estrategia, versao, janelaPregoesApos))
                .filter(java.util.Objects::nonNull)
                .toList();

        int ganhos = (int) entradas.stream().filter(e -> "GANHO".equals(e.getResultado())).count();
        int perdas = (int) entradas.stream().filter(e -> "PERDA".equals(e.getResultado())).count();
        int neutros = entradas.size() - ganhos - perdas;
        BigDecimal taxaAcerto = entradas.isEmpty() ? null : BigDecimal.valueOf(ganhos)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(entradas.size()), 4, RoundingMode.HALF_UP);
        BigDecimal retornoMedio = entradas.isEmpty() ? null : entradas.stream()
                .map(SimuladorRetroativoEntradaDTO::getResultadoPercentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(entradas.size()), 4, RoundingMode.HALF_UP);

        return SimuladorRetroativoDTO.builder()
                .simbolo(simbolo)
                .estrategia(estrategia)
                .versao(versao)
                .janelaPregoesApos(janelaPregoesApos)
                .totalEntradas(entradas.size())
                .ganhos(ganhos)
                .perdas(perdas)
                .neutros(neutros)
                .taxaAcerto(taxaAcerto)
                .retornoMedio(retornoMedio)
                .entradas(entradas)
                .build();
    }

    private static SimuladorRetroativoEntradaDTO entrada(RepositorioSimuladorAnalise.Sinal sinal,
            List<RepositorioSimuladorAnalise.Cotacao> cotacoes, String estrategia, String versao, int janela) {
        int indiceEntrada = 0;
        while (indiceEntrada < cotacoes.size()
                && cotacoes.get(indiceEntrada).dataPregao().isBefore(sinal.dataPregao())) {
            indiceEntrada++;
        }
        int indiceSaida = indiceEntrada + janela;
        if (indiceEntrada >= cotacoes.size() || indiceSaida >= cotacoes.size()) {
            return null;
        }
        var entrada = cotacoes.get(indiceEntrada);
        var saida = cotacoes.get(indiceSaida);
        if (entrada.fechamento().signum() == 0) {
            return null;
        }
        BigDecimal retorno = saida.fechamento().divide(entrada.fechamento(), 12, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
        String resultado = retorno.signum() > 0 ? "GANHO" : retorno.signum() < 0 ? "PERDA" : "NEUTRO";
        return SimuladorRetroativoEntradaDTO.builder()
                .dataPregao(sinal.dataPregao()).quintil(sinal.quintil()).estrategia(estrategia).versao(versao)
                .precoEntrada(entrada.fechamento()).dataJanela(saida.dataPregao()).precoSaida(saida.fechamento())
                .resultadoPercentual(retorno).resultado(resultado).dataAcertoDesde(saida.dataPregao()).build();
    }

    static boolean corresponde(String versaoEstrategia, String estrategia, String versao) {
        String valor = normalizar(versaoEstrategia);
        String tipo = normalizar(estrategia);
        String revisao = normalizar(versao);
        if (!valor.contains(tipo)) {
            return false;
        }
        return revisao.isBlank() || valor.equals(tipo) || valor.contains(revisao);
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}

