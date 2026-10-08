package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.external.CvmTickerEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoBaseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Leitura da tela "Base" - o universo amplo (cvm_ticker/cvm_empresa,
 * TASK-59), cruzado com o ultimo fechamento oficial (cotacao_b3_diaria) e
 * duas flags: tem fundamento carregado (indicador_fundamentalista) e ja e
 * favorito do usuario (ativo_monitorado). So leitura - nenhuma tabela nova.
 */
@Repository
public interface RepositorioBaseAtivos extends JpaRepository<CvmTickerEntity, String> {

    @Query(
            value = """
                    SELECT t.simbolo AS simbolo,
                           e.denominacao AS nome,
                           e.setor AS setor,
                           c.fechamento AS ultimoFechamento,
                           c.data_pregao AS dataUltimoFechamento,
                           EXISTS(SELECT 1 FROM indicador_fundamentalista i WHERE i.simbolo = t.simbolo) AS temFundamento,
                           EXISTS(SELECT 1 FROM ativo_monitorado m WHERE m.simbolo = t.simbolo AND m.ativo = TRUE) AS favorito,
                           e.situacao_registro AS situacaoRegistro,
                           e.uf_municipio AS ufMunicipio
                    FROM cvm_ticker t
                    JOIN cvm_empresa e ON e.cnpj = t.cnpj
                    LEFT JOIN cotacao_b3_diaria c
                           ON c.simbolo = t.simbolo
                          AND c.data_pregao = (SELECT MAX(c2.data_pregao) FROM cotacao_b3_diaria c2 WHERE c2.simbolo = t.simbolo)
                    WHERE t.ativo = TRUE
                      AND (:q IS NULL OR t.simbolo LIKE CONCAT(UPPER(:q), '%') OR e.denominacao LIKE CONCAT('%', :q, '%'))
                      AND (:setor IS NULL OR e.setor = :setor)
                      AND (:uf IS NULL OR e.uf_municipio = :uf)
                    ORDER BY t.simbolo
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM cvm_ticker t
                    JOIN cvm_empresa e ON e.cnpj = t.cnpj
                    WHERE t.ativo = TRUE
                      AND (:q IS NULL OR t.simbolo LIKE CONCAT(UPPER(:q), '%') OR e.denominacao LIKE CONCAT('%', :q, '%'))
                      AND (:setor IS NULL OR e.setor = :setor)
                      AND (:uf IS NULL OR e.uf_municipio = :uf)
                    """,
            nativeQuery = true)
    Page<AtivoBaseDTO> buscar(@Param("q") String q, @Param("setor") String setor, @Param("uf") String uf,
            Pageable pagina);

    /** Setores distintos com pelo menos um ticker ativo, para o filtro da tela. */
    @Query(
            value = """
                    SELECT DISTINCT e.setor
                    FROM cvm_empresa e
                    JOIN cvm_ticker t ON t.cnpj = e.cnpj
                    WHERE t.ativo = TRUE AND e.setor IS NOT NULL
                    ORDER BY e.setor
                    """,
            nativeQuery = true)
    List<String> setoresDisponiveis();
}
