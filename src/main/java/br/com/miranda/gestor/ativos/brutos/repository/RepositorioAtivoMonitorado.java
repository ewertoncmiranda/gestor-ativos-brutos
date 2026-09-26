package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioAtivoMonitorado extends JpaRepository<AtivoMonitoradoEntity, Long> {

    Optional<AtivoMonitoradoEntity> findBySimbolo(String simbolo);

    List<AtivoMonitoradoEntity> findByAtivoTrue();

    List<AtivoMonitoradoEntity> findAllByOrderBySimboloAsc();

    List<AtivoMonitoradoEntity> findByTipoColeta(TipoColeta tipoColeta);
}
