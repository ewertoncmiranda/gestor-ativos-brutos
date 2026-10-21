package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoAplicacao;
import br.com.miranda.gestor.ativos.brutos.external.dto.PanoramaMercadoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado.LinhaAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado.LinhaEvento;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado.LinhaMacro;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado.LinhaVolumeAnormal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MERC-GES-1: regras do panorama com o repositorio mockado e dados montados a
 * mao (mediana, coesao, desvio, "Outros", universos, evento, desempate,
 * limite de 10, indisponivel, janela).
 */
class ServicoPanoramaMercadoTest {

    private static final LocalDate PREGAO = LocalDate.of(2026, 10, 19);
    private static final double D = 1e-9;

    private RepositorioPanoramaMercado repo;
    private ServicoPanoramaMercado servico;

    /** 22 pregoes uteis ate PREGAO, do mais novo ao mais antigo (pula fins de semana e 12/10). */
    private static List<LocalDate> pregoes(LocalDate ate, int n) {
        List<LocalDate> datas = new ArrayList<>();
        LocalDate d = ate;
        while (datas.size() < n) {
            boolean util = d.getDayOfWeek().getValue() <= 5 && !d.equals(LocalDate.of(2026, 10, 12));
            if (util) datas.add(d);
            d = d.minusDays(1);
        }
        return datas;
    }

    /** Ativo liquido "padrao": volume 63d R$ 5 mi, presenca 100%, variacao = fim/100 - 1. */
    private static LinhaAtivo ativo(String simbolo, String setor, double fim, double volume) {
        return new LinhaAtivo(simbolo, "Empresa " + simbolo, setor, setor == null ? null : "GRUPO_" + setor,
                5_000_000.0, 1.0, 0.001, fim, 100.0, volume, 100L, 100.0, false, false);
    }

    private static LinhaAtivo ativo(String simbolo, String setor, double fim) {
        return ativo(simbolo, setor, fim, 1_000_000.0);
    }

    private static LinhaAtivo com(LinhaAtivo a, Double volume63, Double presenca, Double fechBase, long negocios,
                                  boolean evento) {
        return new LinhaAtivo(a.simbolo(), a.nome(), a.setor(), a.grupo(), volume63, presenca, a.spreadEstimado(),
                a.fechamentoFinal(), fechBase, a.volumePeriodo(), negocios, a.media21(), evento, a.evento21());
    }

    @BeforeEach
    void preparar() {
        repo = mock(RepositorioPanoramaMercado.class);
        servico = new ServicoPanoramaMercado(repo);
        when(repo.ultimoPregao()).thenReturn(Optional.of(PREGAO));
        when(repo.ultimoPregaoComLiquidez(any())).thenAnswer(i -> Optional.ofNullable(i.getArgument(0)));
        when(repo.pregoesAte(eq(PREGAO), anyInt())).thenReturn(pregoes(PREGAO, 22));
    }

