package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.ProventoDistribuidoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioProventoDistribuido;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Unica responsabilidade: servir os proventos do cache (provento_distribuido)
 * por ativo. Nunca chama a B3 na hora do clique - ServicoAtualizacaoProventos
 * mantem o cache fresco. So os ultimos 12 meses de cada consulta a B3 entram
 * aqui (limite da propria fonte); nao e o historico completo do ativo.
 */
@RestController
@RequestMapping("/proventos")
@RequiredArgsConstructor
public class ProventoController {

    private final RepositorioProventoDistribuido repositorioProventoDistribuido;

    @GetMapping("/{simbolo}")
    public List<ProventoDistribuidoDTO> listarPorSimbolo(@PathVariable String simbolo) {
        // Provento e por emissora (PETR3 e PETR4 sao a mesma PETR), nao por
        // ticker - aceita qualquer um dos dois e normaliza pro codigo salvo.
        String codigoEmissor = simbolo.toUpperCase().replaceAll("\\d+$", "");
        return repositorioProventoDistribuido.findBySimboloOrderByDataPagamentoDesc(codigoEmissor)
                .stream()
                .map(ProventoDistribuidoDTO::de)
                .toList();
    }
}
