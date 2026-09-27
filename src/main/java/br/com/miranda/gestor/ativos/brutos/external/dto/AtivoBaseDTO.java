package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha da tela "Base" - o universo amplo de ativos que o ecossistema
 * conhece (cvm_ticker/cvm_empresa), nao so os favoritos do usuario
 * (ativo_monitorado). Projecao de interface: os nomes dos getters tem que
 * bater com os alias da query nativa em {@code RepositorioBaseAtivos}.
 */
public interface AtivoBaseDTO {
    String getSimbolo();

    String getNome();

    String getSetor();

    BigDecimal getUltimoFechamento();

    LocalDate getDataUltimoFechamento();

    Boolean getTemFundamento();

    Boolean getFavorito();
}
