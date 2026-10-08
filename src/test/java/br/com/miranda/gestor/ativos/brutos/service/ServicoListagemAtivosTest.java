package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoListagemDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioListagemAtivos;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioListagemAtivos.Filtro;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioListagemAtivos.Linha;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServicoListagemAtivosTest {

    private static final Filtro SEM_FILTRO = new Filtro(null, null, false, false);
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 6);

    private static Linha linha(String simbolo, BigDecimal fechamento, LocalDate data, BigDecimal anterior,
                               String recomendacao, LocalDate entregaComunicado) {
        return new Linha(simbolo, "Empresa " + simbolo, "Energia", null, true, true, fechamento, data, anterior, HOJE,
                recomendacao, recomendacao == null ? null : LocalDateTime.of(2026, 10, 6, 7, 1),
                recomendacao == null ? null : "2026.09.27-3", "FATO_RELEVANTE", "Assunto",
                entregaComunicado, "http://cvm/x", true);
    }

    @Test
    void montaLinhaCompletaComDuasConsultasPorPagina() {
        var repositorio = mock(RepositorioListagemAtivos.class);
        when(repositorio.contar(SEM_FILTRO)).thenReturn(2L);
        when(repositorio.pagina(SEM_FILTRO, 0, 30)).thenReturn(List.of(
                linha("PETR4", new BigDecimal("53.82"), HOJE, new BigDecimal("55.36"), "MANTER", HOJE),
                linha("VALE3", new BigDecimal("70.37"), HOJE, new BigDecimal("71.40"), null, null)));
        when(repositorio.fechamentosRecentes(List.of("PETR4", "VALE3"), 20))
                .thenReturn(Map.of("PETR4", List.of(new BigDecimal("55.36"), new BigDecimal("53.82"))));

        var pagina = new ServicoListagemAtivos(repositorio).listar(SEM_FILTRO, 0, 30);

        assertEquals(2, pagina.getTotalElements());
        AtivoListagemDTO petr = pagina.getContent().get(0);
        assertEquals(new BigDecimal("-2.78"), petr.variacaoPercentual());
        assertEquals(2, petr.serieFechamentos().size());
        assertEquals("MANTER", petr.sinal().recomendacao());
        assertEquals("2026.09.27-3", petr.sinal().versaoRegra());
        assertEquals("FATO_RELEVANTE", petr.ultimoComunicado().categoria());
        assertFalse(petr.selos().pregaoDefasado());

        AtivoListagemDTO vale = pagina.getContent().get(1);
        assertNull(vale.sinal(), "sem insight: sinal ausente, nunca um rotulo inventado");
        assertNull(vale.ultimoComunicado());
        assertEquals(List.of(), vale.serieFechamentos());
        verify(repositorio, times(1)).pagina(any(), anyInt(), anyInt());
        verify(repositorio, times(1)).fechamentosRecentes(anyCollection(), anyInt());
    }

    @Test
    void pregaoAntigoOuAusenteMarcaDefasadoEVariacaoNula() {
        AtivoListagemDTO antigo = ServicoListagemAtivos.paraDto(
                linha("OIBR3", new BigDecimal("1.00"), HOJE.minusDays(30), null, null, null), List.of());
        assertTrue(antigo.selos().pregaoDefasado());
        assertNull(antigo.variacaoPercentual(), "sem fechamento anterior a variacao e null, nao 0");

        AtivoListagemDTO semPreco = ServicoListagemAtivos.paraDto(
                linha("XXXX3", null, null, null, null, null), List.of());
        assertTrue(semPreco.selos().pregaoDefasado());
        assertNull(semPreco.ultimoFechamento());
    }

    @Test
    void tamanhoLimitadoEPaginaAlemDoFimNaoConsultaLinhas() {
        var repositorio = mock(RepositorioListagemAtivos.class);
        when(repositorio.contar(SEM_FILTRO)).thenReturn(5L);

        var alemDoFim = new ServicoListagemAtivos(repositorio).listar(SEM_FILTRO, 3, 10);
        assertTrue(alemDoFim.getContent().isEmpty());
        assertEquals(5, alemDoFim.getTotalElements());
        verify(repositorio, never()).pagina(any(), anyInt(), anyInt());

        when(repositorio.pagina(SEM_FILTRO, 0, ServicoListagemAtivos.TAMANHO_MAXIMO)).thenReturn(List.of());
        new ServicoListagemAtivos(repositorio).listar(SEM_FILTRO, -1, 10_000);
        verify(repositorio).pagina(SEM_FILTRO, 0, ServicoListagemAtivos.TAMANHO_MAXIMO);
    }

    @Test
    void variacaoComAnteriorZeroENula() {
        assertNull(ServicoListagemAtivos.variacaoPercentual(BigDecimal.ONE, BigDecimal.ZERO));
        assertEquals(new BigDecimal("10.00"),
                ServicoListagemAtivos.variacaoPercentual(new BigDecimal("11"), new BigDecimal("10")));
    }
}
