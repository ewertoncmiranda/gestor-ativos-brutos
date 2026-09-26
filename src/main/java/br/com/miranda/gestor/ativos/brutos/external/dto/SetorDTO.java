package br.com.miranda.gestor.ativos.brutos.external.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SetorDTO {
    private String nome;
    private List<AtivoSetorDTO> ativos;
}
