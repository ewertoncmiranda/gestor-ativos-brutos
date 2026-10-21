package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.InsidersDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RecomprasDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioInsiders;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioInsiders.Negociacao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioInsiders.SaldoTesouraria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicoInsidersTest {

    static final LocalDate HOJE = LocalDate.of(2026, 10, 20);
    RepositorioInsiders repo;
    ServicoInsiders servico;

    @BeforeEach
    void setUp() {
        repo = mock(RepositorioInsiders.class);
        RepositorioIdentidadeAtivo id = mock(RepositorioIdentidadeAtivo.class);
        when(id.canonico(any())).thenAnswer(i -> i.getArgument(0));
        when(repo.schemaDisponivel()).thenReturn(true);
        when(repo.cnpj("WEGE3")).thenReturn(Optional.of("84429695000111"));
        servico = new ServicoInsiders(repo, id);
    }

    static Negociacao n(String cargo, String mov, String ativo, LocalDate data, String qtd, String vol) {
        return new Negociacao(cargo, mov, ativo, data, LocalDate.of(2026, 10, 1), new BigDecimal(qtd),
                vol == null ? null : new BigDecimal(vol));
    }

    void linhas(Negociacao... l) {
        when(repo.negociacoes(any(), any())).thenReturn(List.of(l));
    }

    @Test
    void compraLiquidaPositivaPorJanelaECargo() {
        linhas(n("Diretor ou Vinculado", "Compra à vista", "Ações", HOJE.minusMonths(2), "1000", "10000"),
                n("Diretor ou Vinculado", "Venda à vista", "Ações", HOJE.minusMonths(2), "200", "2000"),
                n("Controlador ou Vinculado", "Compra a termo", "Units", HOJE.minusMonths(5), "500", null),
                n("Conselho Fiscal", "Venda à vista", "Ações", HOJE.minusMonths(10), "50", "500"));
        InsidersDTO d = servico.insiders("wege3", HOJE);
        assertTrue(d.disponivel());
        assertEquals("COMPRA_LIQUIDA", d.sinal());
        var j3 = d.janelas().get(0);
        assertEquals(0, new BigDecimal("800").compareTo(j3.total().compraLiquidaQuantidade()));
        assertEquals(0, new BigDecimal("8000").compareTo(j3.total().compraLiquidaVolume()));
        var j6 = d.janelas().get(1);
        assertEquals(0, new BigDecimal("1300").compareTo(j6.total().compraLiquidaQuantidade()));
        assertEquals(0, new BigDecimal("500").compareTo(j6.porCargo().get("Controlador").compraLiquidaQuantidade()));
        assertNull(j6.porCargo().get("Controlador").compraLiquidaVolume());
        var j12 = d.janelas().get(2);
        assertEquals(1250, j12.total().compraLiquidaQuantidade().intValue());
        assertEquals(-50, j12.porCargo().get("Conselho Fiscal").compraLiquidaQuantidade().intValue());
        assertEquals(4, j12.total().movimentacoes());
        assertEquals(HOJE.minusMonths(2), d.dataUltimaMovimentacao());
        assertEquals(LocalDate.of(2026, 10, 1), d.dataEntregaMaisRecente());
    }

    @Test
    void vendaLiquida() {
        linhas(n("Diretor", "Venda à vista", "Ações", HOJE.minusMonths(1), "1000", null),
                n("Diretor", "Compra à vista", "Ações", HOJE.minusMonths(1), "100", null));
        assertEquals("VENDA_LIQUIDA", servico.insiders("WEGE3", HOJE).sinal());
    }

    @Test
    void neutroQuandoEquilibrado() {
        linhas(n("Diretor", "Venda à vista", "Ações", HOJE.minusMonths(1), "1000", null),
                n("Diretor", "Compra à vista", "Ações", HOJE.minusMonths(1), "950", null));
        assertEquals("NEUTRO", servico.insiders("WEGE3", HOJE).sinal());
    }

    @Test
    void filtraTiposEAcentos() {
        linhas(n("Diretor", "Saldo Inicial", "Ações", HOJE.minusMonths(1), "9999", null),
                n("Diretor", "Compra à vista", "Opções", HOJE.minusMonths(1), "9999", null),
                n("Diretor", "Compra Ã  vista", "AÃ§Ãµes", HOJE.minusMonths(1), "10", null),
                n("Diretor", "COMPRA A VISTA", "acoes", HOJE.minusMonths(1), "5", null),
                n("Diretor", "Compra à vista", "Ações", HOJE.plusDays(3), "777", null));
        InsidersDTO d = servico.insiders("WEGE3", HOJE);
        assertEquals(15, d.janelas().get(2).total().compraLiquidaQuantidade().intValue());
        assertEquals(2, d.janelas().get(2).total().movimentacoes());
    }

    @Test
    void semDadosZerado() {
        linhas();
        InsidersDTO d = servico.insiders("WEGE3", HOJE);
        assertFalse(d.disponivel());
        assertNotNull(d.motivo());
        assertEquals(0, d.janelas().get(0).total().movimentacoes());
        assertNull(d.dataEntregaMaisRecente());
    }

    @Test
    void semV45DevolveTrintaETres() {
        when(repo.schemaDisponivel()).thenReturn(false);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servico.insiders("WEGE3", HOJE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    @Test
    void recomprasIndisponivelSemContas() {
        when(repo.saldosTesouraria(any(), any())).thenReturn(List.of());
        RecomprasDTO r = servico.recompras("WEGE3", HOJE);
        assertFalse(r.disponivel());
        assertTrue(r.motivo().contains("FONTES-ETL-8"));
        assertTrue(r.serie().isEmpty());
    }

    @Test
    void recomprasSerieEVariacao() {
        when(repo.saldosTesouraria(any(), any())).thenReturn(List.of(
                new SaldoTesouraria(LocalDate.of(2025, 12, 31), "DFP", "con", new BigDecimal("-100")),
                new SaldoTesouraria(LocalDate.of(2026, 3, 31), "ITR", "ind", new BigDecimal("-90")),
                new SaldoTesouraria(LocalDate.of(2026, 3, 31), "ITR", "con", new BigDecimal("-150")),
                new SaldoTesouraria(LocalDate.of(2026, 6, 30), "ITR", "con", new BigDecimal("-120"))));
        RecomprasDTO r = servico.recompras("WEGE3", HOJE);
        assertTrue(r.disponivel());
        assertEquals(3, r.serie().size());
        assertNull(r.serie().get(0).variacao());
        assertEquals(0, new BigDecimal("50").compareTo(r.serie().get(1).recompraLiquida()));
        assertEquals(0, new BigDecimal("-30").compareTo(r.serie().get(2).recompraLiquida()));
        assertEquals(0, new BigDecimal("20").compareTo(r.recompraLiquida12m()));
    }
}
