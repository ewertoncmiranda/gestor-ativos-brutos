package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.ProventoDistribuidoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.DatasExDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDatasEx;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDatasEx.LinhaDataEx;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioProventoDistribuido;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicoDatasExTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);

    @Mock
    private RepositorioDatasEx repo;
    @Mock
    private RepositorioProventoDistribuido repoProventos;
    @InjectMocks
    private ServicoDatasEx servico;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static LinhaDataEx vivo(LocalDate d, String marca, String fech, String abert) {
        return new LinhaDataEx(d, marca, null, bd(fech), bd(abert), null, null, null, null);
    }

    private static ProventoDistribuidoEntity provento(String tipo, String valor, LocalDate dataCom) {
        var p = new ProventoDistribuidoEntity();
        p.setTipo(tipo);
        p.setValorPorAcao(bd(valor));
        p.setUltimaDataComDireito(dataCom);
        return p;
    }

    @Test
    void fallback_ao_vivo_calcula_ajuste_e_normaliza_simbolo() {
        when(repo.daTabela(eq("PETR4"), any())).thenReturn(List.of());
        when(repo.aoVivo(eq("PETR4"), any())).thenReturn(List.of(
                vivo(LocalDate.of(2026, 5, 4), "EJ", "10.00", "9.80"),
                vivo(LocalDate.of(2026, 6, 1), "EZ", "10.00", "10.00")));

        DatasExDTO r = servico.calendario(" petr4 ", null, HOJE);

        assertEquals("PETR4", r.simbolo());
        assertEquals("COTAHIST_AO_VIVO", r.origemDados());
        assertEquals(HOJE.minusMonths(24), r.desde());
        assertEquals(2, r.eventos().size());
        var e = r.eventos().get(0);
        assertEquals("JCP", e.tipo());
        assertEquals(0, bd("0.20").compareTo(e.ajusteImplicito()));
        assertEquals(0, bd("2.0000").compareTo(e.ajustePercentual()));
        assertNull(e.valorDeclarado());
        assertEquals("OUTRO", r.eventos().get(1).tipo());
    }

    @Test
    void fonte_primaria_usa_data_ex_b3_sem_ao_vivo() {
        var l = new LinhaDataEx(LocalDate.of(2026, 3, 10), "ED", "DIVIDENDO", bd("20"), bd("19.5"),
                bd("0.5"), bd("2.5"), bd("0.48"), "PROVENTO");
        when(repo.daTabela(eq("VALE3"), any())).thenReturn(List.of(l));

        DatasExDTO r = servico.calendario("VALE3", LocalDate.of(2026, 1, 1), HOJE);

        assertEquals("DATA_EX_B3", r.origemDados());
        verify(repo, never()).aoVivo(any(), any());
        var e = r.eventos().get(0);
        assertEquals(0, bd("0.48").compareTo(e.valorDeclarado()));
        assertEquals(0, bd("0.024").compareTo(e.yieldDoEvento()));
        assertEquals(1, r.resumo().quantidade12Meses());
    }

    @Test
    void junta_provento_por_data_ex_e_calcula_razao_e_yield() {
        when(repo.daTabela(any(), any())).thenReturn(List.of());
        when(repo.aoVivo(any(), any())).thenReturn(List.of(vivo(LocalDate.of(2026, 5, 4), "EJ", "10.00", "9.70")));
        when(repoProventos.findBySimboloOrderByDataPagamentoDesc("PETR")).thenReturn(List.of(
                provento("DIVIDENDO", "0.90", LocalDate.of(2026, 5, 3)),
                provento("JCP", "0.30", LocalDate.of(2026, 5, 2)),
                provento("JCP", "9.99", LocalDate.of(2026, 1, 2))));

        var e = servico.calendario("PETR4", null, HOJE).eventos().get(0);

        assertEquals(0, bd("0.30").compareTo(e.valorDeclarado()));
        assertEquals(0, bd("1.0000").compareTo(e.razaoAjuste()));
        assertEquals(0, bd("0.030000").compareTo(e.yieldDoEvento()));
    }

    @Test
    void proximaData_projeta_ocorrencia_do_ano_anterior() {
        when(repo.daTabela(any(), any())).thenReturn(List.of());
        when(repo.aoVivo(any(), any())).thenReturn(List.of(
                vivo(LocalDate.of(2024, 12, 10), "ED", "10", "9.9"),
                vivo(LocalDate.of(2025, 12, 12), "ED", "10", "9.9"),
                vivo(LocalDate.of(2026, 4, 10), "ED", "10", "9.9")));

        var r = servico.calendario("ITSA4", null, HOJE).resumo();

        assertEquals(LocalDate.of(2026, 12, 10), r.proximaDataEstimada());
        assertEquals("DIVIDENDO", r.proximoTipo());
        assertEquals("MEDIA", r.confianca());
        assertNotNull(r.leitura());
    }

    @Test
    void proximaData_baixa_confianca_com_uma_ocorrencia() {
        when(repo.daTabela(any(), any())).thenReturn(List.of());
        when(repo.aoVivo(any(), any())).thenReturn(List.of(vivo(LocalDate.of(2025, 6, 10), "ED", "10", "9.9")));

        var r = servico.calendario("ITSA4", null, HOJE).resumo();

        assertEquals(LocalDate.of(2027, 6, 10), r.proximaDataEstimada());
        assertEquals("BAIXA", r.confianca());
    }

    @Test
    void sem_historico_de_12_meses_nao_estima() {
        when(repo.daTabela(any(), any())).thenReturn(List.of());
        when(repo.aoVivo(any(), any())).thenReturn(List.of(vivo(LocalDate.of(2026, 6, 10), "ED", "10", "9.9")));

        var r = servico.calendario("ITSA4", null, HOJE).resumo();

        assertNull(r.proximaDataEstimada());
        assertNull(r.confianca());
    }

    @Test
    void ativo_sem_eventos_retorna_lista_vazia() {
        when(repo.daTabela(any(), any())).thenReturn(List.of());
        when(repo.aoVivo(any(), any())).thenReturn(List.of());

        DatasExDTO r = servico.calendario("XXXX3", null, HOJE);

        assertTrue(r.eventos().isEmpty());
        assertEquals(0, r.resumo().quantidade12Meses());
        assertNull(r.resumo().proximaDataEstimada());
        verifyNoInteractions(repoProventos);
    }

    @Test
    void marca_repetida_em_pregoes_seguidos_vira_um_evento_no_primeiro_dia() {
        when(repo.daTabela(any(), any())).thenReturn(List.of());
        when(repo.aoVivo(any(), any())).thenReturn(List.of(
                vivo(LocalDate.of(2026, 8, 24), "EDJ", "44.30", "42.60"),
                vivo(LocalDate.of(2026, 8, 25), "EDJ", "42.11", "41.18"),
                vivo(LocalDate.of(2026, 8, 28), "EDJ", "42.70", "42.72"),
                vivo(LocalDate.of(2026, 8, 31), "EDJ", "43.55", "44.62"),
                vivo(LocalDate.of(2026, 9, 2), "EDJ", "46.87", "46.82")));

        var r = servico.calendario("PETR4", null, HOJE);

        assertEquals(1, r.eventos().size());
        assertEquals(LocalDate.of(2026, 8, 24), r.eventos().get(0).dataEx());
    }

    @Test
    void mapa_de_marcas() {
        assertEquals("BONIFICACAO", ServicoDatasEx.tipoDaMarca("EB"));
        assertEquals("DIVIDENDO", ServicoDatasEx.tipoDaMarca("EDJ"));
        assertEquals("DESDOBRAMENTO", ServicoDatasEx.tipoDaMarca("EX"));
        assertEquals("OUTRO", ServicoDatasEx.tipoDaMarca("EZ"));
    }
}
