package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.PerfilEmpresaCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositorioPerfilEmpresaCache extends JpaRepository<PerfilEmpresaCacheEntity, Long> {

    Optional<PerfilEmpresaCacheEntity> findBySimbolo(String simbolo);
}
