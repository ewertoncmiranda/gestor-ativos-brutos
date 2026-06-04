package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.InsightAcao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InsightAcaoRepository extends JpaRepository<InsightAcao, Long> {

    // Query derivation
    List<InsightAcao> findBySimbolo(String simbolo);

    // Exemplo com native query (opcional)
    @Query(value = "SELECT * FROM insight_acao WHERE simbolo = :simbolo", nativeQuery = true)
    List<InsightAcao> findBySimboloNative(@Param("simbolo") String simbolo);
}

