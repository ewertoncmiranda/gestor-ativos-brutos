package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.AnaliseConsolidadaDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ConsolidadorAnaliseAcao {

    private static final DateTimeFormatter FORMATADOR_DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ConsolidadorAnaliseAcao() {
    }

    /** Consolida análises históricas para a decisão determinística. */
    public static AnaliseConsolidadaDTO consolidar(List<AnaliseAcaoEntity> analises) {
        if (analises == null || analises.isEmpty()) {
            return null;
        }

        List<AnaliseAcaoEntity> analisesValidas = analises.stream()
                .filter(Objects::nonNull)
                .toList();
        if (analisesValidas.isEmpty()) {
            return null;
        }

        String simbolo = analisesValidas.get(0).getSimbolo();
        int total = analisesValidas.size();

        LocalDateTime inicio = analisesValidas.stream()
                .map(AnaliseAcaoEntity::getDataAnalise)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime fim = analisesValidas.stream()
                .map(AnaliseAcaoEntity::getDataAnalise)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        Map<String, Long> contagemSinais = analisesValidas.stream()
                .map(AnaliseAcaoEntity::getRecomendacao)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(recomendacao -> recomendacao, Collectors.counting()));

        String sinalPredominante = contagemSinais.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("INDEFINIDO");

        long totalVenda = contagemSinais.entrySet().stream()
                .filter(entrada -> ehSinalDeVenda(entrada.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        double percentualVenda = (totalVenda * 100.0) / total;

        double variacaoMedia = analisesValidas.stream()
                .map(AnaliseAcaoEntity::getMargemSegurancaPercent)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        return AnaliseConsolidadaDTO.builder()
                .ativo(simbolo)
                .janelaAnalise(AnaliseConsolidadaDTO.JanelaAnalise.builder()
                        .inicio(inicio != null ? inicio.format(FORMATADOR_DATA) : "")
                        .fim(fim != null ? fim.format(FORMATADOR_DATA) : "")
                        .build())
                .quantidadeRegistros(total)
                .sinalPredominante(sinalPredominante)
                .percentualSinaisVenda(arredondar(percentualVenda))
                .variacaoMedia(arredondar(variacaoMedia))
                .indicadores(consolidarIndicadores(analisesValidas))
                .build();
    }

    private static boolean ehSinalDeVenda(String recomendacao) {
        return recomendacao != null && recomendacao.startsWith("VENDA");
    }

    private static Map<String, Object> consolidarIndicadores(List<AnaliseAcaoEntity> analises) {
        Map<String, List<Double>> valoresPorChave = new HashMap<>();

        for (AnaliseAcaoEntity analise : analises) {
            JsonNode detalhes = analise.getDetalhesJson();
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
