package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioAtivoMonitorado extends JpaRepository<AtivoMonitoradoEntity, Long> {

    Optional<AtivoMonitoradoEntity> findBySimbolo(String simbolo);

    List<AtivoMonitoradoEntity> findByAtivoTrue();

    List<AtivoMonitoradoEntity> findAllByOrderBySimboloAsc();

    List<AtivoMonitoradoEntity> findByTipoColeta(TipoColeta tipoColeta);

    /** Favoritos ativos, em ordem alfabetica - a lista que a tela de Favoritos mostra. */
    List<AtivoMonitoradoEntity> findByTipoColetaAndAtivoTrueOrderBySimboloAsc(TipoColeta tipoColeta);

    /** Toca so a data da ultima coleta: UPDATE em massa do JPQL nao incrementa a versao. */
    @Transactional
    @Modifying
    @Query("UPDATE AtivoMonitoradoEntity a SET a.atualizadoEm = :quando WHERE a.id = :id")
    int marcarAtualizado(@Param("id") Long id, @Param("quando") LocalDateTime quando);
}
