package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioIndiceMacro extends JpaRepository<IndiceMacroEntity, Long> {

    Optional<IndiceMacroEntity> findByCodigoSerieAndData(String codigoSerie, LocalDate data);

    Optional<IndiceMacroEntity> findFirstByCodigoSerieOrderByDataDesc(String codigoSerie);

    List<IndiceMacroEntity> findByCodigoSerieOrderByDataDesc(String codigoSerie);
}
