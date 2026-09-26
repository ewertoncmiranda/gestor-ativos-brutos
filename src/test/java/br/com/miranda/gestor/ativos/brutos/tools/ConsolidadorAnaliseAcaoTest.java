package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.AnaliseConsolidadaDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsolidadorAnaliseAcaoTest {

    @Test
    void conta_venda_valuation_como_sinal_de_venda() {
        List<AnaliseAcaoEntity> analises = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            analises.add(analise("VENDA_VALUATION", i));
        }
        for (int i = 4; i < 10; i++) {
            analises.add(analise("MANTER", i));
        }

        AnaliseConsolidadaDTO resultado = ConsolidadorAnaliseAcao.consolidar(analises);

        assertEquals(40.0, resultado.getPercentualSinaisVenda());
        assertEquals("MANTER", resultado.getSinalPredominante());
    }

    @Test
    void aceita_familia_de_recomendacoes_de_venda() {
        AnaliseConsolidadaDTO resultado = ConsolidadorAnaliseAcao.consolidar(List.of(
                analise("VENDA_VALUATION", 0),
                analise("VENDA_TECNICA", 1),
                analise("COMPRA_FORTE", 2)
        ));

        assertEquals(66.67, resultado.getPercentualSinaisVenda());
    }

    @Test
    void ignora_recomendacao_nula_sem_falhar() {
        AnaliseAcaoEntity semRecomendacao = analise(null, 0);

        AnaliseConsolidadaDTO resultado = ConsolidadorAnaliseAcao.consolidar(List.of(
                semRecomendacao,
                analise("MANTER", 1)
        ));

        assertEquals("MANTER", resultado.getSinalPredominante());
        assertEquals(0.0, resultado.getPercentualSinaisVenda());
        assertEquals(2, resultado.getQuantidadeRegistros());
    }

    @Test
    void lista_sem_analises_validas_devolve_nulo() {
        List<AnaliseAcaoEntity> apenasNulos = new ArrayList<>();
        apenasNulos.add(null);
        assertNull(ConsolidadorAnaliseAcao.consolidar(apenasNulos));
    }

    private static AnaliseAcaoEntity analise(String recomendacao, int deslocamentoDias) {
        AnaliseAcaoEntity analise = new AnaliseAcaoEntity();
        analise.setSimbolo("PETR4");
        analise.setDataAnalise(LocalDateTime.of(2026, 9, 1, 12, 0).plusDays(deslocamentoDias));
        analise.setRecomendacao(recomendacao);
        analise.setMargemSegurancaPercent(new BigDecimal("10.00"));
        return analise;
    }
}
