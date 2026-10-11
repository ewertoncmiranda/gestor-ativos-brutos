package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.DiarioLinha;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.EventoLinha;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.OperacaoLinha;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacional.Regra;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicoOperacionalTest {

    private static final String V = "OPR-2026.10.10-1";
    private static final LocalDate PREGAO = LocalDate.of(2026, 10, 9);
    private static final ObjectMapper JSON = new ObjectMapper();

    private RepositorioOperacional repo;
    private ServicoOperacional servico;

    @BeforeEach
    void preparar() {
        repo = mock(RepositorioOperacional.class);
        servico = new ServicoOperacional(repo);
    }

    private static JsonNode json(String texto) {
        try {
            return JSON.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Regra regra(String status, String parametros) {
        return new Regra(V, "DEC-OPR-1", status, LocalDate.of(2026, 10, 10), json(parametros));
    }

    private static DiarioLinha diario(LocalDate dia, String status, BigDecimal retorno, BigDecimal cdiLiquido,
                                      BigDecimal excesso, BigDecimal drawdown, BigDecimal drawdownMax,
                                      Integer violacoesLiquidez) {
        return new DiarioLinha(V, dia, bd("100000.00"), bd("101500.00"), bd("20000.00"), bd("0.80000000"), 5, 1, 0,
                bd("0.00100000"), bd("0.00080000"), retorno, bd("0.00050000"), bd("0.01200000"), cdiLiquido, excesso,
                drawdown, drawdownMax, bd("12.30"), violacoesLiquidez, status, json("[\"Menos de 63 pregões\"]"),
                LocalDateTime.of(2026, 10, 9, 19, 0));
    }

    private static OperacaoLinha operacao(long id, String status, String motivoSaida, BigDecimal custosEntrada,
                                          BigDecimal custosSaida) {
        return new OperacaoLinha(id, V, "PETR4", "Energia", 21, status, PREGAO.minusDays(10), PREGAO.minusDays(9),
                bd("38.500000"), 200, bd("7700.00"), bd("36.000000"), bd("37.000000"), "ALTA",
                json("{\"sinal\":\"SINAL_POSITIVO\"}"), motivoSaida == null ? null : PREGAO.minusDays(1),
                motivoSaida == null ? null : PREGAO, motivoSaida == null ? null : bd("39.000000"), motivoSaida,
                motivoSaida == null ? null : json("{\"stop\":37.0}"), custosEntrada, custosSaida,
                motivoSaida == null ? null : bd("100.00"), motivoSaida == null ? null : bd("80.00"),
                motivoSaida == null ? null : bd("5.00"), motivoSaida == null ? null : bd("75.00"));
    }

    // ---------- resumo ----------

    @Test
    void resumoTrazCdiExcessoDrawdownEDiasAvaliados() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{\"trava\":{\"min_pregoes\":63}}")));
        when(repo.regra(V)).thenReturn(Optional.of(regra("ATIVA", "{\"trava\":{\"min_pregoes\":63}}")));
        when(repo.ultimoDiario(V)).thenReturn(Optional.of(diario(PREGAO, "EM_OBSERVACAO", bd("0.03100000"),
                bd("0.01000000"), bd("0.02100000"), bd("-0.01500000"), bd("-0.04000000"), 0)));
        when(repo.contarDiario(V)).thenReturn(40);
        when(repo.primeiraDataDoDiario(V)).thenReturn(Optional.of(LocalDate.of(2026, 8, 3)));
        when(repo.contarOperacoesPorStatus(V)).thenReturn(Map.of("ABERTA", 3L, "FECHADA", 7L, "CANCELADA", 1L));

        OperacionalDTO.Resumo r = servico.resumo(null);

        assertEquals("SIMULADO", r.modo());
        assertEquals(V, r.versaoRegra());
        assertEquals("EM_OBSERVACAO", r.statusSistema());
        assertEquals(PREGAO, r.ultimoPregao());
        assertEquals(LocalDate.of(2026, 8, 3), r.dataInicio());
        assertEquals(bd("12.30"), r.impostoEstimadoMes());
        assertEquals(40, r.pregoesAvaliados());
        assertEquals(63, r.pregoesMinimos(), "e a trava da regra, nao o que falta");
        assertEquals(bd("0.03100000"), r.retornoLiquidoAcumulado());
        assertEquals(bd("0.01000000"), r.cdiLiquidoIrAcumulado());
        assertEquals(bd("0.02100000"), r.excessoSobreCdiLiquido());
        assertEquals(bd("-0.01500000"), r.drawdown());
        assertEquals(bd("-0.04000000"), r.drawdownMaximo());
        assertEquals(3L, r.operacoesAbertas());
        assertEquals(7L, r.operacoesFechadas());
        assertEquals(List.of(new OperacionalDTO.Motivo("MOTIVO", "Menos de 63 pregões")), r.motivosStatus());
        assertTrue(r.avisos().isEmpty());
    }

    @Test
    void excessoAusenteEDerivadoDeRetornoMenosCdiLiquido() {
        var d = diario(PREGAO, "EM_OBSERVACAO", bd("0.05000000"), bd("0.01500000"), null, null, null, 0);
        assertEquals(bd("0.03500000"), ServicoOperacional.excessoSobreCdiLiquido(d));
        var semCdi = diario(PREGAO, "EM_OBSERVACAO", bd("0.05000000"), null, null, null, null, 0);
        assertNull(ServicoOperacional.excessoSobreCdiLiquido(semCdi), "sem CDI liquido nao ha excesso, nunca zero");
    }

    @Test
    void drawdownMaximoAusenteNoDiaVemDoMinimoDaSerie() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.regra(V)).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.ultimoDiario(V)).thenReturn(Optional.of(diario(PREGAO, "EM_OBSERVACAO", null, null, null,
                bd("-0.01"), null, 0)));
        when(repo.drawdownMinimo(V)).thenReturn(Optional.of(bd("-0.07")));
        OperacionalDTO.Resumo r = servico.resumo(null);
        assertEquals(bd("-0.07"), r.drawdownMaximo());
        assertNull(r.pregoesMinimos(), "regra sem trava.min_pregoes nao inventa numero");
    }

    @Test
    void resumoSemNadaGravadoEhNaoOperavelComAvisoNuncaErro() {
        OperacionalDTO.Resumo r = servico.resumo(null);
        assertEquals("NAO_OPERAVEL", r.statusSistema());
        assertNull(r.versaoRegra());
        assertNull(r.patrimonio());
        assertEquals(0, r.pregoesAvaliados());
        assertNull(r.ultimoPregao());
        assertEquals(1, r.avisos().size());
        assertEquals(OperacionalDTO.AVISO, r.aviso());
    }

    @Test
    void regraSemDiarioDizQueFaltaDiarioEAvisaDoRascunho() {
        when(repo.regraAtiva()).thenReturn(Optional.empty());
        when(repo.versaoDaUltimaOperacao()).thenReturn(Optional.of(V));
        when(repo.regra(V)).thenReturn(Optional.of(regra("RASCUNHO", "{}")));
        OperacionalDTO.Resumo r = servico.resumo(null);
        assertEquals("NAO_OPERAVEL", r.statusSistema());
        assertEquals("RASCUNHO", r.statusRegra());
        assertEquals(2, r.avisos().size());
    }

    @Test
    void versaoPedidaMalFormadaEhRecusada() {
        assertThrows(IllegalArgumentException.class, () -> servico.resumo("DROP TABLE"));
    }

    // ---------- diario ----------

    @Test
    void diarioDevolveSerieEmOrdemComExcessoConsistente() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.diario(eq(V), any(), any(), anyInt())).thenReturn(List.of(
                diario(PREGAO.minusDays(1), "EM_OBSERVACAO", bd("0.02"), bd("0.005"), null, bd("-0.01"), bd("-0.01"), 0),
                diario(PREGAO, "EM_OBSERVACAO", bd("0.03"), bd("0.010"), bd("0.02"), bd("0"), bd("-0.01"), 0)));
        OperacionalDTO.Diario d = servico.diario(PREGAO.minusDays(5), PREGAO, null);
        assertEquals(2, d.itens().size());
        assertEquals(bd("0.015"), d.itens().get(0).excessoSobreCdiLiquido());
        assertEquals(bd("0.02"), d.itens().get(1).excessoSobreCdiLiquido());
        assertTrue(d.avisos().isEmpty());
        verify(repo).diario(eq(V), eq(PREGAO.minusDays(5)), eq(PREGAO), eq(ServicoOperacional.LIMITE_DIARIO));
    }

    @Test
    void diarioDeJanelaSemDadosVemVazioComAviso() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.diario(eq(V), any(), any(), anyInt())).thenReturn(List.of());
        OperacionalDTO.Diario d = servico.diario(null, null, null);
        assertTrue(d.itens().isEmpty());
        assertEquals(1, d.avisos().size());
    }

    @Test
    void diarioRecusaInicioDepoisDoFim() {
        assertThrows(IllegalArgumentException.class, () -> servico.diario(PREGAO, PREGAO.minusDays(1), null));
    }

    // ---------- operacoes ----------

    @Test
    void filtrosDeOperacoesSaoNormalizadosECombinados() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.operacoes(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        servico.operacoes("fechada", " petr4 ", PREGAO.minusDays(30), PREGAO, "stop", 50, null);
        verify(repo).operacoes(V, "FECHADA", "PETR4", PREGAO.minusDays(30), PREGAO, "STOP", 50);
    }

    @Test
    void operacoesSemFiltroUsamLimitePadraoELimiteMaximoCorta() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.operacoes(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        assertEquals(ServicoOperacional.LIMITE_PADRAO, servico.operacoes(null, null, null, null, null, null, null).limite());
        assertEquals(RepositorioOperacional.LIMITE_MAXIMO, servico.operacoes(null, null, null, null, null, 99999, null).limite());
        assertEquals(1, servico.operacoes(null, null, null, null, null, -5, null).limite());
    }

    @Test
    void operacaoFechadaTrazMotivoLegivelECustosTotais() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.operacoes(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(
                operacao(1, "FECHADA", "STOP", bd("7.70"), bd("7.80")),
                operacao(2, "ABERTA", null, bd("7.70"), null),
                operacao(3, "PENDENTE", null, null, null)));
        OperacionalDTO.Operacoes o = servico.operacoes(null, null, null, null, null, null, null);
        var fechada = o.itens().get(0);
        assertEquals("STOP", fechada.motivoSaida());
        assertEquals("Stop de preço atingido", fechada.motivoSaidaLegivel());
        assertEquals(bd("15.50"), fechada.custosTotais());
        assertEquals(bd("75.00"), fechada.resultadoPosImpostoEstimado());
        assertEquals(bd("7.70"), o.itens().get(1).custosTotais(), "aberta so tem custo de entrada");
        assertNull(o.itens().get(1).motivoSaida());
        assertNull(o.itens().get(2).custosTotais(), "sem custo registrado nao vira zero");
        assertEquals(3, o.total());
    }

    @Test
    void filtroInvalidoDeOperacoesEhRecusado() {
        assertThrows(IllegalArgumentException.class, () -> servico.operacoes("TALVEZ", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> servico.operacoes(null, "petr4; drop", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> servico.operacoes(null, null, null, null, "AZAR", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> servico.operacoes(null, null, PREGAO, PREGAO.minusDays(1), null, null, null));
    }

    // ---------- bloqueios ----------

    private static EventoLinha evento(String tipo, String simbolo, String payload) {
        return new EventoLinha("e-" + simbolo, tipo, V, PREGAO, simbolo, json(payload));
    }

    @Test
    void bloqueiosAgrupadosPorAtivoComFaixaEMotivosLegiveis() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.ultimaDataDeBloqueio(V)).thenReturn(Optional.of(PREGAO));
        when(repo.bloqueios(eq(V), eq(PREGAO), any(), anyInt())).thenReturn(List.of(
                evento("BLOQUEIO_LIQUIDEZ", "ABCD3",
                        "{\"simbolo\":\"ABCD3\",\"faixa\":\"insuficiente\",\"horizonte_pregoes\":21,\"motivos\":["
                                + "{\"codigo\":\"LIQUIDEZ_INSUFICIENTE\",\"detalhe\":\"ADTV 63d de R$ 1 mi\",\"valor\":1000000,\"limite\":5000000}]}"),
                evento("BLOQUEIO_DADO", "EFGH4",
                        "{\"simbolo\":\"EFGH4\",\"motivos\":[{\"codigo\":\"SEM_ATR\"},{\"codigo\":\"SEM_FUNDAMENTO_VIGENTE\"}]}"),
                evento("BLOQUEIO_DADO", "ABCD3", "{\"codigo\":\"SEM_ATR\"}")));
        OperacionalDTO.Bloqueios b = servico.bloqueios(null, null, null, null, null);
        assertEquals(PREGAO, b.dataPregao());
        assertEquals(2, b.total(), "um item por ativo");
        var abcd = b.itens().get(0);
        assertEquals("ABCD3", abcd.simbolo());
        assertEquals("INSUFICIENTE", abcd.faixa());
        assertEquals(2, abcd.motivos().size(), "os dois eventos do mesmo ativo ficam juntos");
        var liquidez = abcd.motivos().get(0);
        assertEquals("LIQUIDEZ", liquidez.categoria());
        assertEquals("LIQUIDEZ_INSUFICIENTE", liquidez.codigo());
        assertEquals("Liquidez abaixo do mínimo da regra", liquidez.descricao());
        assertEquals("ADTV 63d de R$ 1 mi", liquidez.detalhe());
        assertEquals(1000000, liquidez.valor().asInt());
        assertEquals(5000000, liquidez.limite().asInt());
        var efgh = b.itens().get(1);
        assertEquals("EFGH4", efgh.simbolo());
        assertNull(efgh.faixa(), "faixa so aparece quando gravada");
        assertEquals("Sem fundamento vigente na data (point-in-time)", efgh.motivos().get(1).descricao());
    }

    @Test
    void filtroDeMotivoNosBloqueiosEhPorCodigoEDescartaAtivoSemMotivo() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.bloqueios(eq(V), eq(PREGAO), any(), anyInt())).thenReturn(List.of(
                evento("BLOQUEIO_DADO", "EFGH4", "{\"motivos\":[{\"codigo\":\"SEM_ATR\"},{\"codigo\":\"SEM_FUNDAMENTO_VIGENTE\"}]}"),
                evento("BLOQUEIO_DADO", "IJKL3", "{\"codigo\":\"SEM_FUNDAMENTO_VIGENTE\"}")));
        OperacionalDTO.Bloqueios b = servico.bloqueios(PREGAO, "efgh4", "sem_atr", null, null);
        assertEquals(1, b.total());
        assertEquals(1, b.itens().get(0).motivos().size());
        assertEquals("SEM_ATR", b.itens().get(0).motivos().get(0).codigo());
        verify(repo).bloqueios(V, PREGAO, "EFGH4", RepositorioOperacional.LIMITE_MAXIMO);
        assertEquals(List.of(), ServicoOperacional.agrupar(List.of(
                evento("BLOQUEIO_DADO", "IJKL3", "{\"codigo\":\"SEM_FUNDAMENTO_VIGENTE\"}")), "SEM_ATR"));
    }

    @Test
    void payloadAntigoComBloqueiosEmVezDeMotivosTambemEhLido() {
        var lista = ServicoOperacional.expandir(evento("BLOQUEIO_DADO", "ABCD3",
                "{\"bloqueios\":[{\"codigo\":\"SEM_ATR\"}]}"));
        assertEquals("SEM_ATR", lista.get(0).codigo());
    }

    @Test
    void bloqueioSemCodigoNoPayloadUsaOTipoDoEvento() {
        var itens = ServicoOperacional.expandir(evento("BLOQUEIO_DADO", "ABCD3", "{}"));
        assertEquals("BLOQUEIO_DADO", itens.get(0).codigo());
        assertEquals("Dado ausente ou incompleto", itens.get(0).descricao());
        assertEquals("Algum codigo novo", ServicoOperacional.legivel("ALGUM_CODIGO_NOVO"), "codigo desconhecido vira texto legivel");
    }

    @Test
    void semBloqueiosGravadosVemVazioComAviso() {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        OperacionalDTO.Bloqueios b = servico.bloqueios(null, null, null, null, null);
        assertNull(b.dataPregao());
        assertTrue(b.itens().isEmpty());
        assertEquals(1, b.avisos().size());
    }

    @Test
    void motivosDeStatusViramCodigoEDescricao() {
        var lista = ServicoOperacional.motivos(json("[\"PREGOES_INSUFICIENTES\", \"Menos de 63 pregões\", "
                + "{\"codigo\":\"drawdown\",\"mensagem\":\"Queda acima do limite\"}, {\"codigo\":\"SEM_ATR\"}]"));
        assertEquals(List.of(
                new OperacionalDTO.Motivo("PREGOES_INSUFICIENTES", "Pregoes insuficientes"),
                new OperacionalDTO.Motivo("MOTIVO", "Menos de 63 pregões"),
                new OperacionalDTO.Motivo("DRAWDOWN", "Queda acima do limite"),
                new OperacionalDTO.Motivo("SEM_ATR", "Sem ATR calculado (histórico de preços insuficiente)")), lista);
        assertTrue(ServicoOperacional.motivos(null).isEmpty());
    }

    // ---------- saude ----------

    private void diarioEmUso(DiarioLinha d, String parametros) {
        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", parametros)));
        when(repo.regra(V)).thenReturn(Optional.of(regra("ATIVA", parametros)));
        when(repo.ultimoDiario(V)).thenReturn(Optional.of(d));
        when(repo.tabelasDisponiveis()).thenReturn(true);
    }

    @Test
    void saudeLimpaMantemOStatusDoDiario() {
        diarioEmUso(diario(PREGAO, "PAPER_TRADING_ELEGIVEL", bd("0.1"), bd("0.05"), bd("0.05"), bd("-0.02"),
                bd("-0.05"), 0), "{\"trava\":{\"drawdown_maximo\":0.15}}");
        when(repo.ultimoPregaoDisponivel()).thenReturn(Optional.of(PREGAO));
        when(repo.eventosPendentes(V)).thenReturn(2L);
        OperacionalDTO.Saude s = servico.saude(null);
        assertEquals("PAPER_TRADING_ELEGIVEL", s.status());
        assertEquals(Boolean.TRUE, s.dadosDoDiaCompletos());
        assertTrue(s.violacoes().isEmpty());
        assertEquals(2L, s.eventosPendentes());
        assertEquals(PREGAO, s.ultimaAvaliacao());
        assertEquals(LocalDateTime.of(2026, 10, 9, 19, 0), s.ultimaExecucao());
    }

    @Test
    void dadosDoDiaIncompletosBloqueiam() {
        diarioEmUso(diario(PREGAO.minusDays(3), "EM_OBSERVACAO", null, null, null, null, null, 0), "{}");
        when(repo.ultimoPregaoDisponivel()).thenReturn(Optional.of(PREGAO));
        OperacionalDTO.Saude s = servico.saude(null);
        assertEquals("BLOQUEADO", s.status());
        assertEquals("EM_OBSERVACAO", s.statusDiario());
        assertEquals(Boolean.FALSE, s.dadosDoDiaCompletos());
        assertEquals("DADO_INCOMPLETO", s.violacoes().get(0).codigo());
    }

    @Test
    void violacaoDeLiquidezBloqueia() {
        diarioEmUso(diario(PREGAO, "EM_OBSERVACAO", null, null, null, null, null, 2), "{}");
        when(repo.ultimoPregaoDisponivel()).thenReturn(Optional.of(PREGAO));
        OperacionalDTO.Saude s = servico.saude(null);
        assertEquals("BLOQUEADO", s.status());
        assertEquals("LIQUIDEZ", s.violacoes().get(0).codigo());
        assertEquals(2, s.violacoesDetalhe().get(0).valor().asInt());
    }

    @Test
    void drawdownNoLimiteDaRegraBloqueiaEAcimaDeleNao() {
        diarioEmUso(diario(PREGAO, "EM_OBSERVACAO", null, null, null, bd("-0.16"), bd("-0.16"), 0),
                "{\"trava\":{\"drawdown_maximo\":0.15}}");
        when(repo.ultimoPregaoDisponivel()).thenReturn(Optional.of(PREGAO));
        OperacionalDTO.Saude s = servico.saude(null);
        assertEquals("BLOQUEADO", s.status());
        assertEquals("DRAWDOWN", s.violacoes().get(0).codigo());

        diarioEmUso(diario(PREGAO, "EM_OBSERVACAO", null, null, null, bd("-0.10"), bd("-0.10"), 0),
                "{\"trava\":{\"drawdown_maximo\":0.15}}");
        assertEquals("EM_OBSERVACAO", servico.saude(null).status());
    }

    @Test
    void regraSemLimiteDeDrawdownAvisaEmVezDeBloquear() {
        diarioEmUso(diario(PREGAO, "EM_OBSERVACAO", null, null, null, bd("-0.50"), bd("-0.50"), 0), "{}");
        when(repo.ultimoPregaoDisponivel()).thenReturn(Optional.of(PREGAO));
        OperacionalDTO.Saude s = servico.saude(null);
        assertEquals("EM_OBSERVACAO", s.status());
        assertEquals(1, s.avisos().size());
    }

    @Test
    void diarioJaGravadoComoBloqueadoContinuaBloqueado() {
        diarioEmUso(diario(PREGAO, "BLOQUEADO", null, null, null, null, null, 0), "{}");
        when(repo.ultimoPregaoDisponivel()).thenReturn(Optional.of(PREGAO));
        OperacionalDTO.Saude s = servico.saude(null);
        assertEquals("BLOQUEADO", s.status());
        assertEquals("STATUS_ALTERADO", s.violacoes().get(0).codigo());
    }

    @Test
    void saudeSemTabelasOuSemDiarioNuncaEhErro() {
        OperacionalDTO.Saude semTabelas = servico.saude(null);
        assertEquals("NAO_OPERAVEL", semTabelas.status());
        assertFalse(semTabelas.tabelasDisponiveis());

        when(repo.regraAtiva()).thenReturn(Optional.of(regra("ATIVA", "{}")));
        when(repo.tabelasDisponiveis()).thenReturn(true);
        OperacionalDTO.Saude semDiario = servico.saude(null);
        assertEquals("NAO_OPERAVEL", semDiario.status());
        assertTrue(semDiario.tabelasDisponiveis());
        assertEquals(1, semDiario.avisos().size());
    }
}
