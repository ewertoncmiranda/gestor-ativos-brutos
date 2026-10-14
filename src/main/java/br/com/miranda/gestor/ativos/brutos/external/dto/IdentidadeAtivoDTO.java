package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record IdentidadeAtivoDTO(
        String simbolo,
        String cnpj,
        @JsonProperty("razao_social") String razaoSocial,
        String isin,
        String cnae,
        @JsonProperty("grupo_economico") String grupoEconomico,
        @JsonProperty("situacao_registro") String situacaoRegistro,
        @JsonProperty("data_constituicao") LocalDate dataConstituicao,
        @JsonProperty("uf_municipio") String ufMunicipio,
        @JsonProperty("free_float") BigDecimal freeFloat) {

    public static IdentidadeAtivoDTO vazio(String simbolo) {
        return new IdentidadeAtivoDTO(simbolo, null, null, null, null, null, null, null, null, null);
    }
}
