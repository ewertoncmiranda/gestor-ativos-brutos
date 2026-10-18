package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraAtivoRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.CriarCarteiraRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.ImportacaoCarteirasRequest;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCarteiras;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServicoCarteirasTest {
    private RepositorioCarteiras repositorio;
    private ServicoCarteiras servico;

    @BeforeEach
    void setUp() {
        repositorio = mock(RepositorioCarteiras.class);
        servico = new ServicoCarteiras(repositorio);
        when(repositorio.schemaDisponivel()).thenReturn(true);
    }

    @Test
    void criaCarteiraComIdGeradoPeloServidor() {
        when(repositorio.buscar(anyString())).thenAnswer(inv -> Optional.of(new CarteiraDTO(
                inv.getArgument(0), "Longo prazo", "FUNDAMENTOS", "#369", "BRL", "ATIVA", null, List.of())));
        var criada = servico.criar(new CriarCarteiraRequest("Longo prazo", "FUNDAMENTOS", "#369", "BRL"));

        assertNotNull(criada.id());
        assertEquals("Longo prazo", criada.nome());
        verify(repositorio).criar(eq(criada.id()), eq("Longo prazo"), eq("FUNDAMENTOS"), eq("#369"), eq("BRL"));
    }

    @Test
    void naoPermiteAtivoComCapitalNuloOuNegativo() {
        when(repositorio.buscar("carteira-1")).thenReturn(Optional.of(carteira("carteira-1", "ATIVA")));
        assertThrows(ResponseStatusException.class,
                () -> servico.salvarAtivo("carteira-1", "PETR4", new CarteiraAtivoRequest(LocalDate.now(), BigDecimal.ZERO)));
        verify(repositorio, never()).salvarAtivo(anyString(), anyString(), any(), any());
    }

    @Test
    void alocacaoPersistidaFicaExplicitamenteMarcadaComoSimuladaLegada() {
        when(repositorio.buscar("carteira-1")).thenReturn(Optional.of(carteira("carteira-1", "ATIVA")));
        when(repositorio.listarAtivos("carteira-1")).thenReturn(List.of(new CarteiraDTO.AtivoDTO(
                "PETR4", LocalDate.of(2025, 1, 2), new BigDecimal("1000.00"), "ALOCACAO_SIMULADA_LEGADA")));

        var ativo = servico.salvarAtivo("carteira-1", "petr4", new CarteiraAtivoRequest(
                LocalDate.of(2025, 1, 2), new BigDecimal("1000.00")));

        assertEquals("ALOCACAO_SIMULADA_LEGADA", ativo.naturezaCapital());
        verify(repositorio).salvarAtivo("carteira-1", "PETR4", LocalDate.of(2025, 1, 2), new BigDecimal("1000.00"));
    }

    @Test
    void semMigrationApiFalhaComoIndisponivelEmVezDeDeclararSucesso() {
        when(repositorio.schemaDisponivel()).thenReturn(false);
        var erro = assertThrows(ResponseStatusException.class, servico::listar);
        assertEquals(503, erro.getStatusCode().value());
    }

    @Test
    void carteiraArquivadaNaoPermiteRemoverAlocacao() {
        when(repositorio.buscar("carteira-1")).thenReturn(Optional.of(carteira("carteira-1", "ARQUIVADA")));
        assertThrows(ResponseStatusException.class, () -> servico.removerAtivo("carteira-1", "PETR4"));
        verify(repositorio, never()).removerAtivo(anyString(), anyString());
    }

    @Test
    void importaStoreLocalPreservandoIdEAlocacaoSemCriarOperacao() {
        when(repositorio.buscar("local-1")).thenReturn(Optional.empty());

        var recibo = servico.importar(payload("Carteira", "PETR4", "1000.00"));

        assertEquals(1, recibo.importadas());
        assertEquals("local-1", recibo.idsCarteira().get("local-1"));
        assertEquals(64, recibo.hashConteudo().length());
        verify(repositorio).criarImportada("local-1", "Carteira", "BALANCEADA", "#369", "BRL", null);
        verify(repositorio).salvarAtivo("local-1", "PETR4", LocalDate.of(2025, 1, 2), new BigDecimal("1000.00"));
        verify(repositorio, never()).criar(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void importacaoRepetidaComConteudoNormalizadoIgualRetornaReciboSemGravarNovamente() {
        when(repositorio.buscar("local-1")).thenReturn(Optional.of(new CarteiraDTO(
                "local-1", "Carteira", "BALANCEADA", "#369", "BRL", "ATIVA", null, List.of())));
        when(repositorio.listarAtivos("local-1")).thenReturn(List.of(new CarteiraDTO.AtivoDTO(
                "PETR4", LocalDate.of(2025, 1, 2), new BigDecimal("1000.00"), "ALOCACAO_SIMULADA_LEGADA")));

        var recibo = servico.importar(payload(" Carteira ", "petr4", "1000"));

        assertEquals(0, recibo.importadas());
        assertEquals(1, recibo.jaExistentes());
        assertEquals("local-1", recibo.idsCarteira().get("local-1"));
        verify(repositorio, never()).criarImportada(anyString(), anyString(), anyString(), anyString(), anyString(), any());
        verify(repositorio, never()).salvarAtivo(anyString(), anyString(), any(), any());
    }

    @Test
    void importacaoComMesmoIdMasConteudoDiferenteRespondeConflitoSemSobrescrever() {
        when(repositorio.buscar("local-1")).thenReturn(Optional.of(new CarteiraDTO(
                "local-1", "Outra carteira", "BALANCEADA", "#369", "BRL", "ATIVA", null, List.of())));

        var erro = assertThrows(ResponseStatusException.class, () -> servico.importar(payload("Carteira", "PETR4", "1000")));

        assertEquals(409, erro.getStatusCode().value());
        verify(repositorio, never()).atualizar(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(repositorio, never()).criarImportada(anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private ImportacaoCarteirasRequest payload(String nome, String simbolo, String capital) {
        return new ImportacaoCarteirasRequest(List.of(new ImportacaoCarteirasRequest.CarteiraLegada(
                "local-1", nome, "BALANCEADA", "#369", null,
                List.of(new ImportacaoCarteirasRequest.AtivoLegado(simbolo, LocalDate.of(2025, 1, 2), new BigDecimal(capital))))));
    }

    private CarteiraDTO carteira(String id, String status) {
        return new CarteiraDTO(id, "Teste", "ESTRATEGIA", "#369", "BRL", status, null, List.of());
    }
}
