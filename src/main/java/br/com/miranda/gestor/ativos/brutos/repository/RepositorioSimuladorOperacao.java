package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorOperacaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepositorioSimuladorOperacao extends JpaRepository<SimuladorOperacaoEntity, Long> {

    /**
     * Lista operações ativas (não FECHADAS) ordenadas por data de entrada.
     */
    List<SimuladorOperacaoEntity> findByStatusNotOrderByDataEntradaDesc(SimuladorOperacaoEntity.StatusOperacao status);

    /**
     * Operações de um ativo com status específico.
     */
    @Query("SELECT o FROM SimuladorOperacaoEntity o JOIN o.ativo a WHERE a.simbolo = :simbolo AND o.status = :status")
    List<SimuladorOperacaoEntity> findBySimboloAndStatus(
            @Param("simbolo") String simbolo,
            @Param("status") SimuladorOperacaoEntity.StatusOperacao status);

    /**
     * Operações PENDENTES prontas para abrir (data_entrada <= hoje).
     */
    List<SimuladorOperacaoEntity> findByStatus(SimuladorOperacaoEntity.StatusOperacao status);
}

