package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Uma serie dentro do resultado da API de agregados do IBGE (SIDRA): o mapa
 * "serie" tem periodo -> valor, ambos string (ex.: {"202607":"5.3"}). O
 * periodo pode ser AAAAMM (mensal) ou AAAA (anual), dependendo da tabela -
 * ver ServicoAtualizacaoIndicadoresIbge.periodoParaData.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SerieSidraDTO(
        @JsonProperty("serie") Map<String, String> serie
) {
}
