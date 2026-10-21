package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.util.List;

/** Selos de confiabilidade do ativo (FONTES-GES-4), montados com as fontes da V45. */
public record ConfiabilidadeDTO(String simbolo, String cnpj, Resumo resumo, List<Selo> selos) {

    public enum Nivel { OK, ALERTA, CRITICO, SEM_DADO }

    public record Selo(String id, Nivel nivel, String titulo, String detalhe) {
    }

    public record Resumo(Nivel nivel, String leitura) {
    }
}
