package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.HistoricoAcaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositorioHistoricoAcao extends JpaRepository<HistoricoAcaoEntity, Long> {

    /** Cotação mais recente do símbolo; usa o índice idx_simbolo_timestamp. */
    Optional<HistoricoAcaoEntity> findFirstBySimboloOrderByTimestampDesc(String simbolo);
}
