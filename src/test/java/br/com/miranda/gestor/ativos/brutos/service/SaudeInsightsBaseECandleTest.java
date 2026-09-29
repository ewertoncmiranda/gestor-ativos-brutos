package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.SaudeDadosDTO.Fonte;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSaudeDados.CoberturaInsightsBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SaudeInsightsBaseECandleTest {

    private static final Fonte OK_NO_PRAZO = new Fonte("INSIGHTS_BASE", "Insights", LocalDateTime.of(2026, 9, 27, 22, 45),
            12L, 100, "OK", null, "rodar --recuperar");

    @Test
    void pregao_do_cotahist_sem_insight_fica_atrasado_mesmo_dentro_do_prazo() {
        Fonte f = ServicoSaudeDados.comAtrasoDePregao(OK_NO_PRAZO,
                new CoberturaInsightsBase(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 25)));
        assertEquals("ATRASADA", f.estado());
        assertTrue(f.ultimoErro().contains("2026-09-28"));
    }

    @Test
    void insight_do_ultimo_pregao_mantem_o_estado() {
        Fonte f = ServicoSaudeDados.comAtrasoDePregao(OK_NO_PRAZO,
                new CoberturaInsightsBase(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25)));
        assertSame(OK_NO_PRAZO, f);
        assertSame(OK_NO_PRAZO, ServicoSaudeDados.comAtrasoDePregao(OK_NO_PRAZO, new CoberturaInsightsBase(null, null)));
    }

    @Test
    void candle_do_dia_usa_a_data_do_dado_em_sao_paulo() {
        // 01:30 UTC de 29/09 ainda e 28/09 em Sao Paulo
        assertEquals(LocalDate.of(2026, 9, 28), ServicoAtualizacaoCache.diaDoPregao("2026-09-29T01:30:00.000Z"));
        assertEquals(LocalDate.of(2026, 9, 25), ServicoAtualizacaoCache.diaDoPregao("2026-09-25T20:07:00Z"));
        assertNotNull(ServicoAtualizacaoCache.diaDoPregao(null));
    }
}
