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

    // MySQL devolve EXISTS(...) como inteiro (0/1) pelo driver JDBC, nao como
    // BIT/BOOLEAN - a projecao de interface do Spring Data nao converte Long
    // para Boolean automaticamente (UnsupportedOperationException em tempo de
    // serializacao). Long aqui e verdade: no JSON vira 0/1, que o front trata
    // como truthy/falsy sem diferenca pratica.
    Long getTemFundamento();

    Long getFavorito();
}
