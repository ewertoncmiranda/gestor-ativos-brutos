package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.BancoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.FundamentosCvmDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioBancoIndicadores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicoBancoTest {

    RepositorioBancoIndicadores repo;
    ServicoFundamentosCvm fund;
    ServicoBanco servico;

    @BeforeEach
    void setup() {
        repo = mock(RepositorioBancoIndicadores.class);
        fund = mock(ServicoFundamentosCvm.class);
        servico = new ServicoBanco(repo, fund);
        when(repo.schemaDisponivel()).thenReturn(true);
        when(repo.cnpjDoSimbolo("ITUB4")).thenReturn(Optional.of("60872504000123"));
    }

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    List<BancoDTO.Trimestre> trimestres(String basileia, String inadAtual) {
        List<BancoDTO.Trimestre> l = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            l.add(new BancoDTO.Trimestre(LocalDate.of(2026, 6, 30).minusMonths(3L * i), d(basileia), d("12.0"),
                    d(i == 0 ? inadAtual : "3.0"), d("20.0")));
        }
        return l;
    }

    void bancoComDados(String basileia, String inadAtual) {
        when(repo.setorCvm("60872504000123")).thenReturn(Optional.of("Emp. Adm. Part. - Bancos"));
        when(repo.ultimosTrimestres("60872504000123", 8)).thenReturn(trimestres(basileia, inadAtual));
        when(repo.selicMeta()).thenReturn(Optional.of(d("14.75")));
        FundamentosCvmDTO f = FundamentosCvmDTO.vazio("ITUB4");
        f.setPrecoValorPatrimonial(d("2.00"));
        when(fund.buscarPorSimbolo("ITUB4")).thenReturn(f);
    }

    @Test
    void naoBancoRetornaFalseSemErro() {
        when(repo.cnpjDoSimbolo("VALE3")).thenReturn(Optional.of("33592510000154"));
        when(repo.setorCvm("33592510000154")).thenReturn(Optional.of("Extrativa Mineral"));
        BancoDTO r = servico.buscar("vale3");
        assertFalse(r.ehBanco());
        assertNull(r.serie());
        assertNotNull(r.motivo());
    }

    @Test
    void bancoComDadosCalculaSerieEPvp() {
        bancoComDados("15.0", "3.2");
        BancoDTO r = servico.buscar("ITUB4");
        assertTrue(r.ehBanco());
        assertEquals(8, r.serie().size());
        assertTrue(r.serie().get(0).dataBase().isBefore(r.serie().get(7).dataBase()));
        assertEquals(d("10.0000"), r.roeSobrePvp());
        assertEquals("OK", r.selos().get(0).status());
        assertEquals("OK", r.selos().get(1).status());
        assertEquals("ACIMA", r.selos().get(2).status());
    }

    @Test
    void bancoSemIfDataTemMotivo() {
        when(repo.setorCvm("60872504000123")).thenReturn(Optional.of("Bancos"));
        when(repo.ultimosTrimestres("60872504000123", 8)).thenReturn(List.of());
        when(fund.buscarPorSimbolo("ITUB4")).thenReturn(FundamentosCvmDTO.vazio("ITUB4"));
        BancoDTO r = servico.buscar("ITUB4");
        assertTrue(r.ehBanco());
        assertNull(r.ultimoTrimestre());
        assertNull(r.selos());
        assertNotNull(r.motivo());
        assertNotNull(r.pvpMotivo());
    }

    @Test
    void basileiaAbaixoDoMinimoGeraAlerta() {
        bancoComDados("10.2", "4.0");
        BancoDTO r = servico.buscar("ITUB4");
        assertEquals("ALERTA", r.selos().get(0).status());
        assertEquals("ALERTA", r.selos().get(1).status());
        assertTrue(r.leitura().contains("2 selos"));
    }

    @Test
    void tabelaAusenteDa503() {
        when(repo.schemaDisponivel()).thenReturn(false);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servico.buscar("ITUB4"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }
}
