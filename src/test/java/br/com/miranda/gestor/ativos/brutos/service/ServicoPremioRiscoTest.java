package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.CurvaRealDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.PremioRiscoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioTitulosPublicos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicoPremioRiscoTest {

    private static final LocalDate BASE = LocalDate.of(2026, 10, 16);
    private RepositorioTitulosPublicos repo;
    private ServicoPremioRisco servico;

    private static CurvaRealDTO.Ponto p(int ano, String taxa) {
        return new CurvaRealDTO.Ponto("Tesouro IPCA+", LocalDate.of(ano, 8, 15), new BigDecimal(taxa), new BigDecimal(taxa));
    }

    @BeforeEach
    void setUp() {
        repo = mock(RepositorioTitulosPublicos.class);
        servico = new ServicoPremioRisco(repo);
        when(repo.schemaDisponivel()).thenReturn(true);
        when(repo.dataBaseMaisRecente()).thenReturn(Optional.of(BASE));
        when(repo.ntnbPorVencimento(BASE)).thenReturn(List.of(p(2029, "7.50"), p(2032, "6.79"), p(2035, "6.60"), p(2045, "6.70")));
        when(repo.selicMeta()).thenReturn(Optional.of(new BigDecimal("14.50")));
    }

    @Test
    void referencia_e_o_vencimento_mais_perto_de_5_anos() {
        CurvaRealDTO c = servico.curvaReal();
        assertEquals(LocalDate.of(2032, 8, 15), c.referencia().vencimento());
        assertEquals(4, c.vencimentos().size());
        assertEquals(BASE, c.dataBase());
    }

    @Test
    void empate_fica_com_o_vencimento_menor() {
        LocalDate alvo = BASE.plusYears(5);
        var a = new CurvaRealDTO.Ponto("t", alvo.minusDays(10), BigDecimal.ONE, null);
        var b = new CurvaRealDTO.Ponto("t", alvo.plusDays(10), BigDecimal.ONE, null);
        assertEquals(a, ServicoPremioRisco.referencia(BASE, List.of(b, a)).orElseThrow());
    }

    @Test
    void premio_positivo() {
        when(repo.earningsYield("PETR4")).thenReturn(Optional.of(new BigDecimal("11.2")));
        PremioRiscoDTO d = servico.premioRisco("petr4");
        assertEquals(0, new BigDecimal("4.41").compareTo(d.premioRisco()));
        assertEquals("ACIMA_DA_RENDA_FIXA", d.veredito());
        assertEquals("Earnings yield de 11,2% contra 6,79% da NTN-B 2032: prêmio de 4,4 p.p.", d.leitura());
        assertEquals(0, new BigDecimal("-3.30").compareTo(d.premioSobreSelic()));
        assertNull(d.motivo());
    }

    @Test
    void premio_negativo() {
        when(repo.earningsYield("VALE3")).thenReturn(Optional.of(new BigDecimal("5.0")));
        PremioRiscoDTO d = servico.premioRisco("VALE3");
        assertEquals("ABAIXO_DA_RENDA_FIXA", d.veredito());
        assertTrue(d.premioRisco().signum() < 0);
        assertTrue(d.leitura().contains("prêmio negativo de 1,8 p.p."));
    }

    @Test
    void sem_insight_devolve_nulos_com_motivo() {
        when(repo.earningsYield("XXXX3")).thenReturn(Optional.empty());
        PremioRiscoDTO d = servico.premioRisco("XXXX3");
        assertNull(d.premioRisco());
        assertNull(d.veredito());
        assertNotNull(d.motivo());
        assertEquals(0, new BigDecimal("6.79").compareTo(d.juroReal()));
    }

    @Test
    void tabela_vazia_devolve_nulos_com_motivo() {
        when(repo.dataBaseMaisRecente()).thenReturn(Optional.empty());
        when(repo.earningsYield("PETR4")).thenReturn(Optional.of(BigDecimal.TEN));
        PremioRiscoDTO d = servico.premioRisco("PETR4");
        assertNull(d.premioRisco());
        assertNotNull(d.motivo());
    }

    @Test
    void sem_v45_devolve_503() {
        when(repo.schemaDisponivel()).thenReturn(false);
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> servico.premioRisco("PETR4")).getStatusCode().value());
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> servico.curvaReal()).getStatusCode().value());
    }
}
