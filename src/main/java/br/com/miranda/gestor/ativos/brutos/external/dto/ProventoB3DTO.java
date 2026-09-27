package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Um provento (dividendo ou JCP) de "cashDividends", dentro da resposta de
 * GetListedSupplementCompany. Todos os campos vem como string, mesmo o valor
 * (rate) e as datas (dd/MM/yyyy) - a B3 nao devolve tipo nativo aqui.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProventoB3DTO(
        @JsonProperty("isinCode") String isinCode,
        @JsonProperty("label") String tipo,
        @JsonProperty("rate") String valorPorAcao,
        @JsonProperty("relatedTo") String periodoReferencia,
        @JsonProperty("approvedOn") String aprovadoEm,
        @JsonProperty("lastDatePrior") String ultimaDataComDireito,
        @JsonProperty("paymentDate") String dataPagamento
) {
}
