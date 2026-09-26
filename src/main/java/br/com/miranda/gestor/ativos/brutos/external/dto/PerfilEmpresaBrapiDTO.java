package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.PerfilEmpresaCacheEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PerfilEmpresaBrapiDTO {

    @JsonProperty("sector")
    private String sector;

    @JsonProperty("industry")
    private String industry;

    @JsonProperty("longBusinessSummary")
    private String longBusinessSummary;

    @JsonProperty("website")
    private String website;

    @JsonProperty("cnpj")
    private String cnpj;

    @JsonProperty("fullTimeEmployees")
    private Integer fullTimeEmployees;

    @JsonProperty("city")
    private String city;

    @JsonProperty("state")
    private String state;

    public static PerfilEmpresaBrapiDTO de(PerfilEmpresaCacheEntity entidade) {
        PerfilEmpresaBrapiDTO dto = new PerfilEmpresaBrapiDTO();
        dto.setSector(entidade.getSector());
        dto.setIndustry(entidade.getIndustry());
        dto.setLongBusinessSummary(entidade.getLongBusinessSummary());
        dto.setWebsite(entidade.getWebsite());
        dto.setCnpj(entidade.getCnpj());
        dto.setFullTimeEmployees(entidade.getFullTimeEmployees());
        dto.setCity(entidade.getCity());
        dto.setState(entidade.getState());
        return dto;
    }
}
