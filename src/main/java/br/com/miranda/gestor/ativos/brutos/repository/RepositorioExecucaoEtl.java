package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * Linhas do gestor em etl_execucao (contrato 4.2 do plano de atualizacao
 * diaria): hoje so BRAPI_ORCAMENTO. A tabela e do ETL; aqui so se insere.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioExecucaoEtl {

    private final NamedParameterJdbcTemplate jdbc;

    /** Registra um ERRO finalizado agora; `arquivo` e obrigatorio na tabela. */
    public void registrarErro(String fonte, String competencia, String arquivo, String mensagem) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("fonte", fonte);
        parametros.put("competencia", competencia);
        parametros.put("arquivo", arquivo);
        parametros.put("mensagem", mensagem);
        jdbc.update("INSERT INTO etl_execucao (fonte, competencia, arquivo, status, linhas_carregadas, "
                + "mensagem_erro, finalizado_em) VALUES (:fonte, :competencia, :arquivo, 'ERRO', 0, :mensagem, NOW())",
                parametros);
    }
}
