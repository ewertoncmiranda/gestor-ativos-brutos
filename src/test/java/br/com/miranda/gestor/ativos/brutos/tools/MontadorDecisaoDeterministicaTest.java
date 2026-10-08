package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.dto.AnaliseConsolidadaDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaAnaliseIaDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MontadorDecisaoDeterministicaTest {

    @Test
    void decisao_deterministica_traz_aviso_legal() {
        AnaliseConsolidadaDTO consolidado = AnaliseConsolidadaDTO.builder()
                .ativo("PETR4")
                .quantidadeRegistros(10)
                .sinalPredominante("SEM_MARGEM")
                .percentualSinaisVenda(0.0)
                .variacaoMedia(5.0)
                .build();

        RespostaAnaliseIaDTO resposta = MontadorDecisaoDeterministica.montar(consolidado);

        assertEquals(MontadorDecisaoDeterministica.AVISO_LEGAL, resposta.getAvisoLegal());
        assertTrue(resposta.getAvisoLegal().contains("Não constitui recomendação"));
    }
}
