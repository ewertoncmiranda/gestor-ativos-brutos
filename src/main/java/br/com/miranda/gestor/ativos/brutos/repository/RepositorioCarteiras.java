package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioCarteiras {
    private final JdbcTemplate jdbc;

    public RepositorioCarteiras(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean schemaDisponivel() {
        Integer tabelas = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN ('carteira', 'carteira_ativo')
                """, Integer.class);
        return tabelas != null && tabelas == 2;
    }

    public List<CarteiraDTO> listarCarteiras() {
        return jdbc.query("""
                SELECT id, nome, estrategia, cor, moeda_base, status, criada_em
                FROM carteira WHERE status = 'ATIVA' ORDER BY nome, id
                """, (rs, row) -> new CarteiraDTO(rs.getString("id"), rs.getString("nome"),
                rs.getString("estrategia"), rs.getString("cor"), rs.getString("moeda_base"),
                rs.getString("status"), rs.getObject("criada_em", LocalDateTime.class), List.of()));
    }

    public Optional<CarteiraDTO> buscar(String id) {
        List<CarteiraDTO> resultados = jdbc.query("""
                SELECT id, nome, estrategia, cor, moeda_base, status, criada_em
                FROM carteira WHERE id = ?
                """, (rs, row) -> new CarteiraDTO(rs.getString("id"), rs.getString("nome"),
                rs.getString("estrategia"), rs.getString("cor"), rs.getString("moeda_base"),
                rs.getString("status"), rs.getObject("criada_em", LocalDateTime.class), List.of()), id);
        return resultados.stream().findFirst();
    }

    public List<CarteiraDTO.AtivoDTO> listarAtivos(String carteiraId) {
        return jdbc.query("""
                SELECT simbolo, data_entrada, capital_alocado
                FROM carteira_ativo WHERE carteira_id = ? ORDER BY simbolo
                """, (rs, row) -> new CarteiraDTO.AtivoDTO(rs.getString("simbolo"),
                rs.getObject("data_entrada", LocalDate.class), rs.getBigDecimal("capital_alocado"),
                "ALOCACAO_SIMULADA_LEGADA"), carteiraId);
    }

    public void criar(String id, String nome, String estrategia, String cor, String moeda) {
        jdbc.update("""
                INSERT INTO carteira (id, nome, estrategia, cor, moeda_base)
                VALUES (?, ?, ?, ?, ?)
                """, id, nome, estrategia, cor, moeda);
    }

    public void criarImportada(String id, String nome, String estrategia, String cor, String moeda,
                               LocalDateTime criadaEm) {
        jdbc.update("""
                INSERT INTO carteira (id, nome, estrategia, cor, moeda_base, criada_em)
                VALUES (?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP))
                """, id, nome, estrategia, cor, moeda, criadaEm == null ? null : criadaEm.withNano(0));
    }

    public int atualizar(String id, String nome, String estrategia, String cor, String moeda) {
        return jdbc.update("""
                UPDATE carteira SET nome = ?, estrategia = ?, cor = ?, moeda_base = ?
                WHERE id = ? AND status = 'ATIVA'
                """, nome, estrategia, cor, moeda, id);
    }

    public int arquivar(String id) {
        return jdbc.update("UPDATE carteira SET status = 'ARQUIVADA' WHERE id = ? AND status = 'ATIVA'", id);
    }

    public void salvarAtivo(String carteiraId, String simbolo, LocalDate data, BigDecimal capital) {
        jdbc.update("""
                INSERT INTO carteira_ativo (carteira_id, simbolo, data_entrada, capital_alocado)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE data_entrada = VALUES(data_entrada), capital_alocado = VALUES(capital_alocado)
                """, carteiraId, simbolo, data, capital);
    }

    public int removerAtivo(String carteiraId, String simbolo) {
        return jdbc.update("DELETE FROM carteira_ativo WHERE carteira_id = ? AND simbolo = ?", carteiraId, simbolo);
    }
}
