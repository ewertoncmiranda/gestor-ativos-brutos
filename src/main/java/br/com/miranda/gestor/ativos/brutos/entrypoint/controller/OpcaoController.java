package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.ComposicaoCapitalDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.OpcaoB3DTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioComposicaoCapital;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOpcoes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

/**
 * Endpoints de composicao acionaria e cadeia de opcoes da B3.
 */
@Slf4j
@RestController
@RequestMapping("/ativos")
@RequiredArgsConstructor
public class OpcaoController {

    private final RepositorioComposicaoCapital repositorioComposicaoCapital;
    private final RepositorioOpcoes repositorioOpcoes;
    private final RepositorioIdentidadeAtivo repositorioIdentidade;

    /**
     * Composicao ON x PN mais recente do ativo (DFP, fallback ITR).
     * Devolve HTTP 200 com body nulo quando nao ha dados CVM para o ativo.
     */
    @GetMapping("/{ativo}/composicao-capital")
    public ResponseEntity<ComposicaoCapitalDTO> composicaoCapital(@PathVariable String ativo) {
        String simbolo = repositorioIdentidade.canonico(ativo.trim().toUpperCase());
        log.info("{}-ComposicaoCapital | ativo={}", CONTROLADOR, simbolo);
        return repositorioComposicaoCapital.porSimbolo(simbolo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(null));
    }

    /**
     * Cadeia de opcoes do ativo subjacente no ultimo pregao disponivel.
     * {@code vencimento} opcional no formato YYYYMM.
     * Devolve uma lista vazia quando o ativo nao tem opcoes.
     */
    @GetMapping("/{ativo}/opcoes")
    public ResponseEntity<Map<String, Object>> opcoes(
            @PathVariable String ativo,
            @RequestParam(required = false) String vencimento) {
        String simbolo = repositorioIdentidade.canonico(ativo.trim().toUpperCase());
        log.info("{}-Opcoes | ativo={} vencimento={}", CONTROLADOR, simbolo, vencimento);
        List<OpcaoB3DTO> dados = repositorioOpcoes.porAtivo(simbolo, vencimento);
        List<String> vencimentos = repositorioOpcoes.vencimentos(simbolo);
        return ResponseEntity.ok(Map.of("simbolo", simbolo, "vencimentos", vencimentos, "opcoes", dados));
    }
}
