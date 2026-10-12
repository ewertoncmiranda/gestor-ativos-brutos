package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Leituras reais usadas pelos endpoints analiticos do simulador. */
@Repository
@RequiredArgsConstructor
public class RepositorioSimuladorAnalise {

    private final NamedParameterJdbcTemplate jdbc;

    public record Sinal(LocalDate dataPregao, Integer quintil, String versaoEstrategia) {
    }

    public record Cotacao(LocalDate dataPregao, BigDecimal fechamento) {
    }

    public record PontoCdi(LocalDate data, BigDecimal valor) {
    }

    public List<Sinal> sinais(String simbolo, LocalDate inicio) {
        return jdbc.query("""
                SELECT r.data_pregao, r.quintil, r.versao_estrategia
                  FROM simulador_resultado r
                  JOIN simulador_ativo a ON a.id = r.ativo_id
                 WHERE a.simbolo = :simbolo
                   AND r.data_pregao >= :inicio
                 ORDER BY r.data_pregao, r.versao_estrategia
                """, new MapSqlParameterSource("simbolo", simbolo).addValue("inicio", inicio),
                (rs, i) -> new Sinal(rs.getDate("data_pregao").toLocalDate(),
                        rs.getObject("quintil", Integer.class), rs.getString("versao_estrategia")));
    }

    public List<Sinal> sinaisNaData(String simbolo, LocalDate dataPregao) {
        return jdbc.query("""
                SELECT r.data_pregao, r.quintil, r.versao_estrategia
                  FROM simulador_resultado r
                  JOIN simulador_ativo a ON a.id = r.ativo_id
                 WHERE a.simbolo = :simbolo
                   AND r.data_pregao = :data
                 ORDER BY r.versao_estrategia
                """, new MapSqlParameterSource("simbolo", simbolo).addValue("data", dataPregao),
                (rs, i) -> new Sinal(rs.getDate("data_pregao").toLocalDate(),
                        rs.getObject("quintil", Integer.class), rs.getString("versao_estrategia")));
    }

    public List<Cotacao> cotacoes(String simbolo, LocalDate inicio) {
        return jdbc.query("""
                SELECT data_pregao, fechamento
                  FROM cotacao_b3_diaria
                 WHERE simbolo = :simbolo
                   AND data_pregao >= :inicio
                   AND fechamento IS NOT NULL
                 ORDER BY data_pregao
                """, new MapSqlParameterSource("simbolo", simbolo).addValue("inicio", inicio),
                (rs, i) -> new Cotacao(rs.getDate("data_pregao").toLocalDate(), rs.getBigDecimal("fechamento")));
    }

    public List<PontoCdi> cdi(LocalDate inicio, LocalDate fim) {
        return jdbc.query("""
                SELECT data, valor
                  FROM indice_macro
                 WHERE codigo_serie = 'CDI'
                   AND data BETWEEN :inicio AND :fim
                   AND valor IS NOT NULL
                 ORDER BY data
                """, new MapSqlParameterSource("inicio", inicio).addValue("fim", fim),
                (rs, i) -> new PontoCdi(rs.getDate("data").toLocalDate(), rs.getBigDecimal("valor")));
    }
}
