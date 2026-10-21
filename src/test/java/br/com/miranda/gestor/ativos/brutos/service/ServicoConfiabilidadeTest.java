package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.ConfiabilidadeDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.ConfiabilidadeDTO.Nivel;
import br.com.miranda.gestor.ativos.brutos.external.dto.ConfiabilidadeDTO.Selo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.Auditor;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.Cadastro;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.CompetenciaFundos;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.Divida;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.MovimentoFundos;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.Proventos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServicoConfiabilidadeTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);
    private RepositorioConfiabilidade repo;
    private ServicoConfiabilidade servico;

    @BeforeEach
    void setUp() {
        repo = mock(RepositorioConfiabilidade.class);
        servico = new ServicoConfiabilidade(repo,
                Clock.fixed(HOJE.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC")));
        when(repo.tabelasV45Existentes()).thenReturn(new HashSet<>(RepositorioConfiabilidade.TABELAS_V45));
        when(repo.cnpjPorSimbolo("PETR4")).thenReturn(Optional.of("33000167000101"));
    }

    private static Selo selo(ConfiabilidadeDTO c, String id) {
        return c.selos().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void cadastroAtivoOk() {
        when(repo.cadastro("1")).thenReturn(Optional.of(new Cadastro("ATIVO", HOJE, null, null)));
        assertEquals(Nivel.OK, servico.cadastro("1").nivel());
    }

    @Test
    void cadastroSuspensoECanceladoCriticosComMotivo() {
        when(repo.cadastro("1")).thenReturn(Optional.of(new Cadastro("SUSPENSO(A) - DECISÃO ADM", HOJE, null, null)));
        assertEquals(Nivel.CRITICO, servico.cadastro("1").nivel());
        when(repo.cadastro("2")).thenReturn(Optional.of(
                new Cadastro("CANCELADA", HOJE.minusYears(1), HOJE.minusDays(5), "Incorporação")));
        Selo s = servico.cadastro("2");
        assertEquals(Nivel.CRITICO, s.nivel());
        assertTrue(s.detalhe().contains("Incorporação") && s.detalhe().contains(HOJE.minusDays(5).toString()));
    }

    @Test
    void cadastroAusenteSemDado() {
        when(repo.cadastro("1")).thenReturn(Optional.empty());
        assertEquals(Nivel.SEM_DADO, servico.cadastro("1").nivel());
    }

    @Test
    void auditorTrocaRecenteAlertaComJustificativa() {
        when(repo.auditores("1")).thenReturn(List.of(
                new Auditor("B", "Beta Audit", HOJE.minusMonths(6), null, "Rodízio obrigatório"),
                new Auditor("A", "Alfa Audit", HOJE.minusYears(5), HOJE.minusMonths(6), null)));
        Selo s = servico.auditor("1", HOJE);
        assertEquals(Nivel.ALERTA, s.nivel());
        assertTrue(s.detalhe().contains("Beta Audit") && s.detalhe().contains("Alfa Audit")
                && s.detalhe().contains("Rodízio obrigatório"));
    }

    @Test
    void auditorTrocaAntigaOuSemTrocaOk() {
        when(repo.auditores("1")).thenReturn(List.of(
                new Auditor("B", "Beta", HOJE.minusMonths(30), null, "x"),
                new Auditor("A", "Alfa", HOJE.minusYears(6), null, null)));
        assertEquals(Nivel.OK, servico.auditor("1", HOJE).nivel());
        when(repo.auditores("2")).thenReturn(List.of(new Auditor("A", "Alfa", HOJE.minusMonths(2), null, null)));
        assertEquals(Nivel.OK, servico.auditor("2", HOJE).nivel());
        when(repo.auditores("3")).thenReturn(List.of());
        assertEquals(Nivel.SEM_DADO, servico.auditor("3", HOJE).nivel());
    }

    @Test
    void dividaAcimaDoLimiarAlertaEAbaixoOk() {
        when(repo.divida("1", HOJE)).thenReturn(Optional.of(
                new Divida(HOJE, 3, new BigDecimal("1000"), new BigDecimal("600"))));
        Selo s = servico.divida("1", HOJE);
        assertEquals(Nivel.ALERTA, s.nivel());
        assertTrue(s.detalhe().contains("60.0%"));
        when(repo.divida("2", HOJE)).thenReturn(Optional.of(
                new Divida(HOJE, 3, new BigDecimal("1000"), new BigDecimal("500"))));
        assertEquals(Nivel.OK, servico.divida("2", HOJE).nivel());
        when(repo.divida("3", HOJE)).thenReturn(Optional.empty());
        assertEquals(Nivel.SEM_DADO, servico.divida("3", HOJE).nivel());
    }

    @Test
    void fundosInformativoComVariacaoEDefasagem() {
        LocalDate atual = LocalDate.of(2026, 8, 1);
        LocalDate ant = LocalDate.of(2026, 7, 1);
        when(repo.competenciasFundos("PETR4")).thenReturn(List.of(
                new CompetenciaFundos(atual, 12, new BigDecimal("5000")),
                new CompetenciaFundos(ant, 10, new BigDecimal("4000"))));
        when(repo.movimentoFundos("PETR4", atual, ant)).thenReturn(new MovimentoFundos(3, 1));
        Selo s = servico.fundos("PETR4", HOJE);
        assertEquals(Nivel.OK, s.nivel());
        assertTrue(s.detalhe().contains("12 fundo(s)"));
        assertTrue(s.detalhe().contains("3 entraram, 1 saíram"));
        assertTrue(s.detalhe().contains("fundos +2"));
        assertTrue(s.detalhe().contains("quantidade +1000"));
        assertTrue(s.detalhe().contains("defasagem de 2 mês"));
    }

    @Test
    void fundosSemLinhasSemDado() {
        when(repo.competenciasFundos("PETR4")).thenReturn(List.of());
        assertEquals(Nivel.SEM_DADO, servico.fundos("PETR4", HOJE).nivel());
    }

    @Test
    void dividendoPayoutAcimaDe100Alerta() {
        when(repo.proventosEPayout("PETR4")).thenReturn(Optional.of(
                new Proventos(LocalDate.of(2025, 12, 31), new BigDecimal("120"), new BigDecimal("100"))));
        Selo s = servico.dividendo("PETR4");
        assertEquals(Nivel.ALERTA, s.nivel());
        assertTrue(s.detalhe().contains("120.0%"));
    }

    @Test
    void dividendoPayoutNormalOkESemDado() {
        when(repo.proventosEPayout("PETR4")).thenReturn(Optional.of(
                new Proventos(LocalDate.of(2025, 12, 31), new BigDecimal("50"), new BigDecimal("100"))));
        assertEquals(Nivel.OK, servico.dividendo("PETR4").nivel());
        when(repo.proventosEPayout("X")).thenReturn(Optional.empty());
        assertEquals(Nivel.SEM_DADO, servico.dividendo("X").nivel());
        when(repo.proventosEPayout("Y")).thenReturn(Optional.of(
                new Proventos(LocalDate.of(2025, 12, 31), new BigDecimal("50"), null)));
        assertEquals(Nivel.SEM_DADO, servico.dividendo("Y").nivel());
    }

    @Test
    void resumoPegaPiorNivel() {
        when(repo.cadastro(any())).thenReturn(Optional.of(new Cadastro("CANCELADA", null, null, null)));
        when(repo.auditores(any())).thenReturn(List.of());
        when(repo.divida(any(), any())).thenReturn(Optional.empty());
        when(repo.competenciasFundos(any())).thenReturn(List.of());
        when(repo.proventosEPayout(any())).thenReturn(Optional.empty());
        ConfiabilidadeDTO c = servico.confiabilidade("petr4");
        assertEquals(Nivel.CRITICO, c.resumo().nivel());
        assertTrue(c.resumo().leitura().contains("CADASTRO"));
        assertEquals(5, c.selos().size());
        assertEquals("33000167000101", c.cnpj());
    }

    @Test
    void resumoAlertaQuandoSoAlertaEOk() {
        when(repo.cadastro(any())).thenReturn(Optional.of(new Cadastro("ATIVO", null, null, null)));
        when(repo.auditores(any())).thenReturn(List.of());
        when(repo.divida(any(), any())).thenReturn(Optional.empty());
        when(repo.competenciasFundos(any())).thenReturn(List.of());
        when(repo.proventosEPayout(any())).thenReturn(Optional.of(
                new Proventos(LocalDate.of(2025, 12, 31), new BigDecimal("200"), new BigDecimal("100"))));
        assertEquals(Nivel.ALERTA, servico.confiabilidade("PETR4").resumo().nivel());
    }

    @Test
    void tabelaAusenteViraSemDadoSemQuebrar() {
        when(repo.tabelasV45Existentes()).thenReturn(new HashSet<>(List.of("cvm_cadastro")));
        when(repo.cadastro(any())).thenReturn(Optional.of(new Cadastro("ATIVO", null, null, null)));
        when(repo.proventosEPayout(any())).thenReturn(Optional.empty());
        ConfiabilidadeDTO c = servico.confiabilidade("PETR4");
        assertEquals(Nivel.SEM_DADO, selo(c, "AUDITOR").nivel());
        assertTrue(selo(c, "AUDITOR").detalhe().contains("cvm_auditor"));
        assertEquals(Nivel.OK, c.resumo().nivel());
        verify(repo, never()).auditores(any());
    }

    @Test
    void semV45Responde503() {
        when(repo.tabelasV45Existentes()).thenReturn(new HashSet<>());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servico.confiabilidade("PETR4"));
        assertEquals(503, e.getStatusCode().value());
    }

    @Test
    void simboloDesconhecido404() {
        when(repo.cnpjPorSimbolo("ZZZZ3")).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servico.confiabilidade("zzzz3"));
        assertEquals(404, e.getStatusCode().value());
    }
}
