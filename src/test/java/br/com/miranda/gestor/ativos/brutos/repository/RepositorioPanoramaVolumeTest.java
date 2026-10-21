package br.com.miranda.gestor.ativos.brutos.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RepositorioPanoramaVolumeTest {
    @Test void consultaVolumeUsaColunaFinanceiraDoSchema() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new RepositorioPanoramaMercado(jdbc).volumeAnormal(LocalDate.of(2026,10,19), LocalDate.of(2026,9,21));
        verify(jdbc).query(argThat((String sql) -> sql.contains("l.volume_financeiro_medio_63d >= 1000000")
                        && !sql.contains("l.volume_medio_63d") && sql.contains("l.presenca_63d >= 0.80")),
                any(RowMapper.class), eq(java.sql.Date.valueOf("2026-10-19")),
                eq(java.sql.Date.valueOf("2026-09-21")), eq(java.sql.Date.valueOf("2026-10-19")), eq(java.sql.Date.valueOf("2026-10-19")));
    }
}
