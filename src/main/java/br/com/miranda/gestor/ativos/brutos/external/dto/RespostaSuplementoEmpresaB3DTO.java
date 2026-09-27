package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resposta de GET .../listedCompaniesProxy/CompanyCall/GetListedSupplementCompany/{base64}:
 * um array com 1 objeto por emissor consultado. So usamos cashDividends aqui;
 * stockDividends (bonificacao) e subscriptions ficam de fora por ora.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespostaSuplementoEmpresaB3DTO(
        @JsonProperty("code") String codigoEmissor,
        @JsonProperty("cashDividends") List<ProventoB3DTO> cashDividends
) {
}
