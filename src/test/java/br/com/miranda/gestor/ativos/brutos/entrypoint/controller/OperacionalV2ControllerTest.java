package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.exceptions.handler.TratadorGlobalExcecoes;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacionalV2;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacionalV2.Snapshot;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacionalV2.TimelineLinha;
import br.com.miranda.gestor.ativos.brutos.service.ServicoOperacionalV2;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OPRV2-GES-6: contrato minimo das rotas V2 e regressao de leitura pura. A escrita do snapshot/timeline fica
 * em infra/gerar-insights; aqui apenas SELECT via repositorio.
 */
class OperacionalV2ControllerTest {

    private static final String V = "OPR-2026.10.10-1";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static MockMvc mvc(RepositorioOperacionalV2 repo) {
        return MockMvcBuilders.standaloneSetup(new OperacionalV2Controller(new ServicoOperacionalV2(repo)))
                .setControllerAdvice(new TratadorGlobalExcecoes())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @Test
    void rotasV2ExpoemCardsDetalheConsensoTeseTimelineFiltrosEAlertas() throws Exception {
        var repo = repoComDados();
        MockMvc mvc = mvc(repo);

        mvc.perform(get("/operacional/tickers").param("status", "paper_trading_elegivel")
                        .param("liquidez", "alta").param("pagina", "0").param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("SIMULADO"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].simbolo").value("PETR4"))
                .andExpect(jsonPath("$.itens[0].statusOperacional").value("PAPER_TRADING_ELEGIVEL"))
                .andExpect(jsonPath("$.itens[0].sinalPrincipal").value("COMPRA"));

        mvc.perform(get("/operacional/tickers/PETR4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consenso.regraPrincipal").value("COMPRA"))
                .andExpect(jsonPath("$.tese.positivos[0]").value("Liquidez alta"));

        mvc.perform(get("/operacional/tickers/PETR4/consenso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consenso.regraPrincipal").value("COMPRA"));

        mvc.perform(get("/operacional/tickers/PETR4/tese"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tese.riscos[0]").value("Evento CVM"));

        mvc.perform(get("/operacional/tickers/PETR4/timeline").param("severidade", "atencao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].eventId").value("evt-1"))
                .andExpect(jsonPath("$.itens[0].titulo").value("Ticker deixou de ser elegível"));

        mvc.perform(get("/operacional/filtros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusOperacional[0].valor").value("PAPER_TRADING_ELEGIVEL"))
                .andExpect(jsonPath("$.faixaLiquidez[0].valor").value("ALTA"));

        mvc.perform(get("/operacional/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].severidade").value("ATENCAO"))
                .andExpect(jsonPath("$.itens[0].ativo").value(true));
        verify(repo, never()).alertas(anyInt());
    }

    @Test
    void semTabelasV2Responde200ComEstruturaVaziaEAviso() throws Exception {
        var repo = mock(RepositorioOperacionalV2.class);
        when(repo.snapshotsRecentes()).thenReturn(List.of());
        when(repo.timeline(Mockito.eq("PETR4"), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), anyInt()))
                .thenReturn(List.of());
        when(repo.alertas(anyInt())).thenReturn(List.of());
        MockMvc mvc = mvc(repo);

        mvc.perform(get("/operacional/tickers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty())
                .andExpect(jsonPath("$.avisos[0]").isString());
        mvc.perform(get("/operacional/tickers/PETR4")).andExpect(status().isOk())
                .andExpect(jsonPath("$.simbolo").value("PETR4"))
                .andExpect(jsonPath("$.avisos[0]").isString());
        mvc.perform(get("/operacional/tickers/PETR4/timeline")).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty())
                .andExpect(jsonPath("$.avisos[0]").isString());
        mvc.perform(get("/operacional/filtros")).andExpect(status().isOk())
                .andExpect(jsonPath("$.statusOperacional").isEmpty());
        mvc.perform(get("/operacional/alertas")).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty());
    }

    @Test
    void parametrosInvalidosRespondem400() throws Exception {
        MockMvc mvc = mvc(repoComDados());

        mvc.perform(get("/operacional/tickers/@@@")).andExpect(status().isBadRequest());
        mvc.perform(get("/operacional/tickers/PETR4/timeline").param("inicio", "2026-10-10")
                .param("fim", "2026-10-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/operacional/tickers").param("status", "NAO VALE")).andExpect(status().isBadRequest());
    }

    @Test
    void alertasRepresentamEstadoAtualESemEventosHistoricosFechados() throws Exception {
        var repo = mock(RepositorioOperacionalV2.class);
        var snapshot = new Snapshot("PETR4", LocalDate.of(2026, 10, 9), V, "EM_OBSERVACAO",
                "ALTA", "Energia", "COMPRA", null, null, "NAO_CALIBRADA", null,
                null, JSON.readTree("{\"motivosBloqueio\":[]}"), null, null,
                LocalDateTime.of(2026, 10, 9, 19, 30));
        when(repo.snapshotsRecentes()).thenReturn(List.of(snapshot));
        when(repo.alertas(anyInt())).thenReturn(List.of(new TimelineLinha("evt-antigo", "PETR4",
                LocalDate.of(2026, 10, 1), V, "POSICAO_FECHADA", "ATENCAO", "Posição encerrada", null,
                LocalDateTime.of(2026, 10, 1, 19, 0))));

        mvc(repo).perform(get("/operacional/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty());
    }

    @Test
    void controllerV2SoTemRotasGet() {
        for (var metodo : OperacionalV2Controller.class.getDeclaredMethods()) {
            assertTrue(metodo.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class),
                    metodo.getName());
        }
    }

    private static RepositorioOperacionalV2 repoComDados() throws Exception {
        var repo = mock(RepositorioOperacionalV2.class);
        var payload = JSON.readTree("{\"setor\":\"Energia\",\"motivosBloqueio\":[{\"codigo\":\"SEM_FUNDAMENTO_VIGENTE\",\"descricao\":\"Fundamento ausente\"}]}");
        var consenso = JSON.readTree("{\"regraPrincipal\":\"COMPRA\",\"tecnico\":\"NEUTRO\"}");
        var tese = JSON.readTree("{\"positivos\":[\"Liquidez alta\"],\"riscos\":[\"Evento CVM\"]}");
        var snapshot = new Snapshot("PETR4", LocalDate.of(2026, 10, 9), V, "PAPER_TRADING_ELEGIVEL",
                "ALTA", "Energia", "COMPRA", JSON.readTree("{\"momentum\":\"NEUTRO\"}"), "POSITIVA", "ALTA", "ABERTA",
                "ALERTA", payload, consenso, tese, LocalDateTime.of(2026, 10, 9, 19, 30));
        when(repo.snapshotsRecentes()).thenReturn(List.of(snapshot));
        when(repo.snapshot("PETR4")).thenReturn(java.util.Optional.of(snapshot));
        var evento = new TimelineLinha("evt-1", "PETR4", LocalDate.of(2026, 10, 9), V, "ALERTA",
                "ATENCAO", "Ticker deixou de ser elegível", JSON.readTree("{\"categoria\":\"RISCO\"}"),
                LocalDateTime.of(2026, 10, 9, 19, 31));
        when(repo.timeline(Mockito.eq("PETR4"), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), anyInt()))
                .thenReturn(List.of(evento));
        when(repo.alertas(anyInt())).thenReturn(List.of(evento));
        return repo;
    }
}
