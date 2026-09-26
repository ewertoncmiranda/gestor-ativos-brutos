package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.CotacaoAtualEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoSetorDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.SetorDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCotacaoAtual;
import br.com.miranda.gestor.ativos.brutos.tools.SetoresReferencia;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Unica responsabilidade: servir a visao "Mercado por setor" - agrupa o
 * universo de referencia hardcoded ({@link SetoresReferencia}) com a
 * cotacao mais recente de cada ticker, lida do cache (cotacao_atual). Nunca
 * chama a BRAPI: quem mantem esse cache fresco e o AgendadorCacheAtivos,
 * mesmo escritor unico que ja serve o resto do app.
 */
@RestController
@RequiredArgsConstructor
public class SetorController {

    private final RepositorioCotacaoAtual repositorioCotacaoAtual;

    @GetMapping("/setores")
    public List<SetorDTO> listarSetores() {
        return SetoresReferencia.TICKERS_POR_SETOR.entrySet().stream()
                .map(entrada -> SetorDTO.builder()
                        .nome(entrada.getKey())
                        .ativos(entrada.getValue().stream().map(this::montarAtivoSetor).toList())
                        .build())
                .toList();
    }

    private AtivoSetorDTO montarAtivoSetor(String simbolo) {
        Optional<CotacaoAtualEntity> cotacao = repositorioCotacaoAtual.findBySimbolo(simbolo);
        return AtivoSetorDTO.builder()
                .simbolo(simbolo)
                .preco(cotacao.map(CotacaoAtualEntity::getRegularMarketPrice).orElse(null))
                .variacaoPercent(cotacao.map(CotacaoAtualEntity::getRegularMarketChangePercent).orElse(null))
                .atualizadoEm(cotacao.map(CotacaoAtualEntity::getAtualizadoEm).orElse(null))
                .build();
    }
}
