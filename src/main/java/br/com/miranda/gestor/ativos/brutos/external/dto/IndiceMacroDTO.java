package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class IndiceMacroDTO {
    private String data;
    private BigDecimal valor;

    public static IndiceMacroDTO de(IndiceMacroEntity entidade) {
        return IndiceMacroDTO.builder()
                .data(entidade.getData().toString())
                .valor(entidade.getValor())
                .build();
    }
}
