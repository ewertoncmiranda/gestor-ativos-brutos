package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.ProventoDistribuidoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO.Vela;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicoProventosResumoTest {
    private static final String ISIN = "BRITUBACNPR1";
    private static LocalDate d(String s) { return LocalDate.parse(s); }
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static Vela vela(String data, String abertura, String fechamento, String marca) {
        return new Vela(d(data), d(data), "ITUB4", n(abertura), n("12"), n("8"), n(fechamento),
                100L, 10L, n("1000"), 1, "B3_COTAHIST", null, null, null, marca);
    }
    private static ProventoDistribuidoEntity provento(String isin, String tipo, String com, String valor, String pagamento) {
        var p = new ProventoDistribuidoEntity(); p.setSimbolo("ITUB"); p.setIsin(isin); p.setTipo(tipo);
        p.setUltimaDataComDireito(d(com)); p.setValorPorAcao(n(valor)); p.setDataPagamento(d(pagamento));
        p.setAtualizadoEm(d("2026-10-20").atStartOfDay()); return p;
    }
    @Test void consolidaMarcaSemTransformarBordaDaJanelaEmEvento() {
        var historico = List.of(vela("2026-05-01", "10", "10", null), vela("2026-05-04", "9.8", "10", "EJ"),
                vela("2026-05-05", "10", "10", "EJ"), vela("2026-05-06", "10", "11", "EJ"));
        var r = ServicoProventosResumo.calcular("ITUB4", d("2026-05-05"), d("2026-05-06"), historico, ISIN, List.of());
        assertTrue(r.eventos().isEmpty()); assertNull(r.resumo().totalPorAcao());
        assertEquals(0, r.resumo().quantidadeEventos());
    }
    @Test void uneParcelasPorIsinENaoMisturaOnComPn() {
        var historico = List.of(vela("2026-05-01", "10", "10", null), vela("2026-05-04", "9.8", "10", "EJ"),
                vela("2026-05-05", "10", "11", "EJ"));
        var p1 = provento(ISIN, "JCP", "2026-05-01", "0.1", "2026-06-01");
        var p2 = provento(ISIN, "JCP", "2026-05-01", "0.2", "2026-07-01");
        var on = provento("BRITUBACNOR4", "JCP", "2026-05-01", "9", "2026-06-01");
        var r = ServicoProventosResumo.calcular("ITUB4", d("2026-05-01"), d("2026-05-05"), historico, ISIN, List.of(p1,p2,on,p1));
        assertEquals(1, r.eventos().size()); assertEquals(2, r.eventos().getFirst().pagamentos().size());
        assertEquals(0, n("0.3").compareTo(r.resumo().totalPorAcao()));
        assertEquals(0, n("2").compareTo(r.eventos().getFirst().ajustePercentual()));
        assertEquals(0, n("10").compareTo(r.resumo().retornoPrecoPercentual()));
        assertFalse(r.resumo().coberturaCompleta()); assertNull(r.resumo().retornoComProventosPercentual());
        assertNull(r.resumo().contribuicaoProventosPontosPercentuais());
    }
    @Test void usaProximoPregaoAposFeriadoSemToleranciaMaisOuMenosTresDias() {
        var historico = List.of(vela("2026-06-03", "10", "10", null), vela("2026-06-05", "9.8", "10", "EJ"));
        var p = provento(ISIN, "JCP", "2026-06-03", "0.2", "2026-07-01");
        var r = ServicoProventosResumo.calcular("ITUB4", d("2026-06-03"), d("2026-06-05"), historico, ISIN, List.of(p));
        assertEquals(d("2026-06-05"), r.eventos().getFirst().dataEx());
        assertEquals(0, n("0.2").compareTo(r.eventos().getFirst().valorPorAcao()));
    }
    @Test void marcaCompostaSemDividendoOficialContinuaParcialEAjustePodeSerNegativo() {
        var historico = List.of(vela("2026-05-01", "10", "10", null), vela("2026-05-04", "11", "11", "EDJ"));
        var p = provento(ISIN, "JCP", "2026-05-01", "0.2", "2026-07-01");
        var r = ServicoProventosResumo.calcular("ITUB4", d("2026-05-01"), d("2026-05-04"), historico, ISIN, List.of(p));
        assertEquals(0, r.resumo().eventosComValor()); assertEquals(0, n("-10").compareTo(r.resumo().ajusteMedioPercentual()));
        assertTrue(r.resumo().limitacoes().stream().anyMatch(t -> t.contains("sem valor oficial")));
    }
    @Test void naoMonetarioNaoContaComoProventoNemRegularidade() {
        var historico = List.of(vela("2026-05-01", "10", "10", null), vela("2026-05-04", "5", "5", "EX"));
        var r = ServicoProventosResumo.calcular("ITUB4", d("2026-05-01"), d("2026-05-04"), historico, ISIN, List.of());
        assertEquals(1, r.eventos().size()); assertEquals(0, r.resumo().quantidadeEventos());
        assertNull(r.resumo().retornoComProventosPercentual());
        assertTrue(r.resumo().limitacoes().stream().anyMatch(t -> t.contains("societário")));
    }
    @Test void semIsinNemPrecoNaoInventaZero() {
        var r = ServicoProventosResumo.calcular("ITUB4", d("2026-05-01"), d("2026-05-04"), List.of(), null, List.of());
        assertNull(r.resumo().retornoPrecoPercentual()); assertNull(r.resumo().totalPorAcao());
        assertNull(r.resumo().intervaloMedioDias()); assertNull(r.resumo().ultimoEvento());
    }
    @Test void aceitaCombinacoesDoCotahistSemRepetirTipos() {
        for (String marca : List.of("EJED", "EJ+ED", "EJD")) {
            var historico = List.of(vela("2026-05-01", "10", "10", null), vela("2026-05-04", "10", "10", marca));
            var r = ServicoProventosResumo.calcular("ITUB4", d("2026-05-01"), d("2026-05-04"), historico, ISIN, List.of());
            assertEquals("EDJ", r.eventos().getFirst().marca());
            assertEquals("DIVIDENDO + JCP", r.eventos().getFirst().tipo());
        }
    }
    @Test void dataComOficialTemPrecedenciaSobreInicioInferidoDaSequencia() {
        var historico = List.of(vela("2026-05-01", "10", "10", null), vela("2026-05-04", "10", "10", "EJ"),
                vela("2026-05-05", "10", "10", "EJ"), vela("2026-05-06", "10", "10", "EJ"));
        var p = provento(ISIN, "JCP", "2026-05-04", "0.2", "2026-07-01");
        var r = ServicoProventosResumo.calcular("ITUB4", d("2026-05-01"), d("2026-05-06"), historico, ISIN, List.of(p));
        assertEquals(1, r.eventos().size()); assertEquals(d("2026-05-05"), r.eventos().getFirst().dataEx());
    }
    @Test void identidadeAusenteNoRepositorioNaoDerrubaServico() {
        var pregoes = mock(ServicoPregoes.class);
        var identidade = mock(br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivoF360.class);
        var proventos = mock(br.com.miranda.gestor.ativos.brutos.repository.RepositorioProventoDistribuido.class);
        when(pregoes.montar("ITUB4", ServicoProventosResumo.INICIO, d("2026-05-04"), "dia"))
                .thenReturn(new br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO("ITUB4", List.of("ITUB4"), "dia",
                        d("2016-01-01"), d("2026-05-04"), List.of(), null, List.of(), ""));
        when(proventos.findBySimboloOrderByDataPagamentoDesc("ITUB")).thenReturn(List.of());
        var r = new ServicoProventosResumo(pregoes, identidade, proventos).resumo("ITUB4", d("2026-05-01"), d("2026-05-04"));
        assertFalse(r.resumo().coberturaCompleta());
    }
    @Test void precedenciaOficialPreservaOutroTipoAindaSemVinculo() {
        var historico = List.of(vela("2026-05-01", "10", "10", null), vela("2026-05-04", "10", "10", "EDJ"),
                vela("2026-05-05", "10", "10", "EDJ"));
        var p = provento(ISIN, "JCP", "2026-05-04", "0.2", "2026-07-01");
        var r = ServicoProventosResumo.calcular("ITUB4", d("2026-05-01"), d("2026-05-05"), historico, ISIN, List.of(p));
        assertEquals("ED", r.eventos().getFirst().marca());
        assertNull(r.eventos().getFirst().valorPorAcao());
        assertEquals(2, r.resumo().quantidadeEventos());
    }
    @Test void primeiraLinhaMarcadaNaoProvaNovaDataEx() {
        var historico = List.of(vela("2026-05-04", "10", "10", "EJ"), vela("2026-05-05", "10", "10", "EJ"));
        var r = ServicoProventosResumo.calcular("ITUB4", d("2026-05-04"), d("2026-05-05"), historico, ISIN, List.of());
        assertTrue(r.eventos().isEmpty());
        assertTrue(r.resumo().limitacoes().stream().anyMatch(t -> t.contains("início dessa janela")));
    }
}
