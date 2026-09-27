package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.ProventoDistribuidoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioProventoDistribuido extends JpaRepository<ProventoDistribuidoEntity, Long> {

    Optional<ProventoDistribuidoEntity> findBySimboloAndIsinAndTipoAndDataPagamento(
            String simbolo, String isin, String tipo, LocalDate dataPagamento);

    List<ProventoDistribuidoEntity> findBySimboloOrderByDataPagamentoDesc(String simbolo);
}
