package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.ComunicadoCvmEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Leitura de {@code comunicado_cvm}. A tabela guarda CNPJ; o ticker vem de
 * {@code cvm_ticker}, mantida pelo ETL (contrato {@code infra#CTR-08}).
 */
@Repository
public interface RepositorioComunicadoCvm extends JpaRepository<ComunicadoCvmEntity, Long> {

    /** Linha do tempo de um ticker, mais recente primeiro. */
    @Query(
            value = """
                    SELECT c.* FROM comunicado_cvm c
                    JOIN cvm_ticker t ON t.cnpj = c.cnpj
                    WHERE t.simbolo = :simbolo
                      AND c.categoria IN (:categorias)
                      AND c.data_entrega BETWEEN :desde AND :ate
                    ORDER BY c.data_entrega DESC, c.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM comunicado_cvm c
                    JOIN cvm_ticker t ON t.cnpj = c.cnpj
                    WHERE t.simbolo = :simbolo
                      AND c.categoria IN (:categorias)
                      AND c.data_entrega BETWEEN :desde AND :ate
                    """,
            nativeQuery = true)
    Page<ComunicadoCvmEntity> buscarPorSimbolo(
            @Param("simbolo") String simbolo,
            @Param("categorias") Collection<String> categorias,
            @Param("desde") LocalDate desde,
            @Param("ate") LocalDate ate,
            Pageable pagina);

    /** Documentos das companhias pedidas num período, para montar a edição da newsletter. */
    List<ComunicadoCvmEntity> findByCnpjInAndCategoriaInAndDataEntregaBetween(
            Collection<String> cnpjs, Collection<String> categorias, LocalDate desde, LocalDate ate);

    /**
     * Ticker -> CNPJ da carteira monitorada ativa. Devolve {@code [simbolo, cnpj]}.
     * Companhia com dois tickers monitorados (PETR3 e PETR4) aparece duas vezes.
     */
    @Query(
            value = """
                    SELECT t.simbolo, t.cnpj FROM cvm_ticker t
                    JOIN ativo_monitorado a ON a.simbolo = t.simbolo
                    WHERE a.ativo = TRUE
                    ORDER BY t.simbolo
                    """,
            nativeQuery = true)
    List<Object[]> tickersMonitorados();

    /** Até quando há dado: a CVM republica a base ~1x/semana. */
    @Query("SELECT MAX(c.dataEntrega) FROM ComunicadoCvmEntity c")
    Optional<LocalDate> dataEntregaMaisRecente();
}
