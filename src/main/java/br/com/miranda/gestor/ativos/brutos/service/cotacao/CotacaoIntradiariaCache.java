package br.com.miranda.gestor.ativos.brutos.service.cotacao;

import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAtivoMonitorado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCotacaoAtual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cotacao intradiaria que o agendador grava para os favoritos (cache da BRAPI).
 *
 * <p>So vale para quem AINDA e favorito: o cache guarda a hora da consulta,
 * nao a do preco, e uma referencia de setor que deixou de ser consultada
 * (infra V13) teria um "atualizadoEm" de antes da troca competindo com o
 * fechamento oficial.
 */
@Component
@RequiredArgsConstructor
public class CotacaoIntradiariaCache implements FonteCotacaoRecente {

    private final RepositorioCotacaoAtual repositorio;
    private final RepositorioAtivoMonitorado ativos;

    @Override
    public Optional<CotacaoRecente> cotacao(String simbolo) {
        boolean favorito = ativos.findBySimbolo(simbolo)
                .filter(a -> Boolean.TRUE.equals(a.getAtivo()) && a.getTipoColeta() == TipoColeta.COTACAO_E_HISTORICO)
                .isPresent();
        if (!favorito) {
            return Optional.empty();
        }
        return repositorio.findBySimbolo(simbolo)
                .filter(c -> c.getRegularMarketPrice() != null)
                .map(c -> new CotacaoRecente(c.getRegularMarketPrice(), c.getRegularMarketChangePercent(),
                        c.getAtualizadoEm(), "BRAPI"));
    }
}
