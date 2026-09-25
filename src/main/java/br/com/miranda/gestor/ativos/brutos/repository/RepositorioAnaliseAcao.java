package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioAnaliseAcao extends JpaRepository<AnaliseAcaoEntity, Long> {

    List<AnaliseAcaoEntity> findBySimbolo(String simbolo);

    Optional<AnaliseAcaoEntity> findFirstBySimboloOrderByDataAnaliseDesc(String simbolo);

    @Query(value = "SELECT * FROM insight_acao WHERE simbolo = :simbolo", nativeQuery = true)
    List<AnaliseAcaoEntity> buscarPorSimboloConsultaNativa(@Param("simbolo") String simbolo);
}

