package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.ComposicaoCapitalDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.OpcaoB3DTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaOpcoesDTO;
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

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;
import static br.com.miranda.gestor.ativos.brutos.external.dto.RespostaOpcoesDTO.StatusOpcoes.*;

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
     * Quando a base ainda nao tem colunas confiaveis, devolve vazio explicado
     * sem cair em inferencia fragil por prefixo.
     */
    @GetMapping("/{ativo}/opcoes")
    public ResponseEntity<RespostaOpcoesDTO> opcoes(
            @PathVariable String ativo,
            @RequestParam(required = false) String vencimento) {
        String solicitado = ativo.trim().toUpperCase();
        String canonico = repositorioIdentidade.canonico(solicitado);
        boolean alias = !solicitado.equals(canonico);
        log.info("{}-Opcoes | solicitado={} canonico={} vencimento={}", CONTROLADOR, solicitado, canonico, vencimento);

        if (!repositorioOpcoes.tabelaDisponivel() || repositorioOpcoes.totalRegistros() == 0) {
            return ResponseEntity.ok(resposta(solicitado, canonico, alias, CARGA_OPCOES_AUSENTE,
                    "A carga de opções ainda não rodou ou não publicou dados válidos.", List.of(), List.of()));
        }
        if (!repositorioOpcoes.suportaAtivoObjeto()) {
            return ResponseEntity.ok(resposta(solicitado, canonico, alias, BASE_OPCOES_CONTAMINADA,
                    "A base de opções não possui vínculo confiável por ativo-objeto; consulta por prefixo foi bloqueada para evitar leitura enganosa.",
                    List.of(), List.of()));
        }

        List<OpcaoB3DTO> dados = repositorioOpcoes.porAtivo(canonico, vencimento);
        List<String> vencimentos = repositorioOpcoes.vencimentos(canonico);
        if (dados.isEmpty()) {
            return ResponseEntity.ok(resposta(solicitado, canonico, alias, SEM_CADEIA_VALIDA,
                    alias
                            ? "O ticker solicitado foi normalizado, mas não há cadeia de opções válida para o código canônico no último pregão."
                            : "Não há cadeia de opções válida carregada para este ativo no último pregão.",
                    vencimentos, dados));
        }
        return ResponseEntity.ok(resposta(solicitado, canonico, alias, alias ? TICKER_NORMALIZADO : OK,
                alias ? "O ticker solicitado foi normalizado para o código atual antes da consulta." : "Cadeia de opções válida carregada.",
                vencimentos, dados));
    }

    private static RespostaOpcoesDTO resposta(String solicitado, String canonico, boolean alias,
                                              RespostaOpcoesDTO.StatusOpcoes status, String motivo,
                                              List<String> vencimentos, List<OpcaoB3DTO> opcoes) {
        return new RespostaOpcoesDTO(solicitado, canonico, alias, status, motivo, vencimentos, opcoes);
    }
}
