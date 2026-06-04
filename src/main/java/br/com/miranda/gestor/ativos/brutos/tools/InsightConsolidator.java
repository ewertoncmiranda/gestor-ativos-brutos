package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.InsightAcao;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsightConsolidadoDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class InsightConsolidator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static InsightConsolidadoDTO consolidar(List<InsightAcao> insights) {
        if (insights == null || insights.isEmpty()) {
            return null;
        }

        String simbolo = insights.get(0).getSimbolo();
        int total = insights.size();

        // Janela de tempo
        LocalDateTime inicio = insights.stream()
                .map(InsightAcao::getDataAnalise)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime fim = insights.stream()
                .map(InsightAcao::getDataAnalise)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        // Sinal predominante
        Map<String, Long> contagemSinais = insights.stream()
                .collect(Collectors.groupingBy(InsightAcao::getRecomendacao, Collectors.counting()));

        String sinalPredominante = contagemSinais.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("INDEFINIDO");

        // Percentual de Venda
        long totalVenda = contagemSinais.getOrDefault("VENDA", 0L);
        double percentualVenda = (totalVenda * 100.0) / total;

        // Variação média (Margem de Segurança)
        double variacaoMedia = insights.stream()
                .map(InsightAcao::getMargemSegurancaPercent)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        // Consolidar indicadores do JSON (média simples dos valores numéricos)
        Map<String, Object> indicadoresMedios = consolidarIndicadores(insights);

        return InsightConsolidadoDTO.builder()
                .ativo(simbolo)
                .janelaAnalise(InsightConsolidadoDTO.JanelaAnalise.builder()
                        .inicio(inicio != null ? inicio.format(FORMATTER) : "")
                        .fim(fim != null ? fim.format(FORMATTER) : "")
                        .build())
                .quantidadeRegistros(total)
                .sinalPredominante(sinalPredominante)
                .percentualSinaisVenda(arredondar(percentualVenda))
                .variacaoMedia(arredondar(variacaoMedia))
                .indicadores(indicadoresMedios)
                .build();
    }

    private static Map<String, Object> consolidarIndicadores(List<InsightAcao> insights) {
        Map<String, List<Double>> valoresPorChave = new HashMap<>();

        for (InsightAcao insight : insights) {
            JsonNode detalhes = insight.getDetalhesJson();
            if (detalhes != null && detalhes.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = detalhes.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    if (field.getValue().isNumber()) {
                        valoresPorChave.computeIfAbsent(field.getKey(), k -> new ArrayList<>())
                                .add(field.getValue().asDouble());
                    }
                }
            }
        }

        Map<String, Object> medias = new HashMap<>();
        valoresPorChave.forEach((chave, valores) -> {
            double media = valores.stream().mapToDouble(v -> v).average().orElse(0.0);
            medias.put(chave, arredondar(media));
        });

        return medias;
    }

    private static double arredondar(double valor) {
        return BigDecimal.valueOf(valor)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
