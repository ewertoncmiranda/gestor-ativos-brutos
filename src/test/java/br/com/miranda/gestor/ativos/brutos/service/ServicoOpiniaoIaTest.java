package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.OpiniaoAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOpiniaoIa;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOpiniaoIa.Linha;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicoOpiniaoIaTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate PREGAO = LocalDate.of(2026, 10, 6);

    private static JsonNode json(String texto) throws Exception {
        return JSON.readTree(texto);
    }

    private static Linha linha(int horizonte, String opiniao, String modelo, String origem, int minuto) throws Exception {
        return new Linha(PREGAO, horizonte, opiniao, "RISCO_MEDIO",
                json("[{\"evidencia_id\":\"sinal_momentum\",\"leitura\":\"Momentum neutro.\"}]"),
                json("[\"Queda abaixo da MM50\"]"), json("[\"fator BETA_12M\"]"),
                json("[{\"id\":\"sinal_momentum\",\"rotulo\":\"Momentum\",\"valor\":\"NEUTRO_TECNICO\",\"direcao\":0}]"),
                modelo, origem, "1.2", LocalDateTime.of(2026, 10, 7, 18, minuto));
    }

    @Test
    void modeloPrevaleceSobreRegraPorHorizonte() throws Exception {
        var repositorio = mock(RepositorioOpiniaoIa.class);
        when(repositorio.doUltimoPregao("PETR4")).thenReturn(List.of(
                linha(63, "SINAL_NEGATIVO", "regra", "REGRA", 1),
                linha(21, "SINAL_NEUTRO", "regra", "REGRA", 1),
                linha(21, "SINAL_POSITIVO", "qwen2.5:7b", "MODELO", 5),
                linha(126, "SEM_BASE", "regra", "REGRA", 1)));

        OpiniaoAtivoDTO dto = new ServicoOpiniaoIa(repositorio).opiniao("petr4");

        assertEquals("PETR4", dto.simbolo());
        assertEquals(PREGAO, dto.dataPregao());
        assertEquals(OpiniaoAtivoDTO.AVISO, dto.aviso());
        assertEquals(List.of(21, 63, 126), dto.horizontes().stream().map(OpiniaoAtivoDTO.Horizonte::horizontePregoes).toList());
        OpiniaoAtivoDTO.Horizonte curto = dto.horizontes().get(0);
        assertEquals("SINAL_POSITIVO", curto.opiniao());
        assertEquals("MODELO", curto.origem());
        assertEquals("sinal_momentum", curto.justificativa().get(0).evidenciaId());
        assertEquals(List.of("Queda abaixo da MM50"), curto.oQueInvalida());
        assertEquals(List.of("fator BETA_12M"), curto.dadosAusentes());
        assertEquals(0, curto.evidencias().get(0).direcao());
        assertEquals("REGRA", dto.horizontes().get(1).origem());

        // Contrato com o painel: camelCase exato, inclusive "oQueInvalida".
        JsonNode serializado = new ObjectMapper().findAndRegisterModules().valueToTree(dto);
        JsonNode primeiro = serializado.get("horizontes").get(0);
        for (String campo : List.of("dataPregao", "horizontePregoes", "opiniao", "risco", "justificativa",
                "oQueInvalida", "dadosAusentes", "evidencias", "modelo", "origem", "versaoPrompt")) {
            assertTrue(primeiro.has(campo), "campo ausente no JSON: " + campo);
        }
        assertEquals(OpiniaoAtivoDTO.AVISO, serializado.get("aviso").asText());
    }

    @Test
    void entreModelosValeOMaisRecente() throws Exception {
        var escolhidos = ServicoOpiniaoIa.escolherPorHorizonte(List.of(
                linha(21, "SINAL_NEUTRO", "modelo-a", "MODELO", 1),
                linha(21, "SINAL_NEGATIVO", "modelo-b", "MODELO", 9)));
        assertEquals("modelo-b", escolhidos.get(0).modelo());
    }

    @Test
    void reservaPorRegraGravadaPeloModeloNaoPassaNaFrenteDoModelo() throws Exception {
        // O modelo falhou na validacao e gravou a reserva (modelo=qwen, origem=REGRA)
        // depois de uma linha MODELO valida: vale a MODELO, mesmo mais antiga.
        var escolhidos = ServicoOpiniaoIa.escolherPorHorizonte(List.of(
                linha(21, "SINAL_POSITIVO", "qwen2.5:1.5b-instruct", "MODELO", 1),
                linha(21, "SINAL_NEUTRO", "qwen2.5:1.5b-instruct", "REGRA", 9)));
        assertEquals("MODELO", escolhidos.get(0).origem());
        assertEquals("SINAL_POSITIVO", escolhidos.get(0).opiniao());
    }

    @Test
    void justificativaComTrechoIdSemEvidenciaId() throws Exception {
        var l = new Linha(PREGAO, 63, "SINAL_NEUTRO", "RISCO_MEDIO",
                json("[{\"evidencia_id\":null,\"trecho_id\":\"evidencia/momentum#2016-2026\",\"leitura\":\"Momentum bateu o mercado em 49% das semanas.\",\"fonte\":\"conhecimento/evidencia/momentum.md#resultado\",\"trecho\":\"Quintil de maior momentum: 49,1% das semanas.\"}]"),
                json("[]"), json("[]"), json("[]"), "qwen2.5:1.5b-instruct", "MODELO", "skills@abc", null);
        var j = ServicoOpiniaoIa.escolherPorHorizonte(List.of(l)).get(0).justificativa().get(0);
        assertNull(j.evidenciaId());
        assertEquals("evidencia/momentum#2016-2026", j.trechoId());
        assertTrue(j.leitura().startsWith("Momentum"));
        assertEquals("conhecimento/evidencia/momentum.md#resultado", j.fonte());
        assertEquals("Quintil de maior momentum: 49,1% das semanas.", j.trecho());
    }

    @Test
    void semLinhasDevolveAvisoEListaVazia() {        var repositorio = mock(RepositorioOpiniaoIa.class);
        when(repositorio.doUltimoPregao("XXXX3")).thenReturn(List.of());
        OpiniaoAtivoDTO dto = new ServicoOpiniaoIa(repositorio).opiniao("XXXX3");
        assertTrue(dto.horizontes().isEmpty());
        assertNull(dto.dataPregao());
        assertEquals(OpiniaoAtivoDTO.AVISO, dto.aviso());
    }

    @Test
    void jsonAusenteViraListaVazia() {
        var l = new Linha(PREGAO, 21, "SEM_BASE", "RISCO_ALTO", null, null, null, null, "regra", "REGRA", "1.2", null);
        var h = ServicoOpiniaoIa.escolherPorHorizonte(List.of(l)).get(0);
        assertTrue(h.justificativa().isEmpty());
        assertTrue(h.oQueInvalida().isEmpty());
        assertTrue(h.evidencias().isEmpty());
    }
}