    private PanoramaMercadoDTO.Panorama comAtivos(String janela, LinhaAtivo... linhas) {
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of(linhas));
        return servico.panorama(janela);
    }

    private static PanoramaMercadoDTO.Setor setor(PanoramaMercadoDTO.Panorama p, String nome) {
        return p.setores().stream().filter(s -> s.setor().equals(nome)).findFirst().orElseThrow();
    }

    // --- mediana, coesao, desvio -------------------------------------------------------

    @Test
    void medianaComNumeroImparEPar() {
        assertEquals(0.02, ServicoPanoramaMercado.mediana(List.of(0.05, -0.01, 0.02)), D);
        assertEquals(0.015, ServicoPanoramaMercado.mediana(List.of(0.05, -0.01, 0.02, 0.01)), D);

        var p = comAtivos("5d", ativo("A1", "Bancos", 105), ativo("A2", "Bancos", 99), ativo("A3", "Bancos", 102),
                ativo("B1", "Energia", 105), ativo("B2", "Energia", 99), ativo("B3", "Energia", 102),
                ativo("B4", "Energia", 101));
        assertEquals(0.02, setor(p, "Bancos").variacaoMediana(), D);
        assertEquals(0.015, setor(p, "Energia").variacaoMediana(), D);
        assertEquals(3, setor(p, "Bancos").empresas());
        assertEquals("GRUPO_Bancos", setor(p, "Bancos").grupo());
    }

    @Test
    void coesaoESelos() {
        var p = comAtivos("5d",
                // 3 de 4 sobem com a mediana: 0.75 JUNTO
                ativo("J1", "Junto", 103), ativo("J2", "Junto", 102), ativo("J3", "Junto", 101), ativo("J4", "Junto", 97),
                // 2 de 4 caem com a mediana negativa: 0.5 MISTO
                ativo("M1", "Misto", 95), ativo("M2", "Misto", 96), ativo("M3", "Misto", 101), ativo("M4", "Misto", 102),
                // mediana zero (estavel) e so 1 de 5 estavel: 0.2 DISPERSO
                ativo("D1", "Disperso", 105), ativo("D2", "Disperso", 103), ativo("D3", "Disperso", 100),
                ativo("D4", "Disperso", 97), ativo("D5", "Disperso", 95));
        assertEquals(0.75, setor(p, "Junto").coesao(), D);
        assertEquals("JUNTO", setor(p, "Junto").selo());
        assertTrue(setor(p, "Misto").variacaoMediana() < 0);
        assertEquals(0.5, setor(p, "Misto").coesao(), D);
        assertEquals("MISTO", setor(p, "Misto").selo());
        assertEquals(0.0, setor(p, "Disperso").variacaoMediana(), D);
        assertEquals(0.2, setor(p, "Disperso").coesao(), D);
        assertEquals("DISPERSO", setor(p, "Disperso").selo());

        assertEquals("JUNTO", ServicoPanoramaMercado.selo(0.70));
        assertEquals("MISTO", ServicoPanoramaMercado.selo(0.6999));
        assertEquals("MISTO", ServicoPanoramaMercado.selo(0.50));
        assertEquals("DISPERSO", ServicoPanoramaMercado.selo(0.4999));
    }

    @Test
    void desvioSeparaDestaqueDoSetorEOrdenaSetoresPelaMediana() {
        var p = comAtivos("5d", ativo("A1", "Alta", 110), ativo("A2", "Alta", 104), ativo("A3", "Alta", 103),
                ativo("Q1", "Queda", 98), ativo("Q2", "Queda", 97), ativo("Q3", "Queda", 90));
        assertEquals(List.of("Alta", "Queda"), p.setores().stream().map(PanoramaMercadoDTO.Setor::setor).toList());
        var alta = setor(p, "Alta");
        assertEquals(0.04, alta.variacaoMediana(), D);
        // 3 empresas: melhores = 2 de cima, piores = a restante, sem repetir
        assertEquals(List.of("A1", "A2"), alta.melhores().stream().map(PanoramaMercadoDTO.EmpresaSetor::simbolo).toList());
        assertEquals(List.of("A3"), alta.piores().stream().map(PanoramaMercadoDTO.EmpresaSetor::simbolo).toList());
        assertEquals(0.10, alta.melhores().get(0).variacao(), D);
        assertEquals(0.06, alta.melhores().get(0).desvio(), D);
        assertEquals(-0.01, alta.piores().get(0).desvio(), D);
        assertEquals("Empresa A1", alta.melhores().get(0).nome());
    }

    @Test
    void setorPequenoVaiParaOutrosQueFicaPorUltimoMesmoSubindoMais() {
        var p = comAtivos("5d", ativo("X1", "Pequeno1", 150), ativo("X2", "Pequeno1", 140),
                ativo("Y1", "Pequeno2", 130),
                ativo("B1", "Bancos", 99), ativo("B2", "Bancos", 98), ativo("B3", "Bancos", 97),
                ativo("SEM", null, 120));
        var nomes = p.setores().stream().map(PanoramaMercadoDTO.Setor::setor).toList();
        assertEquals(List.of("Bancos", "Outros"), nomes);
        var outros = setor(p, "Outros");
        assertEquals(3, outros.empresas());
        assertNull(outros.grupo());
        assertEquals(0.40, outros.variacaoMediana(), D);
        // Ativo sem setor CVM nao entra no card de setores, mas entra em altas
        assertTrue(p.altas().stream().anyMatch(a -> a.simbolo().equals("SEM")));
    }

    @Test
    void setorGrandeMostraDezMelhoresEDezPiores() {
        List<LinhaAtivo> linhas = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> ativo(String.format("S%02d", i), "Grande", 100 + i)).toList();
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(linhas);
        var grande = setor(servico.panorama("5d"), "Grande");
        assertEquals(25, grande.empresas());
        assertEquals(10, grande.melhores().size());
        assertEquals(10, grande.piores().size());
        assertEquals("S25", grande.melhores().get(0).simbolo());
        assertEquals("S01", grande.piores().get(0).simbolo());
        assertEquals(0.13, grande.variacaoMediana(), D);
    }

    // --- universos e evento --------------------------------------------------------------

    @Test
    void universosLiquidosEPresenca() {
        var bom = ativo("BOM3", "Bancos", 101);
        var semVolume = com(ativo("POUCO3", "Bancos", 120), 999_999.99, 1.0, 100.0, 100, false);
        var semPresenca = com(ativo("RARO3", "Bancos", 130), 5e6, 0.79, 100.0, 100, false);
        var semBase = com(ativo("NOVO3", "Bancos", 140), 5e6, 0.95, null, 100, false);
        var p = comAtivos("5d", bom, semVolume, semPresenca, semBase);

        assertEquals(1, p.universo().liquidos());
        assertEquals(3, p.universo().presenca()); // BOM3, POUCO3, NOVO3
        assertEquals(List.of("BOM3"), p.altas().stream().map(PanoramaMercadoDTO.Variacao::simbolo).toList());
        // liquidez usa presenca >= 80%: RARO3 fica fora, POUCO3 (pouco volume) entra
        var menores = p.liquidez().menores().stream().map(PanoramaMercadoDTO.ItemLiquidez::simbolo).toList();
        assertEquals(List.of("POUCO3", "BOM3", "NOVO3"), menores);
        assertFalse(menores.contains("RARO3"));
    }

    @Test
    void ativoComEventoNoPeriodoFicaForaDaVariacao() {
        var desdobrou = com(ativo("SPLT3", "Bancos", 50), 5e6, 1.0, 100.0, 100, true); // "-50%" falso
        var p = comAtivos("5d", desdobrou, ativo("B1", "Bancos", 99), ativo("B2", "Bancos", 98),
                ativo("B3", "Bancos", 97));
        assertTrue(p.quedas().stream().noneMatch(q -> q.simbolo().equals("SPLT3")));
        assertEquals(3, setor(p, "Bancos").empresas());
        assertEquals(3, p.amplitude().cairam());
        assertEquals(4, p.universo().liquidos()); // continua liquido; so sai dos rankings de variacao
        assertTrue(p.liquidez().maiores().stream().anyMatch(l -> l.simbolo().equals("SPLT3")));
    }

    // --- desempate e limite --------------------------------------------------------------

    @Test
    void desempatePorVolumeDepoisSimbolo() {
        var p = comAtivos("5d", ativo("CCC3", "S", 105, 1e6), ativo("AAA3", "S", 105, 1e6),
                ativo("BBB3", "S", 105, 9e6), ativo("ZZZ3", "S", 110, 1));
        assertEquals(List.of("ZZZ3", "BBB3", "AAA3", "CCC3"),
                p.altas().stream().map(PanoramaMercadoDTO.Variacao::simbolo).toList());
        assertEquals(List.of(1, 2, 3, 4), p.altas().stream().map(PanoramaMercadoDTO.Variacao::posicao).toList());
    }

    @Test
    void rankingsCortamEmDezComPosicaoAPartirDeUm() {
        List<LinhaAtivo> linhas = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            linhas.add(ativo(String.format("UP%02d", i), "S", 100 + i, i * 1e6));
            linhas.add(ativo(String.format("DN%02d", i), "S", 100 - i, i * 1e6));
        }
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(linhas);
        var p = servico.panorama("5d");
        assertEquals(10, p.altas().size());
        assertEquals(10, p.quedas().size());
        assertEquals("UP15", p.altas().get(0).simbolo());
        assertEquals("DN15", p.quedas().get(0).simbolo());
        assertEquals(-0.15, p.quedas().get(0).variacao(), D);
        assertEquals(1, p.altas().get(0).posicao());
        assertEquals(10, p.altas().get(9).posicao());
        assertEquals(10, p.liquidez().maiores().size());
        assertEquals(10, p.liquidez().menores().size());
        assertEquals(10, p.negociacao().maiores().size());
        assertEquals(10, p.negociacao().menores().size());
        assertEquals("DN15", p.negociacao().maiores().get(0).simbolo()); // empate com UP15 em 15e6 -> simbolo
        assertEquals(15e6, p.negociacao().maiores().get(0).volumeFinanceiro(), D);
        assertEquals("DN01", p.negociacao().menores().get(0).simbolo());
    }

    @Test
    void negociacaoMenoresIgnoraAtivoSemNegocioNoPeriodo() {
        var parado = com(ativo("PARA3", "S", 100, 0), 5e6, 0.9, 100.0, 0, false);
        var p = comAtivos("5d", parado, ativo("MOVE3", "S", 101, 2e6));
        assertEquals(List.of("MOVE3"),
                p.negociacao().menores().stream().map(PanoramaMercadoDTO.ItemNegociacao::simbolo).toList());
        assertEquals(100L, p.negociacao().maiores().get(0).negocios());
    }

    @Test
    void amplitudeContaDirecoesEAcimaDaMedia21() {
        var acima = ativo("UP3", "S", 110); // media21 = 100
        var abaixo = ativo("DN3", "S", 95);
        var estavel = ativo("EQ3", "S", 100);
        var eventoNa21 = new LinhaAtivo("EV3", "Ev", "S", "G", 5e6, 1.0, 0.0, 120.0, 100.0, 1e6, 10L, 300.0,
                false, true);
        var p = comAtivos("5d", acima, abaixo, estavel, eventoNa21);
        assertEquals(2, p.amplitude().subiram());
        assertEquals(1, p.amplitude().cairam());
        assertEquals(1, p.amplitude().estaveis());
        assertEquals(1.0 / 3, p.amplitude().acimaMedia21(), D); // EV3 fora da conta da media
    }

    // --- indisponivel, sem dado, janela ---------------------------------------------------

    @Test
    void blocoVazioEntraEmIndisponivelComSegundaOnda() {
        var p = comAtivos("5d", ativo("DN1", "S", 95), ativo("DN2", "S", 96), ativo("DN3", "S", 97));
        assertTrue(p.altas().isEmpty());
        assertEquals(List.of("altas", "volumeAnormal", "macro", "eventos"), p.indisponivel());
        assertTrue(p.volumeAnormal().isEmpty());
        assertTrue(p.eventos().isEmpty());
        assertNull(p.macro().selic());
    }

    @Test
    void semLiquidezCalculadaTodosOsBlocosIndisponiveisMasComDatas() {
        var p = comAtivos("5d");
        assertEquals(PREGAO, p.dataPregao());
        assertEquals(LocalDate.of(2026, 10, 13), p.dataInicial());
        assertEquals(ServicoPanoramaMercado.BLOCOS, p.indisponivel());
        assertEquals(0, p.universo().liquidos());
        assertNull(p.amplitude().subiram());
        assertTrue(p.liquidez().maiores().isEmpty());
        // resposta incompleta nao vai para o cache
        servico.panorama("5d");
        verify(repo, times(2)).ativos(any(), any(), any(), any(), any());
    }

    @Test
    void semNenhumaCotacaoDevolveTudoIndisponivel() {
        when(repo.ultimoPregao()).thenReturn(Optional.empty());
        var p = servico.panorama(null);
        assertEquals("5d", p.janela());
        assertNull(p.dataPregao());
        assertEquals(ServicoPanoramaMercado.BLOCOS, p.indisponivel());
        verify(repo, never()).ativos(any(), any(), any(), any(), any());
    }

    @Test
    void janelaInvalidaE400() {
        var erro = assertThrows(ExcecaoAplicacao.class, () -> servico.panorama("7d"));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatus());
        assertEquals("5d", ServicoPanoramaMercado.Janela.de(" ").codigo);
        assertEquals("21d", ServicoPanoramaMercado.Janela.de("21D").codigo);
    }

    // --- MERC-GES-2: volumeAnormal, macro, eventos ----------------------------------------

    private static LinhaVolumeAnormal vol(String simbolo, double volumePregao, double media21) {
        return new LinhaVolumeAnormal(simbolo, volumePregao, media21);
    }

    private void mockSegundaOnda(List<LinhaVolumeAnormal> vols, LinhaMacro macro, List<LinhaEvento> eventos) {
        when(repo.volumeAnormal(any(), any())).thenReturn(vols);
        when(repo.macro(any(), any())).thenReturn(macro);
        when(repo.eventos(any())).thenReturn(eventos);
    }

    @Test
    void volumeAnormalOrdenadoPorRazaoComDesempate() {
        // 3 ativos: razoes 3.0, 5.0, 5.0 — desempate pelo volumePregao
        var linhas = List.of(
                vol("AAA3", 3_000_000, 1_000_000),  // razao 3.0
                vol("BBB3", 2_000_000, 400_000),     // razao 5.0, vol menor
                vol("CCC3", 5_000_000, 1_000_000));  // razao 5.0, vol maior
        mockSegundaOnda(linhas, new LinhaMacro(0.15, 0.001, 5.20, 0.035), List.of());
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of());

        var p = servico.panorama("5d");
        var va = p.volumeAnormal();
        assertEquals(3, va.size());
        assertEquals("CCC3", va.get(0).simbolo()); // razao 5.0, maior vol
        assertEquals(5.0, va.get(0).razao(), D);
        assertEquals(1, va.get(0).posicao());
        assertEquals("BBB3", va.get(1).simbolo()); // razao 5.0, menor vol
        assertEquals(2, va.get(1).posicao());
        assertEquals("AAA3", va.get(2).simbolo()); // razao 3.0
        assertEquals(3, va.get(2).posicao());
        assertFalse(p.indisponivel().contains("volumeAnormal"));
    }

    @Test
    void volumeAnormalIgnoraMedia21Zero() {
        var linhas = List.of(
                vol("SEM3", 1_000_000, 0),        // media21 zero: ignorado
                vol("COM3", 2_000_000, 500_000)); // razao 4.0
        mockSegundaOnda(linhas, null, List.of());
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of());

        var p = servico.panorama("5d");
        assertEquals(1, p.volumeAnormal().size());
        assertEquals("COM3", p.volumeAnormal().get(0).simbolo());
    }

    @Test
    void volumeAnormalCortaEmDez() {
        var linhas = new ArrayList<LinhaVolumeAnormal>();
        for (int i = 1; i <= 15; i++) {
            linhas.add(vol(String.format("S%02d", i), i * 1_000_000.0, 1_000_000.0));
        }
        mockSegundaOnda(linhas, null, List.of());
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of());

        var p = servico.panorama("5d");
        assertEquals(10, p.volumeAnormal().size());
        assertEquals("S15", p.volumeAnormal().get(0).simbolo()); // maior razao
    }

    @Test
    void volumeAnormalFalhaSQLVaiParaIndisponivel() {
        DataAccessException ex = new DataRetrievalFailureException("simulado");
        when(repo.volumeAnormal(any(), any())).thenThrow(ex);
        when(repo.macro(any(), any())).thenReturn(new LinhaMacro(0.15, 0.001, 5.20, 0.035));
        when(repo.eventos(any())).thenReturn(List.of());
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(
                List.of(ativo("UP3", "S", 110), ativo("DN3", "S", 90)));

        var p = servico.panorama("5d");
        assertTrue(p.volumeAnormal().isEmpty());
        assertTrue(p.indisponivel().contains("volumeAnormal"));
        // macro e eventos presentes mesmo com falha no volumeAnormal
        assertNotNull(p.macro().selic());
    }

    @Test
    void macroPreenchidoComValoresDoBanco() {
        var macro = new LinhaMacro(0.15, 0.0025, 5.22, 0.035);
        mockSegundaOnda(List.of(), macro, List.of());
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of());

        var p = servico.panorama("5d");
        assertEquals(0.15, p.macro().selic(), D);
        assertEquals(0.0025, p.macro().cdiPeriodo(), D);
        assertEquals(5.22, p.macro().dolar(), D);
        assertEquals(0.035, p.macro().ipca12m(), D);
        assertFalse(p.indisponivel().contains("macro"));
    }

    @Test
    void macroNullDoRepositorioResultaEmMacroVazio() {
        // macro() retorna null (ex: Mockito default sem stub)
        mockSegundaOnda(List.of(), null, List.of());
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of());

        var p = servico.panorama("5d");
        assertNull(p.macro().selic());
        assertNull(p.macro().dolar());
        assertTrue(p.indisponivel().contains("macro"));
    }

    @Test
    void macroFalhaSQLVaiParaIndisponivel() {
        when(repo.volumeAnormal(any(), any())).thenReturn(List.of());
        when(repo.macro(any(), any())).thenThrow(new DataRetrievalFailureException("simulado"));
        when(repo.eventos(any())).thenReturn(List.of());
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of());

        var p = servico.panorama("5d");
        assertNull(p.macro().selic());
        assertTrue(p.indisponivel().contains("macro"));
    }

    @Test
    void eventosMapeadosCorretamente() {
        var dataCom = LocalDate.of(2026, 10, 15);
        var linhasEv = List.of(
                new LinhaEvento("ITUB4", "BONIFICACAO", dataCom, "11/10"),
                new LinhaEvento("BBAS3", "DESDOBRAMENTO", dataCom.minusDays(1), "2.0"));
        mockSegundaOnda(List.of(), new LinhaMacro(0.15, 0.001, 5.20, 0.035), linhasEv);
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of());

        var p = servico.panorama("5d");
        assertEquals(2, p.eventos().size());
        assertEquals("ITUB4", p.eventos().get(0).simbolo());
        assertEquals("BONIFICACAO", p.eventos().get(0).tipo());
        assertEquals(dataCom, p.eventos().get(0).data());
        assertEquals("11/10", p.eventos().get(0).fator());
        assertFalse(p.indisponivel().contains("eventos"));
    }

    @Test
    void eventosFalhaSQLVaiParaIndisponivel() {
        when(repo.volumeAnormal(any(), any())).thenReturn(List.of());
        when(repo.macro(any(), any())).thenReturn(new LinhaMacro(0.15, 0.001, 5.20, 0.035));
        when(repo.eventos(any())).thenThrow(new DataRetrievalFailureException("simulado"));
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of());

        var p = servico.panorama("5d");
        assertTrue(p.eventos().isEmpty());
        assertTrue(p.indisponivel().contains("eventos"));
    }

    // --- periodo e cache ------------------------------------------------------------------

    @Test
    void periodoEmPregoesParaCadaJanela() {
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of());
        LocalDate inicio21 = LocalDate.of(2026, 9, 18);
        LocalDate base21 = LocalDate.of(2026, 9, 17);

        assertEquals(PREGAO, servico.panorama("1d").dataInicial());
        verify(repo).ativos(LocalDate.of(2026, 10, 16), PREGAO, PREGAO, inicio21, base21);

        assertEquals(LocalDate.of(2026, 10, 13), servico.panorama("5d").dataInicial());
        verify(repo).ativos(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 13), PREGAO, inicio21, base21);

        assertEquals(inicio21, servico.panorama("21d").dataInicial());
        verify(repo).ativos(base21, inicio21, PREGAO, inicio21, base21);

        when(repo.primeiroPregaoEntre(LocalDate.of(2026, 1, 1), PREGAO)).thenReturn(Optional.of(LocalDate.of(2026, 1, 2)));
        when(repo.pregaoAnterior(LocalDate.of(2026, 1, 2))).thenReturn(Optional.of(LocalDate.of(2025, 12, 30)));
        assertEquals(LocalDate.of(2026, 1, 2), servico.panorama("ano").dataInicial());
        verify(repo).ativos(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 2), PREGAO, inicio21, base21);
    }

    @Test
    void historicoCurtoSemBaseDeixaVariacaoIndisponivel() {
        when(repo.pregoesAte(eq(PREGAO), anyInt())).thenReturn(List.of(PREGAO));
        when(repo.ativos(isNull(), eq(PREGAO), eq(PREGAO), eq(PREGAO), isNull()))
                .thenReturn(List.of(com(ativo("A3", "S", 100), 5e6, 1.0, null, 10, false)));
        var p = servico.panorama("5d");
        assertEquals(PREGAO, p.dataInicial());
        assertTrue(p.indisponivel().containsAll(List.of("setores", "altas", "quedas", "amplitude")));
        assertFalse(p.indisponivel().contains("liquidez"));
        assertFalse(p.indisponivel().contains("negociacao"));
    }

    @Test
    void cachePorJanelaEDataDoPregao() {
        when(repo.ativos(any(), any(), any(), any(), any()))
                .thenReturn(List.of(ativo("UP3", "S", 110), ativo("DN3", "S", 90)));
        var primeiro = servico.panorama("5d");
        assertSame(primeiro, servico.panorama("5d"));
        verify(repo, times(1)).ativos(any(), any(), any(), any(), any());

        servico.panorama("1d"); // outra janela, outro calculo
        verify(repo, times(2)).ativos(any(), any(), any(), any(), any());

        LocalDate novo = LocalDate.of(2026, 10, 20);
        when(repo.ultimoPregao()).thenReturn(Optional.of(novo));
        when(repo.pregoesAte(eq(novo), anyInt())).thenReturn(pregoes(novo, 22));
        var depois = servico.panorama("5d"); // novo pregao invalida
        assertNotSame(primeiro, depois);
        assertEquals(novo, depois.dataPregao());
        verify(repo, times(3)).ativos(any(), any(), any(), any(), any());
    }
    @Test void macroDisponivelNaoCongelaUniversoVazio() {
        when(repo.macro(any(), any())).thenReturn(new LinhaMacro(0.13, null, null, null));
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of(), List.of(ativo("ITUB4", "Bancos", 102)));
        assertEquals(0, servico.panorama("5d").universo().presenca());
        assertEquals(1, servico.panorama("5d").universo().presenca());
        verify(repo, times(2)).ativos(any(), any(), any(), any(), any());
    }

    @Test void cacheExpiraNoMesmoPregaoSemDormir() {
        var relogio = mock(java.time.Clock.class);
        var agora = new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2026-10-21T12:00:00Z"));
        when(relogio.instant()).thenAnswer(i -> agora.get());
        var s = new ServicoPanoramaMercado(repo, relogio);
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of(ativo("ITUB4", "Bancos", 102)));
        var primeiro = s.panorama("5d");
        agora.set(agora.get().plusSeconds(29));
        assertSame(primeiro, s.panorama("5d"));
        agora.set(agora.get().plusSeconds(1));
        assertNotSame(primeiro, s.panorama("5d"));
        verify(repo, times(2)).ativos(any(), any(), any(), any(), any());
    }

    @Test void usaPregaoAnteriorDuranteCargaEAvancaQuandoLiquidezTermina() {
        LocalDate novo = PREGAO.plusDays(1);
        when(repo.ultimoPregao()).thenReturn(Optional.of(novo));
        when(repo.ultimoPregaoComLiquidez(novo)).thenReturn(Optional.of(PREGAO), Optional.of(novo));
        when(repo.pregoesAte(eq(novo), anyInt())).thenReturn(pregoes(novo, 22));
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of(ativo("ITUB4", "Bancos", 102)));
        assertEquals(PREGAO, servico.panorama("5d").dataPregao());
        assertEquals(novo, servico.panorama("5d").dataPregao());
        verify(repo, times(2)).ativos(any(), any(), any(), any(), any());
    }

    @Test void semLiquidezHistoricaNaoGuardaRespostaVaziaComMacro() {
        when(repo.ultimoPregaoComLiquidez(any())).thenReturn(Optional.empty());
        when(repo.macro(any(), any())).thenReturn(new LinhaMacro(0.13, null, null, null));
        servico.panorama("5d"); servico.panorama("5d");
        verify(repo, times(2)).ativos(any(), any(), any(), any(), any());
    }

}
