package br.com.miranda.gestor.ativos.brutos.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OPR-GES-1: so SELECT, sem tabela responde vazio (nunca 500) e filtros combinam em AND com parametros nomeados. */
class RepositorioOperacionalTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 9);
    private static final String V = "OPR-2026.10.10-1";

    private NamedParameterJdbcTemplate jdbc;
    private RepositorioOperacional repo;

    @BeforeEach
    void preparar() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repo = new RepositorioOperacional(jdbc, new ObjectMapper());
    }

    private void semAsTabelas() {
        var erro = new BadSqlGrammarException("query", "SELECT", new SQLException("Table doesn't exist"));
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenThrow(erro);
        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class))).thenThrow(erro);
    }

    @Test
    void semAsTabelasCadaLeituraDevolveVazioENuncaLevanta() {
        semAsTabelas();
        assertTrue(repo.regraAtiva().isEmpty());
        assertTrue(repo.regra(V).isEmpty());
        assertTrue(repo.versaoDoUltimoDiario().isEmpty());
        assertTrue(repo.versaoDaUltimaOperacao().isEmpty());
        assertTrue(repo.ultimoDiario(V).isEmpty());
        assertTrue(repo.diario(V, null, null, 10).isEmpty());
        assertEquals(0, repo.contarDiario(V));
        assertTrue(repo.primeiraDataDoDiario(V).isEmpty());
        assertTrue(repo.drawdownMinimo(V).isEmpty());
        assertTrue(repo.contarOperacoesPorStatus(V).isEmpty());
        assertTrue(repo.operacoes(V, null, null, null, null, null, 10).isEmpty());
        assertTrue(repo.ultimaDataDeBloqueio(V).isEmpty());
        assertTrue(repo.bloqueios(V, DIA, null, 10).isEmpty());
        assertEquals(0L, repo.eventosPendentes(V));
        assertTrue(repo.ultimoPregaoDisponivel().isEmpty());
        assertFalse(repo.tabelasDisponiveis());
    }

    @Test
    void tabelasDisponiveisQuandoAsQuatroResponderem() {
        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class))).thenReturn(List.of());
        assertTrue(repo.tabelasDisponiveis());
    }

    @Test
    void todoSqlEmitidoEhSelectEOGestorNuncaGrava() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class))).thenReturn(List.of());
        repo.regraAtiva();
        repo.regra(V);
        repo.versaoDoUltimoDiario();
        repo.versaoDaUltimaOperacao();
        repo.ultimoDiario(V);
        repo.diario(V, DIA, DIA, 10);
        repo.contarDiario(V);
        repo.primeiraDataDoDiario(V);
        repo.drawdownMinimo(V);
        repo.contarOperacoesPorStatus(V);
        repo.operacoes(V, "FECHADA", "PETR4", DIA, DIA, "STOP", 10);
        repo.ultimaDataDeBloqueio(V);
        repo.bloqueios(V, DIA, "PETR4", 10);
        repo.eventosPendentes(V);
        repo.ultimoPregaoDisponivel();
        repo.tabelasDisponiveis();

        List<String> metodos = Mockito.mockingDetails(jdbc).getInvocations().stream()
                .map(i -> i.getMethod().getName()).distinct().toList();
        assertEquals(List.of("query"), metodos, "o repositorio OPR so pode chamar query()");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        sql.getAllValues().forEach(s -> assertTrue(s.stripLeading().toUpperCase().startsWith("SELECT"), s));
    }

    @Test
    void filtrosDeOperacoesCombinamEmAndComParametrosNomeados() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        repo.operacoes(V, "FECHADA", "PETR4", DIA.minusDays(30), DIA, "STOP", 50);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        String s = sql.getValue();
        for (String trecho : List.of("versao_regra = :v", "AND status = :status", "AND simbolo = :simbolo",
                "AND data_decisao_entrada >= :ini", "AND data_decisao_entrada <= :fim", "AND motivo_saida = :motivo",
                "LIMIT :n")) {
            assertTrue(s.contains(trecho), "faltou: " + trecho);
        }
        assertFalse(s.contains("PETR4") || s.contains("FECHADA"), "valor nunca concatenado no SQL");
        SqlParameterSource p = params.getValue();
        assertEquals("FECHADA", p.getValue("status"));
        assertEquals("PETR4", p.getValue("simbolo"));
        assertEquals("STOP", p.getValue("motivo"));
        assertEquals(50, p.getValue("n"));
    }

    @Test
    void semFiltrosOSqlNaoTemClausulaExtra() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        repo.operacoes(V, null, null, null, null, null, 10);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        for (String clausula : List.of(":status", ":simbolo", ":ini", ":fim", ":motivo")) {
            assertFalse(sql.getValue().contains(clausula), clausula);
        }
    }

    @Test
    void limiteEhSempreEntreUmEOMaximo() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        repo.operacoes(V, null, null, null, null, null, 999999);
        repo.operacoes(V, null, null, null, null, null, 0);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, Mockito.times(2)).query(anyString(), params.capture(), any(RowMapper.class));
        assertEquals(RepositorioOperacional.LIMITE_MAXIMO, params.getAllValues().get(0).getValue("n"));
        assertEquals(1, params.getAllValues().get(1).getValue("n"));
    }

    @Test
    void bloqueiosFiltramPorTipoDeBloqueioEPelaDataPedida() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        repo.bloqueios(V, DIA, "ABCD3", 10);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(sql.getValue().contains("tipo IN ('BLOQUEIO_DADO','BLOQUEIO_LIQUIDEZ')"));
        assertTrue(sql.getValue().contains("data_pregao = :data"));
        assertTrue(sql.getValue().contains("AND simbolo = :simbolo"));
    }
}
