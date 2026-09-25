package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.dto.AnaliseConsolidadaDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaAnaliseIaDTO;

public final class MontadorDecisaoDeterministica {

    private MontadorDecisaoDeterministica() {
    }

    /**
     * Deriva a decisão consolidada a partir de regras fixas sobre os indicadores já calculados
     * em {@link ConsolidadorAnaliseAcao}, sem depender de nenhum modelo de IA.
     */
    public static RespostaAnaliseIaDTO montar(AnaliseConsolidadaDTO consolidado) {
        double percentualVenda = valorOuZero(consolidado.getPercentualSinaisVenda());
        double percentualCompra = 100.0 - percentualVenda;
        double margemSeguranca = valorOuZero(consolidado.getVariacaoMedia());
        int registros = consolidado.getQuantidadeRegistros() == null ? 0 : consolidado.getQuantidadeRegistros();

        String sentimento = sentimentoPor(percentualVenda);
        String risco = riscoPor(margemSeguranca);

        return RespostaAnaliseIaDTO.builder()
                .ativo(consolidado.getAtivo())
                .sentimento(sentimento)
                .forcaSinal(forcaSinalPor(percentualVenda, percentualCompra))
                .risco(risco)
                .confiancaAnalise(confiancaPor(registros))
                .resumo(resumoPor(consolidado, sentimento, risco))
                .analiseTecnica(String.format(
                        "Sinal predominante: %s (%.2f%% dos %d registros indicam venda).",
                        consolidado.getSinalPredominante(), percentualVenda, registros))
                .analiseFundamentalista(String.format(
                        "Margem de seguranca media no periodo: %.2f%%.", margemSeguranca))
                .possivelCenario(cenarioPor(margemSeguranca, percentualVenda))
                .recomendacao(consolidado.getSinalPredominante())
                .build();
    }

    private static String sentimentoPor(double percentualVenda) {
        if (percentualVenda >= 60) {
            return "NEGATIVO";
        }
        if (percentualVenda <= 40) {
            return "POSITIVO";
        }
        return "NEUTRO";
    }

    private static String forcaSinalPor(double percentualVenda, double percentualCompra) {
        double dominancia = Math.abs(percentualVenda - percentualCompra);
        if (dominancia >= 40) {
            return "FORTE";
        }
        if (dominancia >= 15) {
            return "MODERADA";
        }
        return "FRACA";
    }

    private static String riscoPor(double margemSeguranca) {
        if (margemSeguranca < 0) {
            return "ALTO";
        }
        if (margemSeguranca < 10) {
            return "MEDIO";
        }
        return "BAIXO";
    }

    private static double confiancaPor(int registros) {
        double confianca = Math.min(registros / 20.0, 1.0);
        return Math.round(confianca * 100.0) / 100.0;
    }

    private static String cenarioPor(double margemSeguranca, double percentualVenda) {
        if (margemSeguranca < 0 && percentualVenda >= 50) {
            return "Ativo aparenta sobrevalorizado, com predominancia de sinais de venda.";
        }
        if (margemSeguranca > 10 && percentualVenda < 50) {
            return "Ativo aparenta ter margem de seguranca favoravel, com predominancia de sinais de compra.";
        }
        return "Cenario misto, sem tendencia predominante clara.";
    }

    private static String resumoPor(AnaliseConsolidadaDTO consolidado, String sentimento, String risco) {
        String inicio = consolidado.getJanelaAnalise() == null ? "-" : consolidado.getJanelaAnalise().getInicio();
        String fim = consolidado.getJanelaAnalise() == null ? "-" : consolidado.getJanelaAnalise().getFim();

        return String.format(
                "Ativo %s: %d analises entre %s e %s, sentimento %s, risco %s.",
                consolidado.getAtivo(),
                consolidado.getQuantidadeRegistros(),
                inicio,
                fim,
                sentimento,
                risco
        );
    }

    private static double valorOuZero(Double valor) {
        return valor == null ? 0.0 : valor;
    }
}
