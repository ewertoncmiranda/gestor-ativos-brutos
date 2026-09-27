package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.BacktestDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioBacktest;
import br.com.miranda.gestor.ativos.brutos.tools.IntervaloConfianca;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Monta a visao do ultimo backtest walk-forward (CTR-14). A taxa-base ja vem
 * gravada na direcao da aposta (alta para compra, queda para venda).
 */
@Service
@RequiredArgsConstructor
public class ServicoBacktest {

    public static final String AVISO =
            "Simulação sobre preços oficiais da B3 (COTAHIST, sem ajuste de proventos) e balanços da CVM "
                    + "pela data de entrega. Mede o método, não é recomendação de investimento. Resultado do "
                    + "período de teste é o que vale: o de calibração foi visto ao ajustar as regras.";

    private final RepositorioBacktest repositorio;
    private final ObjectMapper objectMapper;

    public BacktestDTO montar() {
        return repositorio.ultimaExecucao()
                .map(e -> new BacktestDTO(
                        new BacktestDTO.Execucao(e.id(), e.finalizadoEm(), e.inicioPeriodo(), e.fimPeriodo(),
                                e.corteCalibracao(), e.ativos(), e.sinais(), json(e.parametrosJson()),
                                e.observacoes()),
                        repositorio.placar(e.id()).stream().map(ServicoBacktest::linha).toList(),
                        ServicoDiarioDeSinais.AMOSTRA_MINIMA,
                        AVISO))
                .orElseGet(() -> new BacktestDTO(null, List.of(), ServicoDiarioDeSinais.AMOSTRA_MINIMA, AVISO));
    }

    private static BacktestDTO.Linha linha(RepositorioBacktest.Linha l) {
        int direcao = ServicoDiarioDeSinais.direcao(l.recomendacao());
        BigDecimal taxaAcerto = l.acertos() == null || l.avaliados() == 0 ? null
                : BigDecimal.valueOf(l.acertos()).divide(BigDecimal.valueOf(l.avaliados()), 4, RoundingMode.HALF_UP);
        return new BacktestDTO.Linha(l.versaoRegra(), l.periodo(), l.recomendacao(), direcao, l.horizonte(),
                l.avaliados(), taxaAcerto, l.taxaBase(), l.retornoMedio(), l.excessoMedioCdi(),
                l.excessoMedioCarteira(), l.avaliados() >= ServicoDiarioDeSinais.AMOSTRA_MINIMA,
                l.acertos() == null ? null : IntervaloConfianca.wilson(l.acertos(), l.avaliados()),
                IntervaloConfianca.media(l.excessoMedioCdi(), l.desvioExcessoCdi(), l.nExcessoCdi()),
                IntervaloConfianca.media(l.excessoMedioCarteira(), l.desvioExcessoCarteira(), l.nExcessoCarteira()),
                l.janelasComProvento());
    }

    private JsonNode json(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return objectMapper.readTree(texto);
        } catch (Exception e) {
            return null;
        }
    }
}
