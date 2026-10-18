package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraFinanceiraDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraOperacaoRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraProventoRequest;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCarteiraFinanceira;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCarteiras;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServicoCarteiraFinanceiraTest {
    private RepositorioCarteiraFinanceira repo;
    private RepositorioCarteiras repoCarteira;
    private ServicoCarteiraFinanceira servico;

    @BeforeEach
    void setUp() {
        repo = mock(RepositorioCarteiraFinanceira.class);
        repoCarteira = mock(RepositorioCarteiras.class);
        servico = new ServicoCarteiraFinanceira(repo, repoCarteira);
        when(repoCarteira.schemaDisponivel()).thenReturn(true);
        when(repoCarteira.buscar("c_teste")).thenReturn(Optional.of(carteira()));
        when(repo.schemaFinanceiroDisponivel()).thenReturn(true);
        when(repo.carteiraAtiva("c_teste")).thenReturn(true);
    }

    @Test
    void compraRegistraQuantidadePrecoCustosEFluxoLiquidoCalculado() {
        when(repo.operacaoPorChave("c_teste", "op-1")).thenReturn(Optional.empty());
        when(repo.operacoes("c_teste")).thenReturn(List.of());

        var operacao = servico.registrarOperacao("c_teste", new CarteiraOperacaoRequest("COMPRA", "petr4",
                LocalDateTime.of(2026, 10, 16, 11, 0), new BigDecimal("10.00000000"), new BigDecimal("20.00"),
                null, new BigDecimal("1.00"), null, "BRL", "op-1"));

        assertEquals("PETR4", operacao.simbolo());
        assertEquals(new BigDecimal("200.00"), operacao.valorBruto());
        assertEquals(new BigDecimal("-201.00"), operacao.valorLiquido());
        assertEquals("MANUAL", operacao.origem());
        verify(repo).inserirOperacao(eq("c_teste"), same(operacao));
    }

    @Test
    void vendaMaiorQuePosicaoRegistradaERejeitada() {
        when(repo.operacaoPorChave("c_teste", "venda-1")).thenReturn(Optional.empty());
        when(repo.operacoes("c_teste")).thenReturn(List.of(operacao("COMPRA", "buy-1", null, "PETR4",
                new BigDecimal("10"), new BigDecimal("100.00"), new BigDecimal("-100.00"))));

        var erro = assertThrows(ResponseStatusException.class, () -> servico.registrarOperacao("c_teste",
                new CarteiraOperacaoRequest("VENDA", "PETR4", LocalDateTime.of(2026, 10, 17, 10, 0),
                        new BigDecimal("11"), new BigDecimal("10.00"), null, null, null, "BRL", "venda-1")));

        assertEquals(400, erro.getStatusCode().value());
        verify(repo, never()).inserirOperacao(anyString(), any());
    }

    @Test
    void proventoSoAceitaPagamentoJaOcorridoEValidaImposto() {
        when(repo.proventoPorChave("c_teste", "prov-1")).thenReturn(Optional.empty());
        var pago = servico.registrarProvento("c_teste", new CarteiraProventoRequest("PETR4", "DIVIDENDO",
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(2), new BigDecimal("10"),
                new BigDecimal("0.50"), new BigDecimal("5.00"), new BigDecimal("0.75"), "BRL", "EV-1", "prov-1"));
        assertEquals(new BigDecimal("4.25"), pago.valorLiquido());
        verify(repo).inserirProvento("c_teste", pago);

        var futuro = new CarteiraProventoRequest("PETR4", "JCP", null, LocalDate.now().plusDays(1), null,
                null, BigDecimal.ONE, null, "BRL", null, "prov-futuro");
        assertThrows(ResponseStatusException.class, () -> servico.registrarProvento("c_teste", futuro));
    }

    @Test
    void snapshotPersistePrecoOficialEMantemPrimeiroRetornoIndisponivel() {
        LocalDate data = LocalDate.of(2026, 10, 16);
        when(repo.operacoes("c_teste")).thenReturn(List.of(operacao("COMPRA", "buy-1", null, "PETR4",
                new BigDecimal("10"), new BigDecimal("200.00"), new BigDecimal("-200.00"))));
        when(repo.proventos("c_teste")).thenReturn(List.of());
        when(repo.ultimoPregao(data)).thenReturn(Optional.of(data));
        when(repo.cotacao("PETR4", data)).thenReturn(Optional.of(new RepositorioCarteiraFinanceira.Cotacao(data, new BigDecimal("25.00"))));
        when(repo.rentabilidadeAnterior(eq("c_teste"), eq(data), anyString())).thenReturn(Optional.empty());

        var snapshot = servico.calcularSnapshot("c_teste", data, null);

        assertEquals(new BigDecimal("250.00"), snapshot.rentabilidade().valorMercado());
        assertEquals(new BigDecimal("50.00"), snapshot.rentabilidade().resultadoNaoRealizado());
        assertEquals("TWR", snapshot.rentabilidade().metodo());
        assertNull(snapshot.rentabilidade().retornoPeriodo());
        assertEquals(data, snapshot.ativos().getFirst().dataPregaoPreco());
        verify(repo).salvarAvaliacao(eq("c_teste"), any(), eq(data));
        verify(repo).salvarRentabilidade(eq("c_teste"), same(snapshot.rentabilidade()));
    }

    @Test
    void snapshotSemCotacaoNaoFabricaValorDeMercadoOuRetorno() {
        LocalDate data = LocalDate.of(2026, 10, 16);
        when(repo.operacoes("c_teste")).thenReturn(List.of(operacao("COMPRA", "buy-1", null, "PETR4",
                new BigDecimal("10"), new BigDecimal("200.00"), new BigDecimal("-200.00"))));
        when(repo.proventos("c_teste")).thenReturn(List.of());
        when(repo.ultimoPregao(data)).thenReturn(Optional.of(data));
        when(repo.cotacao("PETR4", data)).thenReturn(Optional.empty());
        when(repo.rentabilidadeAnterior(eq("c_teste"), eq(data), anyString())).thenReturn(Optional.empty());

        var snapshot = servico.calcularSnapshot("c_teste", data, "CARTDB-1");

        assertEquals(List.of("PETR4"), snapshot.simbolosSemCotacao());
        assertEquals("INDISPONIVEL", snapshot.rentabilidade().metodo());
        assertNull(snapshot.rentabilidade().retornoPeriodo());
        assertEquals(BigDecimal.ZERO, snapshot.rentabilidade().valorMercado());
        verify(repo, never()).salvarAvaliacao(anyString(), any(), any());
    }

    @Test
    void snapshotEstornoDeCompraCancelaQuantidadeECustoSemGerarResultadoRealizado() {
        LocalDate data = LocalDate.of(2026, 10, 16);
        var compra = operacao("COMPRA", "buy-1", null, "PETR4", new BigDecimal("10"),
                new BigDecimal("201.00"), new BigDecimal("-201.00"));
        var estorno = operacao("ESTORNO", "reversal-1", "buy-1", "PETR4", new BigDecimal("10"),
                new BigDecimal("201.00"), new BigDecimal("201.00"));
        when(repo.operacoes("c_teste")).thenReturn(List.of(compra, estorno));
        when(repo.proventos("c_teste")).thenReturn(List.of());
        when(repo.ultimoPregao(data)).thenReturn(Optional.of(data));
        when(repo.rentabilidadeAnterior(eq("c_teste"), eq(data), anyString())).thenReturn(Optional.empty());

        var snapshot = servico.calcularSnapshot("c_teste", data, "CARTDB-1");

        assertTrue(snapshot.ativos().isEmpty());
        assertEquals(0, snapshot.rentabilidade().resultadoRealizado().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.00"), snapshot.rentabilidade().saldoCaixa());
    }

    @Test
    void snapshotEstornoDeVendaRestauraPosicaoCustoEReverteResultadoRealizado() {
        LocalDate data = LocalDate.of(2026, 10, 16);
        var compra = operacao("COMPRA", "buy-1", null, "PETR4", new BigDecimal("10"),
                new BigDecimal("1000.00"), new BigDecimal("-1000.00"));
        var venda = operacao("VENDA", "sell-1", null, "PETR4", new BigDecimal("5"),
                new BigDecimal("125.00"), new BigDecimal("125.00"));
        var estorno = operacao("ESTORNO", "reversal-1", "sell-1", "PETR4", new BigDecimal("5"),
                new BigDecimal("125.00"), new BigDecimal("-125.00"));
        when(repo.operacoes("c_teste")).thenReturn(List.of(compra, venda, estorno));
        when(repo.proventos("c_teste")).thenReturn(List.of());
        when(repo.ultimoPregao(data)).thenReturn(Optional.of(data));
        when(repo.cotacao("PETR4", data)).thenReturn(Optional.of(new RepositorioCarteiraFinanceira.Cotacao(data, new BigDecimal("110.00"))));
        when(repo.rentabilidadeAnterior(eq("c_teste"), eq(data), anyString())).thenReturn(Optional.empty());

        var snapshot = servico.calcularSnapshot("c_teste", data, "CARTDB-1");

        assertEquals(new BigDecimal("10"), snapshot.ativos().getFirst().quantidade());
        assertEquals(new BigDecimal("1100.00"), snapshot.rentabilidade().valorMercado());
        assertEquals(new BigDecimal("100.00"), snapshot.rentabilidade().resultadoNaoRealizado());
        assertEquals(new BigDecimal("0.00"), snapshot.rentabilidade().resultadoRealizado());
    }

    private CarteiraDTO carteira() {
        return new CarteiraDTO("c_teste", "Teste", "PADRAO", "#369", "BRL", "ATIVA", null, List.of());
    }

    private CarteiraFinanceiraDTO.Operacao operacao(String tipo, String id, String referencia, String simbolo,
                                                     BigDecimal quantidade, BigDecimal bruto, BigDecimal liquido) {
        return new CarteiraFinanceiraDTO.Operacao(id, tipo, referencia, simbolo,
                LocalDateTime.of(2026, 10, 15, 12, 0), quantidade, new BigDecimal("20.00"), bruto,
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), liquido, "BRL", "MANUAL", id);
    }
}
