package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.exceptions.handler.TratadorGlobalExcecoes;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.DiarioLinha;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.EventoLinha;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.OperacaoLinha;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.Regra;
import br.com.miranda.gestor.ativos.brutos.service.ServicoOperacional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OPR-GES-5: contrato das rotas /operacional (nomes camelCase, 400 para parametro ruim, 200 vazio sem
 * tabelas) e GET sem efeito colateral: 5 chamadas de cada rota so executam SELECT.
 */
class OperacionalControllerTest {

    private static final String V = "OPR-2026.10.10-1";
    private static final LocalDate PREGAO = LocalDate.of(2026, 10, 9);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String texto) {
        try {
            return JSON.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockMvc mvc(RepositorioOperacional repo) {
        return MockMvcBuilders.standaloneSetup(new OperacionalController(new ServicoOperacional(repo)))
                .setControllerAdvice(new TratadorGlobalExcecoes())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()
                                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    private RepositorioOperacional repoComDados() {
        var repo = mock(RepositorioOperacional.class);
        var regra = new Regra(V, "DEC-OPR-1", "ATIVA", LocalDate.of(2026, 10, 10), json("{\"trava\":{\"min_pregoes\":63}}"));
        when(repo.regraAtiva()).thenReturn(Optional.of(regra));
        when(repo.regra(V)).thenReturn(Optional.of(regra));
        var d = new DiarioLinha(V, PREGAO, new BigDecimal("100000.00"), new BigDecimal("101500.00"),
                new BigDecimal("20000.00"), new BigDecimal("0.8"), 5, 1, 0, new BigDecimal("0.001"),
                new BigDecimal("0.0008"), new BigDecimal("0.031"), new BigDecimal("0.0005"), new BigDecimal("0.012"),
                new BigDecimal("0.010"), new BigDecimal("0.021"), new BigDecimal("-0.015"), new BigDecimal("-0.04"),
                new BigDecimal("12.30"), 0, "EM_OBSERVACAO", json("[\"Menos de 63 pregões\"]"),
                LocalDateTime.of(2026, 10, 9, 19, 0));
        when(repo.ultimoDiario(V)).thenReturn(Optional.of(d));
        when(repo.contarDiario(V)).thenReturn(40);
        when(repo.primeiraDataDoDiario(V)).thenReturn(Optional.of(LocalDate.of(2026, 8, 3)));
        when(repo.contarOperacoesPorStatus(V)).thenReturn(Map.of("ABERTA", 1L, "FECHADA", 1L));
        when(repo.diario(any(), any(), any(), anyInt())).thenReturn(List.of(d));
        when(repo.operacoes(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(
                new OperacaoLinha(1, V, "PETR4", "Energia", 21, "FECHADA", PREGAO.minusDays(10), PREGAO.minusDays(9),
                        new BigDecimal("38.5"), 200, new BigDecimal("7700"), new BigDecimal("36"), new BigDecimal("37"),
                        "ALTA", json("{}"), PREGAO.minusDays(1), PREGAO, new BigDecimal("39"), "STOP",
                        json("{\"stop\":37}"), new BigDecimal("7.7"), new BigDecimal("7.8"), new BigDecimal("100"),
                        new BigDecimal("80"), new BigDecimal("5"), new BigDecimal("75"))));
        when(repo.ultimaDataDeBloqueio(V)).thenReturn(Optional.of(PREGAO));
        when(repo.bloqueios(any(), any(), any(), anyInt())).thenReturn(List.of(
                new EventoLinha("e1", "BLOQUEIO_LIQUIDEZ", V, PREGAO, "ABCD3", json("{\"codigo\":\"LIQUIDEZ_INSUFICIENTE\"}"))));
        when(repo.ultimoPregaoDisponivel()).thenReturn(Optional.of(PREGAO));
        when(repo.tabelasDisponiveis()).thenReturn(true);
        return repo;
    }

    @Test
    void resumoExpoeOsCamposDoContratoEmCamelCase() throws Exception {
        mvc(repoComDados()).perform(get("/operacional/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("SIMULADO"))
                .andExpect(jsonPath("$.versaoRegra").value(V))
                .andExpect(jsonPath("$.statusSistema").value("EM_OBSERVACAO"))
                .andExpect(jsonPath("$.ultimoPregao").value("2026-10-09"))
                .andExpect(jsonPath("$.dataInicio").value("2026-08-03"))
                .andExpect(jsonPath("$.impostoEstimadoMes").value(12.30))
                .andExpect(jsonPath("$.motivosStatus[0].codigo").value("MOTIVO"))
                .andExpect(jsonPath("$.motivosStatus[0].descricao").value("Menos de 63 pregões"))
                .andExpect(jsonPath("$.excessoSobreCdiLiquido").value(0.021))
                .andExpect(jsonPath("$.drawdownMaximo").value(-0.04))
                .andExpect(jsonPath("$.cdiLiquidoIrAcumulado").value(0.010))
                .andExpect(jsonPath("$.pregoesAvaliados").value(40))
                .andExpect(jsonPath("$.pregoesMinimos").value(63))
                .andExpect(jsonPath("$.operacoesAbertas").value(1))
                .andExpect(jsonPath("$.aviso").isString());
    }

    @Test
    void diarioOperacoesBloqueiosESaudeExpoemOsCamposDoContrato() throws Exception {
        MockMvc mvc = mvc(repoComDados());
        mvc.perform(get("/operacional/diario").param("inicio", "2026-10-01").param("fim", "2026-10-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].retornoLiquidoAcumulado").value(0.031))
                .andExpect(jsonPath("$.itens[0].statusSistema").value("EM_OBSERVACAO"))
                .andExpect(jsonPath("$.itens[0].excessoSobreCdiLiquido").value(0.021));
        mvc.perform(get("/operacional/operacoes").param("status", "fechada").param("simbolo", "petr4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].motivoSaida").value("STOP"))
                .andExpect(jsonPath("$.itens[0].motivoSaidaLegivel").value("Stop de preço atingido"))
                .andExpect(jsonPath("$.itens[0].custosTotais").value(15.5))
                .andExpect(jsonPath("$.itens[0].resultadoPosImpostoEstimado").value(75));
        mvc.perform(get("/operacional/bloqueios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataPregao").value("2026-10-09"))
                .andExpect(jsonPath("$.itens[0].simbolo").value("ABCD3"))
                .andExpect(jsonPath("$.itens[0].motivos[0].codigo").value("LIQUIDEZ_INSUFICIENTE"))
                .andExpect(jsonPath("$.itens[0].motivos[0].descricao").value("Liquidez abaixo do mínimo da regra"));
        mvc.perform(get("/operacional/saude"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_OBSERVACAO"))
                .andExpect(jsonPath("$.dadosDoDiaCompletos").value(true))
                .andExpect(jsonPath("$.violacoes").isArray())
                .andExpect(jsonPath("$.ultimaExecucao").value("2026-10-09T19:00:00"))
                .andExpect(jsonPath("$.tabelasDisponiveis").value(true));
    }

    @Test
    void parametroInvalidoResponde400() throws Exception {
        MockMvc mvc = mvc(repoComDados());
        mvc.perform(get("/operacional/diario").param("inicio", "ontem")).andExpect(status().isBadRequest());
        mvc.perform(get("/operacional/diario").param("inicio", "2026-10-09").param("fim", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/operacional/operacoes").param("status", "TALVEZ")).andExpect(status().isBadRequest());
        mvc.perform(get("/operacional/operacoes").param("motivo", "AZAR")).andExpect(status().isBadRequest());
        mvc.perform(get("/operacional/bloqueios").param("data", "9/10/2026")).andExpect(status().isBadRequest());
        mvc.perform(get("/operacional/resumo").param("versaoRegra", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void oControllerSoTemRotasGet() {
        for (var m : OperacionalController.class.getDeclaredMethods()) {
            assertTrue(m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class), m.getName());
        }
    }

    @Test
    void semAsTabelasTodaRotaResponde200VazioNuncaErro() throws Exception {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var erro = new org.springframework.jdbc.BadSqlGrammarException("q", "SELECT",
                new java.sql.SQLException("Table doesn't exist"));
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenThrow(erro);
        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class))).thenThrow(erro);
        MockMvc mvc = mvc(new RepositorioOperacional(jdbc, JSON));

        mvc.perform(get("/operacional/resumo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.statusSistema").value("NAO_OPERAVEL")).andExpect(jsonPath("$.avisos[0]").isString());
        mvc.perform(get("/operacional/diario")).andExpect(status().isOk()).andExpect(jsonPath("$.itens").isEmpty());
        mvc.perform(get("/operacional/operacoes")).andExpect(status().isOk()).andExpect(jsonPath("$.itens").isEmpty());
        mvc.perform(get("/operacional/bloqueios")).andExpect(status().isOk()).andExpect(jsonPath("$.itens").isEmpty());
        mvc.perform(get("/operacional/saude")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_OPERAVEL")).andExpect(jsonPath("$.tabelasDisponiveis").value(false));
    }

    @Test
    void cincoChamadasDeCadaGetSoExecutamSelect() throws Exception {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class))).thenReturn(List.of());
        MockMvc mvc = mvc(new RepositorioOperacional(jdbc, JSON));

        for (int i = 0; i < 5; i++) {
            for (String rota : List.of("resumo", "diario", "operacoes", "bloqueios", "saude")) {
                mvc.perform(get("/operacional/" + rota)).andExpect(status().isOk());
            }
        }

        var chamadas = Mockito.mockingDetails(jdbc).getInvocations();
        assertFalse(chamadas.isEmpty());
        for (var chamada : chamadas) {
            assertEquals("query", chamada.getMethod().getName(), "so leitura: " + chamada.getMethod().getName());
            assertTrue(chamada.getArgument(0).toString().stripLeading().toUpperCase().startsWith("SELECT"));
        }
    }
}
