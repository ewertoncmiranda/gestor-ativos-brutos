package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Favoritar acima do teto que a cota gratuita da BRAPI comporta
 * (brapi.favoritos.max). 409: o pedido e valido, o estado atual e que nao deixa.
 */
public class ExcecaoLimiteFavoritos extends ExcecaoAplicacao {

    public ExcecaoLimiteFavoritos(int limite) {
        super("LIMITE_FAVORITOS",
                "Limite de " + limite + " favoritos atingido: a cota gratuita da BRAPI (15.000 requisições/mês) "
                        + "comporta " + limite + " ativos com cotação a cada 30 min. Remova um favorito antes de adicionar outro.",
                HttpStatus.CONFLICT);
    }
}
