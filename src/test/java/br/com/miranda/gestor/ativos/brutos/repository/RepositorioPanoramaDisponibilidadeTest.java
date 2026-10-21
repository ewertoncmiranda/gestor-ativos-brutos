package br.com.miranda.gestor.ativos.brutos.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RepositorioPanoramaDisponibilidadeTest {
    @Test void agregadosSqlNulosMantemIndicadoresAusentesSemExcecao() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.singletonList(null));
        var macro = new RepositorioPanoramaMercado(jdbc).macro(LocalDate.of(2026,10,20), LocalDate.of(2026,10,20));
        assertNull(macro.selic()); assertNull(macro.cdiPeriodo()); assertNull(macro.dolar()); assertNull(macro.ipca12m());
    }
    @Test void listaVaziaTambemMantemIndicadoresAusentes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        var macro = new RepositorioPanoramaMercado(jdbc).macro(LocalDate.of(2026,10,20), LocalDate.of(2026,10,20));
        assertNull(macro.selic()); assertNull(macro.cdiPeriodo()); assertNull(macro.dolar()); assertNull(macro.ipca12m());
    }
    @Test void valoresZeroNaoSaoConfundidosComAusencia() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(BigDecimal.ZERO));
        var macro = new RepositorioPanoramaMercado(jdbc).macro(LocalDate.of(2026,10,20), LocalDate.of(2026,10,20));
        assertEquals(0.0, macro.selic()); assertEquals(0.0, macro.cdiPeriodo()); assertEquals(0.0, macro.dolar()); assertEquals(0.0, macro.ipca12m());
    }
    @Test void dataComumDependeDeLiquidezEPrecoDoMesmoSimboloEDia() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var ate = LocalDate.of(2026,10,20);
        when(jdbc.queryForObject(anyString(), eq(java.sql.Date.class), eq(java.sql.Date.valueOf(ate))))
                .thenReturn(java.sql.Date.valueOf("2026-10-19"), null);
        var repo = new RepositorioPanoramaMercado(jdbc);
        assertEquals(LocalDate.of(2026,10,19), repo.ultimoPregaoComLiquidez(ate).orElseThrow());
        assertTrue(repo.ultimoPregaoComLiquidez(ate).isEmpty());
        verify(jdbc, times(2)).queryForObject(argThat((String sql) -> sql.contains("c.data_pregao = l.data_pregao")
                && sql.contains("c.simbolo = l.simbolo") && sql.contains("l.data_pregao <= ?")), eq(java.sql.Date.class), eq(java.sql.Date.valueOf(ate)));
    }
}
