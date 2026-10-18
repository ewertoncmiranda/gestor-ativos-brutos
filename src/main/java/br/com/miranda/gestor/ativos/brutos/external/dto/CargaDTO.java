package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cargas do ETL (Plano CARGA, CARGA-GES-1): leitura de carga_execucao,
 * carga_etapa, etl_execucao e vw_carga_ultima (infra V44). O gestor so le;
 * quem executa e grava e o ETL.
 */
public final class CargaDTO {

    private CargaDTO() {
    }

    /** Uma execucao. Na listagem, etapas e arquivos vem vazios. */
    public record Execucao(String id, String tipo, String solicitadaPor, JsonNode parametros, String status,
                           String versaoEtl, LocalDateTime criadaEm, LocalDateTime iniciadaEm,
                           LocalDateTime finalizadaEm, Long duracaoMs, int etapasOk, int etapasAlerta,
                           int etapasFalha, String mensagemErro, List<Etapa> etapas, List<Arquivo> arquivos) {
    }

    /** metricas = o que foi carregado (anos, lidos, gravados, rejeitados por motivo...). */
    public record Etapa(String etapa, int ordem, String status, LocalDateTime iniciadaEm,
                        LocalDateTime finalizadaEm, Long duracaoMs, JsonNode metricas,
                        List<String> alertas, List<String> erros) {
    }

    /** Arquivo baixado pela carga (etl_execucao). */
    public record Arquivo(String fonte, String competencia, String arquivo, String etag, int linhasCarregadas,
                          String status, LocalDateTime iniciadoEm, LocalDateTime finalizadoEm,
                          String mensagemErro) {
    }

    /** Por etapa: ultima tentativa e ultimo sucesso (vw_carga_ultima). */
    public record Ultima(String etapa, LocalDateTime ultimaTentativaEm, String ultimoStatus,
                         String ultimaExecucaoId, LocalDateTime ultimoSucessoEm, String ultimoSucessoExecucaoId) {
    }

    /** Pedido de carga feito pelo painel (CARGA-GES-2). */
    public record Disparo(String tipo, List<Integer> anos, List<String> simbolos, List<String> etapas,
                          Boolean forcar) {
    }
}
