package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorIaAnaliseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface RepositorioSimuladorIaAnalise extends JpaRepository<SimuladorIaAnaliseEntity, Long> {

    /**
     * Verifica se existe análise em cache para o ativo no dia (SIM-GES-5: não cobra token duas vezes).
     * Usa a unique constraint (ativo_id, DATE(data_analise)).
     */
    @Query("SELECT a FROM SimuladorIaAnaliseEntity a JOIN a.ativo at WHERE at.simbolo = :simbolo AND DATE(a.dataAnalise) = :data")
    Optional<SimuladorIaAnaliseEntity> findBySimboloAndDataAnalise(
            @Param("simbolo") String simbolo,
            @Param("data") LocalDate data);

    /**
     * Retorna a análise mais recente de um símbolo.
     */
    @Query("SELECT a FROM SimuladorIaAnaliseEntity a JOIN a.ativo at WHERE at.simbolo = :simbolo ORDER BY a.dataAnalise DESC")
    Optional<SimuladorIaAnaliseEntity> findFirstBySimboloOrderByDataAnaliseDesc(@Param("simbolo") String simbolo);
}

