package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.util.Map;

public record ImportacaoCarteirasDTO(String hashConteudo, int importadas, int jaExistentes,
                                     Map<String, String> idsCarteira) { }
