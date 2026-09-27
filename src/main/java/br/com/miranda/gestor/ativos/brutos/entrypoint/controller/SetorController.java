package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoSetorDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.SetorDTO;
import br.com.miranda.gestor.ativos.brutos.service.cotacao.CotacaoRecente;
import br.com.miranda.gestor.ativos.brutos.service.cotacao.FonteCotacaoRecente;
import br.com.miranda.gestor.ativos.brutos.tools.SetoresReferencia;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Unica responsabilidade: servir a visao "Mercado por setor" - agrupa o
 * universo de referencia ({@link SetoresReferencia}) com o preco mais recente
 * de cada ticker. Desde o monitoramento em camadas (infra V13) as referencias
 * nao consultam mais a BRAPI: o preco vem do fechamento oficial (COTAHIST),
 * ou da cotacao intradiaria quando o ticker tambem e favorito - quem decide e
 * a {@link FonteCotacaoRecente}.
 */
@RestController
@RequiredArgsConstructor
public class SetorController {

    private final FonteCotacaoRecente fonteCotacao;

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
        Optional<CotacaoRecente> cotacao = fonteCotacao.cotacao(simbolo);
        return AtivoSetorDTO.builder()
                .simbolo(simbolo)
                .preco(cotacao.map(CotacaoRecente::preco).orElse(null))
                .variacaoPercent(cotacao.map(CotacaoRecente::variacaoPercent).orElse(null))
                .atualizadoEm(cotacao.map(CotacaoRecente::referencia).orElse(null))
                .fonte(cotacao.map(CotacaoRecente::fonte).orElse(null))
                .build();
    }
}
