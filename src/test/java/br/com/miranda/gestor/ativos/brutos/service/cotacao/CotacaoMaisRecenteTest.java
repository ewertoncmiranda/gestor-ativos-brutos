package br.com.miranda.gestor.ativos.brutos.service.cotacao;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CotacaoMaisRecenteTest {

    private static final LocalDateTime FECHAMENTO_SEXTA = LocalDateTime.of(2026, 9, 25, 18, 0);

    private final CotacaoOficialCotahist oficial = mock(CotacaoOficialCotahist.class);
    private final CotacaoIntradiariaCache intradiaria = mock(CotacaoIntradiariaCache.class);
    private final CotacaoMaisRecente composta = new CotacaoMaisRecente(oficial, intradiaria);

    private static CotacaoRecente cotacao(String preco, LocalDateTime quando, String fonte) {
        return new CotacaoRecente(new BigDecimal(preco), null, quando, fonte);
    }

    @Test
    void favorito_no_pregao_usa_a_intradiaria_mais_nova() {
        when(oficial.cotacao("RAIZ4")).thenReturn(Optional.of(cotacao("1.10", FECHAMENTO_SEXTA, "B3_COTAHIST")));
        when(intradiaria.cotacao("RAIZ4")).thenReturn(
                Optional.of(cotacao("1.15", FECHAMENTO_SEXTA.plusDays(3).minusHours(6), "BRAPI")));
        assertEquals("BRAPI", composta.cotacao("RAIZ4").orElseThrow().fonte());
    }

    @Test
    void cache_intradiario_velho_perde_para_o_fechamento_oficial() {
        when(oficial.cotacao("PETR4")).thenReturn(Optional.of(cotacao("47.99", FECHAMENTO_SEXTA, "B3_COTAHIST")));
        when(intradiaria.cotacao("PETR4")).thenReturn(
                Optional.of(cotacao("45.00", FECHAMENTO_SEXTA.minusDays(10), "BRAPI")));
        assertEquals(new BigDecimal("47.99"), composta.cotacao("PETR4").orElseThrow().preco());
    }

    @Test
    void referencia_sem_brapi_fica_com_o_oficial_e_sem_nada_e_vazio() {
        when(oficial.cotacao("VALE3")).thenReturn(Optional.of(cotacao("60.00", FECHAMENTO_SEXTA, "B3_COTAHIST")));
        when(intradiaria.cotacao("VALE3")).thenReturn(Optional.empty());
        when(oficial.cotacao("XXXX3")).thenReturn(Optional.empty());
        when(intradiaria.cotacao("XXXX3")).thenReturn(Optional.empty());
        assertEquals("B3_COTAHIST", composta.cotacao("VALE3").orElseThrow().fonte());
        assertTrue(composta.cotacao("XXXX3").isEmpty());
    }
}
