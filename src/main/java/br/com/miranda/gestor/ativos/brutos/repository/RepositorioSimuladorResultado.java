package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorResultadoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioSimuladorResultado extends JpaRepository<SimuladorResultadoEntity, Long> {

    /**
     * Lista resultados de todas as estratégias para um ativo no pregão mais recente.
     * Usa JOIN com simulador_ativo para filtrar por simbolo.
     */
    @Query(value = """
            SELECT r FROM SimuladorResultadoEntity r
            JOIN r.ativo a
            WHERE a.simbolo = :simbolo
              AND r.dataPregao = (SELECT MAX(r2.dataPregao)
                                   FROM SimuladorResultadoEntity r2
                                   JOIN r2.ativo a2
                                  WHERE a2.simbolo = :simbolo)
            """)
    List<SimuladorResultadoEntity> findMaisRecentePorSimbolo(@Param("simbolo") String simbolo);

    /**
     * Resultados de um ativo em uma data específica.
     */
    @Query(value = """
            SELECT r FROM SimuladorResultadoEntity r
            JOIN r.ativo a
            WHERE a.simbolo = :simbolo
              AND r.dataPregao = :dataPregao
            """)
    List<SimuladorResultadoEntity> findBySimboloAndDataPregao(
            @Param("simbolo") String simbolo,
            @Param("dataPregao") LocalDate dataPregao);

    /**
     * Todas as estratégias de um ativo em uma data. Se data_pregao for nula,
     * retorna o pregão mais recente disponível.
     */
    @Query(value = """
            SELECT r FROM SimuladorResultadoEntity r
            JOIN r.ativo a
            WHERE a.simbolo = :simbolo
              AND (:dataPregao IS NULL OR r.dataPregao = :dataPregao)
            ORDER BY r.versaoEstrategia
            """)
    List<SimuladorResultadoEntity> findResultados(
            @Param("simbolo") String simbolo,
            @Param("dataPregao") LocalDate dataPregao);

    @Query(value = """
            SELECT r.* FROM simulador_resultado r
            JOIN simulador_ativo a ON a.id = r.ativo_id
            WHERE a.simbolo = :simbolo
              AND r.data_pregao >= :inicio
              AND (r.versao_estrategia = :versao
                   OR r.versao_estrategia LIKE CONCAT(:estrategia, '%'))
            ORDER BY r.data_pregao
            """, nativeQuery = true)
    List<SimuladorResultadoEntity> findHistoricoEstrategia(
            @Param("simbolo") String simbolo,
            @Param("estrategia") String estrategia,
            @Param("versao") String versao,
            @Param("inicio") LocalDate inicio);

    @Query(value = """
            SELECT fechamento FROM cotacao_b3_diaria
            WHERE simbolo = :simbolo AND data_pregao = :data
            LIMIT 1
            """, nativeQuery = true)
    Optional<BigDecimal> fechamentoEm(@Param("simbolo") String simbolo, @Param("data") LocalDate data);

    @Query(value = """
            SELECT data_pregao FROM cotacao_b3_diaria
            WHERE simbolo = :simbolo AND data_pregao >= :data
            ORDER BY data_pregao
            LIMIT 1
            """, nativeQuery = true)
    Optional<LocalDate> primeiroPregaoEmOuApos(@Param("simbolo") String simbolo, @Param("data") LocalDate data);

    @Query(value = """
            SELECT data_pregao FROM cotacao_b3_diaria
            WHERE simbolo = :simbolo AND data_pregao > :data
            ORDER BY data_pregao
            LIMIT :offset, 1
            """, nativeQuery = true)
    Optional<LocalDate> pregaoDepoisDe(@Param("simbolo") String simbolo,
                                       @Param("data") LocalDate data,
                                       @Param("offset") int offset);
}

