package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.DiarioDeSinaisDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDiarioDeSinais;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDiarioDeSinais.LinhaPlacar;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDiarioDeSinais.Resultado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDiarioDeSinais.Sinal;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDiarioDeSinais.Totais;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicoDiarioDeSinaisTest {

    private static final LocalDate SEGUNDA = LocalDate.of(2026, 9, 28);

    @Mock
    private RepositorioDiarioDeSinais repositorio;

    @InjectMocks
    private ServicoDiarioDeSinais servico;

    private void comDiarioVazio() {
        when(repositorio.totais(any())).thenReturn(new Totais(0, 0, null, null, 0));
        when(repositorio.sinaisRecentes(any(), anyInt())).thenReturn(List.of());
        when(repositorio.resultadosDe(anyList())).thenReturn(List.of());
        when(repositorio.taxaBaseDeAlta(any())).thenReturn(Map.of());
        when(repositorio.placar(any())).thenReturn(List.of());
        when(repositorio.simbolosComSinal()).thenReturn(List.of());
    }

    @Test
    void diario_vazio_devolve_estrutura_completa_sem_erro() {
        comDiarioVazio();

        DiarioDeSinaisDTO dto = servico.montar(null, null);

        assertEquals(0, dto.totalSinais());
        assertEquals(0, dto.horizontesPendentes());
        assertEquals(List.of(21, 63, 126), dto.horizontes());
        assertTrue(dto.placar().isEmpty());
        assertTrue(dto.linhaDoTempo().isEmpty());
        verify(repositorio).sinaisRecentes(null, 120);
    }

    @Test
    void pendentes_sao_os_horizontes_ainda_nao_avaliados() {
        comDiarioVazio();
        when(repositorio.totais(any())).thenReturn(new Totais(10, 5, SEGUNDA, SEGUNDA, 4));

        assertEquals(26, servico.montar(null, null).horizontesPendentes()); // 10 x 3 - 4
    }

    @Test
    void linha_do_tempo_junta_os_resultados_de_cada_sinal() {
        comDiarioVazio();
        when(repositorio.sinaisRecentes(any(), anyInt())).thenReturn(List.of(
                new Sinal(7, "PETR4", SEGUNDA, "2026.09.26-1", "COMPRA_FORTE", "BAIXO", 80, new BigDecimal("48.00"))));
        when(repositorio.resultadosDe(List.of(7L))).thenReturn(List.of(
                new Resultado(7, 21, SEGUNDA.plusDays(30), new BigDecimal("0.031"), new BigDecimal("0.02"),
                        null, true, false)));

        DiarioDeSinaisDTO.SinalNaLinhaDoTempo sinal = servico.montar("petr4", 50).linhaDoTempo().get(0);

        assertEquals(1, sinal.direcao());
        assertEquals(Boolean.TRUE, sinal.resultados().get(21).acerto());
        assertNull(sinal.resultados().get(63)); // ainda pendente
        verify(repositorio).sinaisRecentes("PETR4", 50);
    }

    @Test
    void acerto_de_venda_e_medido_contra_a_taxa_base_de_queda() {
        List<DiarioDeSinaisDTO.LinhaPlacar> placar = ServicoDiarioDeSinais.montarPlacar(
                List.of(
                        new LinhaPlacar("v1", "COMPRA_FORTE", 21, 40, 40, 26, null, null, null),
                        new LinhaPlacar("v1", "VENDA_VALUATION", 21, 10, 10, 6, null, null, null)),
                Map.of(21, new BigDecimal("0.55")));

        DiarioDeSinaisDTO.LinhaPlacar compra = placar.get(0);
        DiarioDeSinaisDTO.LinhaPlacar venda = placar.get(1);
        assertEquals(new BigDecimal("0.6500"), compra.taxaAcerto());
        assertEquals(new BigDecimal("0.5500"), compra.taxaBase());
        assertTrue(compra.amostraSuficiente());
        assertEquals(new BigDecimal("0.4500"), venda.taxaBase()); // 1 - 0,55
        assertFalse(venda.amostraSuficiente()); // 10 < 30
    }

    @Test
    void recomendacao_sem_direcao_nao_tem_taxa_de_acerto_nem_base() {
        List<DiarioDeSinaisDTO.LinhaPlacar> placar = ServicoDiarioDeSinais.montarPlacar(
                List.of(new LinhaPlacar("v1", "MANTER", 63, 12, 0, 0, new BigDecimal("0.01"), null, null)),
                Map.of(63, new BigDecimal("0.5")));

        assertNull(placar.get(0).taxaAcerto());
        assertNull(placar.get(0).taxaBase());
        assertEquals(new BigDecimal("0.0100"), placar.get(0).retornoMedio());
    }

    @Test
    void versao_mais_nova_aparece_primeiro_no_placar() {
        List<DiarioDeSinaisDTO.LinhaPlacar> placar = ServicoDiarioDeSinais.montarPlacar(
                List.of(new LinhaPlacar("2026.09.26-1", "MANTER", 21, 1, 0, 0, null, null, null),
                        new LinhaPlacar("2026.10.15-1", "MANTER", 21, 1, 0, 0, null, null, null)),
                Map.of());

        assertEquals("2026.10.15-1", placar.get(0).versaoRegra());
    }

    @Test
    void limite_fora_da_faixa_e_erro_do_cliente() {
        assertThrows(IllegalArgumentException.class, () -> servico.montar(null, 0));
        assertThrows(IllegalArgumentException.class, () -> servico.montar(null, 5000));
    }
}
