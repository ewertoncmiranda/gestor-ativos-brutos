package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.FatorAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.ProventoContabilDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioLacunas;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Leituras do Plano LAC por ativo (infra V16): fatores (LAC-GES-2) e
 * proventos contabeis - DVA (LAC-GES-3). Classe separada de
 * {@code AtivoController} (mesmo prefixo {@code /ativos}, rotas diferentes)
 * so para nao mexer num arquivo com edicao de outra sessao em curso.
 */
@RestController
@RequestMapping("/ativos")
@RequiredArgsConstructor
public class LacunasAtivoController {

    private final RepositorioLacunas repositorio;

    /** Ultimo valor de cada fator ativo, com percentil no universo e no setor. */
    @GetMapping("/{simbolo}/fatores")
    public List<FatorAtivoDTO> fatores(@PathVariable String simbolo) {
        return repositorio.fatoresPorSimbolo(simbolo);
    }

    /** Proventos por periodo contabil (DVA) - ao lado dos eventos de /proventos/{simbolo}. */
    @GetMapping("/{simbolo}/proventos-contabeis")
    public List<ProventoContabilDTO> proventosContabeis(@PathVariable String simbolo) {
        return repositorio.proventosContabeisPorSimbolo(simbolo);
    }
}
