package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.CotacaoAtualEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositorioCotacaoAtual extends JpaRepository<CotacaoAtualEntity, Long> {

    Optional<CotacaoAtualEntity> findBySimbolo(String simbolo);
}
