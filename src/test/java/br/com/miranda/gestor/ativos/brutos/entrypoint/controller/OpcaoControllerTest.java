package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.OpcaoB3DTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOpcoes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpcaoControllerTest {

    private static OpcaoController controller(RepoOpcoesFake opcoes, RepositorioIdentidadeAtivo identidade) {
        return new OpcaoController(null, opcoes, identidade);
    }

    private static RepositorioIdentidadeAtivo identidade(String solicitado, String canonico) {
        return new RepositorioIdentidadeAtivo(null) {
            @Override public String canonico(String simbolo) {
                return solicitado.equals(simbolo) ? canonico : simbolo;
            }
        };
    }

    @Test
    void baseSemAtivoObjetoNaoUsaPrefixoFragilEExplicaVazio() {
        var repo = new RepoOpcoesFake(true, 100L, false, List.of(), List.of());

        var resposta = controller(repo, identidade("BVMF3", "B3SA3"))
                .opcoes("BVMF3", null).getBody();

        assertNotNull(resposta);
        assertEquals("BVMF3", resposta.simboloSolicitado());
        assertEquals("B3SA3", resposta.simboloCanonico());
        assertTrue(resposta.aliasAplicado());
        assertEquals("BASE_OPCOES_CONTAMINADA", resposta.status().name());
        assertTrue(resposta.motivo().contains("prefixo"));
        assertTrue(resposta.opcoes().isEmpty());
        assertEquals(0, repo.consultasPorAtivo);
        assertEquals(0, repo.consultasVencimentos);
    }

    @Test
    void cargaAusenteDiferenciaBaseVazia() {
        var repo = new RepoOpcoesFake(true, 0L, false, List.of(), List.of());

        var resposta = controller(repo, identidade("PETR4", "PETR4"))
                .opcoes("PETR4", null).getBody();

        assertNotNull(resposta);
        assertEquals("CARGA_OPCOES_AUSENTE", resposta.status().name());
        assertTrue(resposta.motivo().contains("carga"));
        assertTrue(resposta.opcoes().isEmpty());
    }

    @Test
    void cadeiaValidaMantemAliasEListaDados() {
        var opcao = new OpcaoB3DTO("B3SAK10", "12", "CALL", LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 11, 20), new BigDecimal("10.00"), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 10L, 3,
                new BigDecimal("1000.00"));
        var repo = new RepoOpcoesFake(true, 100L, true, List.of(opcao), List.of("202611"));

        var resposta = controller(repo, identidade("BVMF3", "B3SA3"))
                .opcoes("BVMF3", "202611").getBody();

        assertNotNull(resposta);
        assertEquals("TICKER_NORMALIZADO", resposta.status().name());
        assertEquals(List.of("202611"), resposta.vencimentos());
        assertEquals(List.of(opcao), resposta.opcoes());
        assertEquals("B3SA3", repo.ultimoAtivo);
        assertEquals("202611", repo.ultimoVencimento);
    }

    @Test
    void tickerSemCadeiaExplicaAusenciaSemInferirPorPrefixo() {
        var repo = new RepoOpcoesFake(true, 100L, true, List.of(), List.of("202611"));

        var resposta = controller(repo, identidade("ABCD3", "ABCD3"))
                .opcoes("ABCD3", null).getBody();

        assertNotNull(resposta);
        assertEquals("ABCD3", resposta.simboloSolicitado());
        assertEquals("ABCD3", resposta.simboloCanonico());
        assertFalse(resposta.aliasAplicado());
        assertEquals("SEM_CADEIA_VALIDA", resposta.status().name());
        assertTrue(resposta.motivo().contains("Não há cadeia"));
        assertEquals(List.of("202611"), resposta.vencimentos());
        assertTrue(resposta.opcoes().isEmpty());
        assertEquals("ABCD3", repo.ultimoAtivo);
    }

    @Test
    void chamadasRepetidasDeOpcoesSaoSomenteLeiturasDoRepositorio() {
        var opcao = new OpcaoB3DTO("PETRK10", "12", "CALL", LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 11, 20), new BigDecimal("10.00"), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 10L, 3,
                new BigDecimal("1000.00"));
        var repo = new RepoOpcoesFake(true, 100L, true, List.of(opcao), List.of("202611"));
        var controller = controller(repo, identidade("PETR4", "PETR4"));

        for (int i = 0; i < 5; i++) {
            var resposta = controller.opcoes("PETR4", "202611").getBody();
            assertNotNull(resposta);
            assertEquals("OK", resposta.status().name());
        }

        assertEquals(5, repo.consultasTabelaDisponivel);
        assertEquals(5, repo.consultasTotalRegistros);
        assertEquals(5, repo.consultasSuporteAtivoObjeto);
        assertEquals(5, repo.consultasPorAtivo);
        assertEquals(5, repo.consultasVencimentos);
    }

    static class RepoOpcoesFake extends RepositorioOpcoes {
        private final boolean tabela;
        private final long total;
        private final boolean ativoObjeto;
        private final List<OpcaoB3DTO> opcoes;
        private final List<String> vencimentos;
        int consultasTabelaDisponivel;
        int consultasTotalRegistros;
        int consultasSuporteAtivoObjeto;
        int consultasPorAtivo;
        int consultasVencimentos;
        String ultimoAtivo;
        String ultimoVencimento;

        RepoOpcoesFake(boolean tabela, long total, boolean ativoObjeto,
                       List<OpcaoB3DTO> opcoes, List<String> vencimentos) {
            super(null);
            this.tabela = tabela;
            this.total = total;
            this.ativoObjeto = ativoObjeto;
            this.opcoes = opcoes;
            this.vencimentos = vencimentos;
        }

        @Override public boolean tabelaDisponivel() {
            consultasTabelaDisponivel++;
            return tabela;
        }
        @Override public long totalRegistros() {
            consultasTotalRegistros++;
            return total;
        }
        @Override public boolean suportaAtivoObjeto() {
            consultasSuporteAtivoObjeto++;
            return ativoObjeto;
        }
        @Override public List<OpcaoB3DTO> porAtivo(String simbolo, String vencimento) {
            consultasPorAtivo++;
            ultimoAtivo = simbolo;
            ultimoVencimento = vencimento;
            return opcoes;
        }
        @Override public List<String> vencimentos(String simbolo) {
            consultasVencimentos++;
            return vencimentos;
        }
    }
}
