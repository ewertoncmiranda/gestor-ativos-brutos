package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.IndicadorFundamentalistaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositorioIndicadorFundamentalista
        extends JpaRepository<IndicadorFundamentalistaEntity, Long> {

    /**
     * Fundamento mais recente do símbolo para um tipo de período
     * (ANUAL, TRIMESTRAL ou TTM).
     */
    Optional<IndicadorFundamentalistaEntity> findFirstBySimboloAndTipoPeriodoOrderByPeriodoDesc(
            String simbolo, String tipoPeriodo);

    /** Mais recente de qualquer tipo, quando o chamador não especifica. */
    Optional<IndicadorFundamentalistaEntity> findFirstBySimboloOrderByPeriodoDesc(String simbolo);
}
