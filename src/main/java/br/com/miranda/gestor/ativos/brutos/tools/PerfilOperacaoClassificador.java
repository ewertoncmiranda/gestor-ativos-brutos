package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.dto.FundamentosAtivoDTO.ConfluenciaSinaisDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Deriva, a partir do MESMO detalhes_json de um único ciclo de análise (a fonte
 * de GET /analises/{simbolo}/fundamentos), um perfil de operação e riscos
 * separados de compra/venda - reaproveitando os sinais categóricos que o
 * gerar-insights já calcula (sinal_momentum, sinal_reversao, zona_52w,
 * classificacao_earnings_yield, margens de segurança de Graham), sem nenhuma
 * consulta ou dado novo.
 *
 * Deliberadamente não calcula nenhuma probabilidade de sucesso: a confluência
 * de sinais é só uma contagem de concordância, não uma taxa de acerto medida
 * (isso exigiria acompanhar o resultado futuro de recomendações passadas, que
 * não existe hoje).
 */
public final class PerfilOperacaoClassificador {

    private static final double MARGEM_LONGO_PRAZO_MINIMA = 20.0;
    private static final double MARGEM_RISCO_MINIMA = 10.0;

    private PerfilOperacaoClassificador() {
    }

    public static Resultado classificar(JsonNode detalhes) {
        if (detalhes == null || detalhes.isMissingNode()) {
            return Resultado.vazio();
        }

        String sinalMomentum = texto(detalhes, "contexto_tecnico_serie", "sinal_momentum");
        String sinalReversao = texto(detalhes, "contexto_tecnico_serie", "sinal_reversao");
        String zona52w = texto(detalhes, "contexto_tecnico", "zona_52w");
        String classificacaoEarningsYield = texto(detalhes, "valuation", "classificacao_earnings_yield");
        String recomendacao = texto(detalhes, "resumo", "recomendacao");

        Double margemConservador = numero(detalhes, "valuation", "cenarios_graham", "conservador", "margem_seguranca_percent");
        Double margemBase = numero(detalhes, "valuation", "cenarios_graham", "base", "margem_seguranca_percent");

        List<String> perfis = new ArrayList<>();
        if (indicaSinal(sinalMomentum)) {
            perfis.add("DAY_TRADE");
        }
        if (indicaSinal(sinalReversao)) {
            perfis.add("SWING_REVERSAO");
        }
        if (margemConservador != null && margemConservador >= MARGEM_LONGO_PRAZO_MINIMA
                && ("ATRATIVO".equals(classificacaoEarningsYield) || "RAZOAVEL".equals(classificacaoEarningsYield))) {
            perfis.add("LONGO_PRAZO");
        }

        return new Resultado(
                perfis,
                riscoCompraAgora(zona52w, margemBase),
                riscoVendaAgora(zona52w, margemBase),
                confluencia(recomendacao, sinalMomentum, sinalReversao)
        );
    }

    private static boolean indicaSinal(String sinal) {
        return sinal != null && !"NEUTRO_TECNICO".equals(sinal);
    }

    private static String riscoCompraAgora(String zona52w, Double margemBase) {
        boolean pertoDaMaxima = "PROXIMO_DA_MAXIMA".equals(zona52w);
        boolean margemBaixa = margemBase == null || margemBase < MARGEM_RISCO_MINIMA;

        if (pertoDaMaxima && margemBaixa) {
            return "ALTO";
        }
        if (pertoDaMaxima || margemBaixa) {
            return "MEDIO";
        }
        return "BAIXO";
    }

    private static String riscoVendaAgora(String zona52w, Double margemBase) {
        boolean pertoDaMinima = "PROXIMO_DA_MINIMA".equals(zona52w);
        boolean margemAlta = margemBase != null && margemBase >= MARGEM_RISCO_MINIMA;

        if (pertoDaMinima && margemAlta) {
            return "ALTO";
        }
        if ("PROXIMO_DA_MAXIMA".equals(zona52w)) {
            return "BAIXO";
        }
        return "MEDIO";
    }

    private static ConfluenciaSinaisDTO confluencia(String recomendacao, String sinalMomentum, String sinalReversao) {
        int compra = 0;
        int venda = 0;
        int neutro = 0;

        for (String sentido : List.of(sentidoDaRecomendacao(recomendacao), sentidoTecnico(sinalMomentum), sentidoTecnico(sinalReversao))) {
            if (sentido == null) {
                continue;
            }
            switch (sentido) {
                case "COMPRA" -> compra++;
                case "VENDA" -> venda++;
                default -> neutro++;
            }
        }

        int total = compra + venda + neutro;
        String predominante = compra >= venda && compra > 0 ? "COMPRA" : (venda > 0 ? "VENDA" : "NEUTRO");
        int dominantes = predominante.equals("COMPRA") ? compra : (predominante.equals("VENDA") ? venda : neutro);

        String resumo = total == 0
                ? "Sem sinais disponiveis para avaliar concordancia."
                : String.format("%d de %d sinais indicam %s.", dominantes, total, predominante);

        return ConfluenciaSinaisDTO.builder().compra(compra).venda(venda).neutro(neutro).resumo(resumo).build();
    }

    private static String sentidoDaRecomendacao(String recomendacao) {
        if (recomendacao == null) {
            return null;
        }
        if (recomendacao.startsWith("COMPRA")) {
            return "COMPRA";
        }
        if (recomendacao.startsWith("VENDA")) {
            return "VENDA";
        }
        return "NEUTRO";
    }

    private static String sentidoTecnico(String sinal) {
        if (sinal == null) {
            return null;
        }
        if ("COMPRA_TECNICA".equals(sinal)) {
            return "COMPRA";
        }
        if ("VENDA_TECNICA".equals(sinal)) {
            return "VENDA";
        }
        return "NEUTRO";
    }

    private static String texto(JsonNode raiz, String... caminho) {
        JsonNode atual = navegar(raiz, caminho);
        return atual == null || atual.isMissingNode() || atual.isNull() ? null : atual.asText();
    }

    private static Double numero(JsonNode raiz, String... caminho) {
        JsonNode atual = navegar(raiz, caminho);
        return atual == null || atual.isMissingNode() || atual.isNull() || !atual.isNumber() ? null : atual.asDouble();
    }

    private static JsonNode navegar(JsonNode raiz, String... caminho) {
        JsonNode atual = raiz;
        for (String chave : caminho) {
            if (atual == null) {
                return null;
            }
            atual = atual.path(chave);
        }
        return atual;
    }

    public record Resultado(
            List<String> perfisAplicaveis,
            String riscoCompraAgora,
            String riscoVendaAgora,
            ConfluenciaSinaisDTO confluenciaSinais
    ) {
        static Resultado vazio() {
            return new Resultado(List.of(), null, null, null);
        }
    }
}
