package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorResultadoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

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
}

