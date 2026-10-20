package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.OpcaoB3DTO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mapeamento de ResultSet sintético: nenhum banco ou contexto Spring é iniciado. */
class MicroMercadoRepositoryTest {
    private final LocalDate dia = LocalDate.of(2026, 10, 19);

    @Test
    void pregaoOficialPreservaNovosCamposENulos() throws Exception {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var rs = mock(ResultSet.class);
        when(rs.getDate("data_pregao")).thenReturn(Date.valueOf(dia));
        when(rs.getString("simbolo")).thenReturn("PETR4");
        when(rs.getString("marca_ex")).thenReturn("EDJ");
        when(rs.getBigDecimal("preco_medio")).thenReturn(new BigDecimal("40.12"));
        when(rs.getBigDecimal("melhor_oferta_compra")).thenReturn(new BigDecimal("40"));
        doAnswer(call -> {
            String sql = call.getArgument(0);
            assertTrue(sql.contains("preco_medio, melhor_oferta_compra, melhor_oferta_venda, marca_ex"));
            RowMapper<RepositorioPregoes.Pregao> mapper = call.getArgument(2);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<RepositorioPregoes.Pregao>>any());
        var p = new RepositorioPregoes(jdbc).oficiais(List.of("PETR4"), dia, dia).getFirst();
        assertEquals(new BigDecimal("40.12"), p.precoMedio());
        assertEquals(new BigDecimal("40"), p.melhorOfertaCompra());
        assertNull(p.melhorOfertaVenda());
        assertEquals("EDJ", p.marcaEx());
        verify(jdbc, times(1)).query(anyString(), any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<RepositorioPregoes.Pregao>>any());
    }

    @Test
    void opcaoMapeiaOfertasIndiceEMantemFiltroDeValidade() throws Exception {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var rs = mock(ResultSet.class);
        when(rs.getDate("data_pregao")).thenReturn(Date.valueOf(dia));
        when(rs.getDate("data_vencimento")).thenReturn(Date.valueOf(dia.plusMonths(1)));
        when(rs.getString("indice_correcao")).thenReturn("1");
        when(rs.getBigDecimal("melhor_oferta_compra")).thenReturn(new BigDecimal("9"));
        when(rs.getBigDecimal("melhor_oferta_venda")).thenReturn(new BigDecimal("11"));
        doAnswer(call -> {
            String sql = call.getArgument(0);
            assertTrue(sql.contains("melhor_oferta_compra, melhor_oferta_venda, indice_correcao"));
            assertTrue(sql.contains("status_validacao = 'VALIDA'"));
            assertTrue(sql.contains("WHERE ativo_objeto = :ativo"));
            assertTrue(sql.contains("MONTH(data_vencimento) = :mes"));
            RowMapper<OpcaoB3DTO> mapper = call.getArgument(2);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<OpcaoB3DTO>>any());
        var o = new RepositorioOpcoes(jdbc).porAtivo("PETR", "202611").getFirst();
        assertEquals("1", o.indiceCorrecao());
        assertEquals(new BigDecimal("10"), o.precoJusto());
        assertEquals(0, new BigDecimal("20").compareTo(o.spreadPercentual()));
    }
}
