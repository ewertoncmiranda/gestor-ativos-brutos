package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Opiniao por horizonte de um ativo (TASK-OPI-1; tabela opiniao_ia, infra
 * V22, escrita pelo gerar-insights). Um item por horizonte do pregao mais
 * recente: a leitura do modelo local quando existe, senao a da regra. O
 * {@code aviso} vai sempre, inclusive com a lista vazia.
 */
public record OpiniaoAtivoDTO(String simbolo, LocalDate dataPregao, String aviso, List<Horizonte> horizontes) {

    public static final String AVISO =
            "Leitura automática dos números, regra experimental. Não é recomendação de investimento.";

    /**
     * @param opiniao SINAL_POSITIVO | SINAL_NEGATIVO | SINAL_NEUTRO | SEM_BASE
     * @param risco   RISCO_BAIXO | RISCO_MEDIO | RISCO_ALTO
     * @param origem  MODELO (modelo local) | REGRA
     */
    public record Horizonte(LocalDate dataPregao, int horizontePregoes, String opiniao, String risco,
                            List<Justificativa> justificativa, List<String> oQueInvalida,
                            List<String> dadosAusentes, List<Evidencia> evidencias,
                            String modelo, String origem) {
    }

    public record Justificativa(String evidenciaId, String leitura) {
    }

    public record Evidencia(String id, String rotulo, String valor, Integer direcao) {
    }
}
