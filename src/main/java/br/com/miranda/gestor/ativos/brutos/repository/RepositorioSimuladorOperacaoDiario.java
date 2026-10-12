package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorOperacaoDiarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioSimuladorOperacaoDiario extends JpaRepository<SimuladorOperacaoDiarioEntity, Long> {

    /**
     * Verifica se já existe snapshot para uma operação em um pregão específico (idempotência).
     */
    Optional<SimuladorOperacaoDiarioEntity> findByOperacaoIdAndDataPregao(Long operacaoId, LocalDate dataPregao);

    /**
     * Snapshots de um pregão específico (para verificar integridade da carga).
     */
    List<SimuladorOperacaoDiarioEntity> findByDataPregao(LocalDate dataPregao);

    /**
     * Histórico de snapshots de uma operação.
     */
    List<SimuladorOperacaoDiarioEntity> findByOperacaoIdOrderByDataPregaoDesc(Long operacaoId);
}

