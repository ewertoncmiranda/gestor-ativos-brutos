package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.CargaDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteEtl;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCargas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServicoCargasTest {

    private final ObjectMapper json = new ObjectMapper();
    private RepositorioCargas repositorio;
    private ClienteEtl etl;
    private ServicoCargas servico;

    @BeforeEach
    void setUp() {
        repositorio = mock(RepositorioCargas.class);
        etl = mock(ClienteEtl.class);
        servico = new ServicoCargas(repositorio, etl);
        when(repositorio.schemaDisponivel()).thenReturn(true);
    }

    private static HttpStatus status(ResponseStatusException e) {
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    // --- leitura (CARGA-GES-1) --------------------------------------------------------

    @Test
    void semV44NoBancoRespondeServicoIndisponivelEmTodaLeitura() {
        when(repositorio.schemaDisponivel()).thenReturn(false);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, status(assertThrows(ResponseStatusException.class, () -> servico.listar(null, null, null))));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, status(assertThrows(ResponseStatusException.class, () -> servico.ultimas())));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, status(assertThrows(ResponseStatusException.class, () -> servico.buscar("x"))));
        verify(repositorio, never()).listar(anyInt(), any(), any());
    }

    @Test
    void listarUsaLimitePadraoENormalizaStatus() {
        servico.listar(null, " falha ", null);
        verify(repositorio).listar(ServicoCargas.LIMITE_PADRAO, "FALHA", null);
    }

    @Test
    void listarRecusaLimiteForaDaFaixaEStatusDesconhecido() {
        assertEquals(HttpStatus.BAD_REQUEST, status(assertThrows(ResponseStatusException.class, () -> servico.listar(0, null, null))));
        assertEquals(HttpStatus.BAD_REQUEST, status(assertThrows(ResponseStatusException.class, () -> servico.listar(201, null, null))));
        assertEquals(HttpStatus.BAD_REQUEST, status(assertThrows(ResponseStatusException.class, () -> servico.listar(10, "OK", null))));
    }

    @Test
    void buscarInexistenteRespondeNaoEncontrado() {
        when(repositorio.buscar("abc")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, status(assertThrows(ResponseStatusException.class, () -> servico.buscar("abc"))));
    }

    // --- disparo (CARGA-GES-2) --------------------------------------------------------

    @Test
    void pedidoValidoVaiAoEtlEmSnakeCaseComoPedidoDoPainel() {
        var corpo = servico.validar(new CargaDTO.Disparo(" parcial ", List.of(2025, 2024, 2025),
                List.of("petr4", "VALE3", "PETR4"), List.of("cotahist", "TTM"), true));
        assertEquals(Map.of("tipo", "PARCIAL", "anos", List.of(2024, 2025), "simbolos", List.of("PETR4", "VALE3"),
                "etapas", List.of("COTAHIST", "TTM"), "forcar", true, "solicitada_por", "PAINEL"), corpo);
    }

    @Test
    void rotinaSemParametrosLevaSoTipoESolicitante() {
        assertEquals(Map.of("tipo", "ROTINA", "solicitada_por", "PAINEL"),
                servico.validar(new CargaDTO.Disparo("ROTINA", null, null, null, false)));
    }

    @Test
    void pedidosInvalidosNaoChegamAoEtl() {
        int anoFuturo = Year.now().getValue() + 1;
        List<CargaDTO.Disparo> invalidos = List.of(
                new CargaDTO.Disparo(null, null, null, null, null),
                new CargaDTO.Disparo("DIARIA", null, null, null, null),
                new CargaDTO.Disparo("PARCIAL", null, null, null, null),
                new CargaDTO.Disparo("COMPLETA", List.of(2015), null, null, null),
                new CargaDTO.Disparo("COMPLETA", List.of(anoFuturo), null, null, null),
                new CargaDTO.Disparo("PARCIAL", null, List.of("PETR4; DROP"), null, null),
                new CargaDTO.Disparo("PARCIAL", null, null, List.of("BRAPI"), null));
        for (var pedido : invalidos) {
            var erro = assertThrows(ResponseStatusException.class, () -> servico.disparar(pedido), pedido.toString());
            assertEquals(HttpStatus.BAD_REQUEST, status(erro));
        }
        verifyNoInteractions(etl);
    }

    @Test
    void aceitoPeloEtlVolta202ComIdEStatus() throws Exception {
        when(etl.disparar(any())).thenReturn(new ClienteEtl.Resposta(202,
                json.readTree("{\"id\":\"c-1\",\"status\":\"PENDENTE\"}")));
        var resposta = servico.disparar(new CargaDTO.Disparo("ROTINA", null, null, null, null));
        assertEquals(202, resposta.status());
        assertEquals(Map.of("id", "c-1", "status", "PENDENTE"), resposta.corpo());
    }

    @Test
    void cargaJaRodandoVolta409ComAExecucaoEmCamelCase() throws Exception {
        when(etl.disparar(any())).thenReturn(new ClienteEtl.Resposta(409,
                json.readTree("{\"execucao_em_andamento\":\"c-9\"}")));
        var resposta = servico.disparar(new CargaDTO.Disparo("COMPLETA", null, null, null, null));
        assertEquals(409, resposta.status());
        assertEquals(Map.of("execucaoEmAndamento", "c-9"), resposta.corpo());
    }

    @Test
    void etlForaDoArVira503() {
        when(etl.disparar(any())).thenThrow(new ClienteEtl.EtlIndisponivel("recusado", null));
        var erro = assertThrows(ResponseStatusException.class,
                () -> servico.disparar(new CargaDTO.Disparo("ROTINA", null, null, null, null)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, status(erro));
    }

    @Test
    void camelCaseDeSnakeCase() {
        assertEquals("execucaoEmAndamento", ServicoCargas.camelCase("execucao_em_andamento"));
        assertEquals("id", ServicoCargas.camelCase("id"));
    }
}
