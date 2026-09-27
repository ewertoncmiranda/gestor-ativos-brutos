package br.com.miranda.gestor.ativos.brutos.service.cotacao;

import java.util.Optional;

/** Porta para "o preco mais recente deste ativo", independente de onde vem. */
public interface FonteCotacaoRecente {

    Optional<CotacaoRecente> cotacao(String simbolo);
}
