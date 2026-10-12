package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioSimuladorAtivo extends JpaRepository<SimuladorAtivoEntity, Integer> {

    Optional<SimuladorAtivoEntity> findBySimbolo(String simbolo);

    List<SimuladorAtivoEntity> findByAtivoTrueOrderBySimboloAsc();

    List<SimuladorAtivoEntity> findAllByOrderBySimboloAsc();
}

