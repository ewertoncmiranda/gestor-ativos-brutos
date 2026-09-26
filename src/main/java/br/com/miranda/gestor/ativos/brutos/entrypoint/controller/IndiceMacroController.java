package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.IndiceMacroDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Unica responsabilidade: servir os indices macro (Selic, CDI, IPCA) do
 * cache (indice_macro). Nunca chama o Banco Central na hora do clique -
 * quem mantem o cache fresco e ServicoAtualizacaoIndicesMacro.
 */
@RestController
@RequestMapping("/indices-macro")
@RequiredArgsConstructor
public class IndiceMacroController {

    private final RepositorioIndiceMacro repositorioIndiceMacro;

    @GetMapping("/{codigo}")
    public List<IndiceMacroDTO> buscarSerie(@PathVariable String codigo) {
        return repositorioIndiceMacro.findByCodigoSerieOrderByDataDesc(codigo.toUpperCase())
                .stream()
                .map(IndiceMacroDTO::de)
                .toList();
    }
}
