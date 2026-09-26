package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.CandleDiarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioCandleDiario extends JpaRepository<CandleDiarioEntity, Long> {

    Optional<CandleDiarioEntity> findBySimboloAndData(String simbolo, LocalDate data);

    List<CandleDiarioEntity> findBySimboloAndDataBetweenOrderByDataAsc(String simbolo, LocalDate inicio, LocalDate fim);

    List<CandleDiarioEntity> findBySimboloOrderByDataAsc(String simbolo);
}
