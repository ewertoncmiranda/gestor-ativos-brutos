package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.IndiceMacroDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.PainelMacroDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoPainelMacro;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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

    private final ServicoPainelMacro servicoPainelMacro;

    /** Painel macro consolidado; rota literal tem precedencia sobre /{codigo}. */
    @GetMapping("/painel")
    public PainelMacroDTO painel() {
        return servicoPainelMacro.montar();
    }

    /**
     * Sem {@code desde}, a serie inteira (contrato original); com {@code desde}
     * (ISO, ex. 2025-10-20), so os pontos a partir da data. Data invalida = 400.
     */
    @GetMapping("/{codigo}")
    public List<IndiceMacroDTO> buscarSerie(@PathVariable String codigo,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        String serie = codigo.toUpperCase();
        var pontos = desde == null
                ? repositorioIndiceMacro.findByCodigoSerieOrderByDataDesc(serie)
                : repositorioIndiceMacro.findByCodigoSerieAndDataGreaterThanEqualOrderByDataDesc(serie, desde);
        return pontos.stream()
                .map(IndiceMacroDTO::de)
                .toList();
    }
}
