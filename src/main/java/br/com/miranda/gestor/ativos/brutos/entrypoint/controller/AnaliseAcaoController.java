package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.AnaliseConsolidadaDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaAnaliseIaDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAnaliseAcao;
import br.com.miranda.gestor.ativos.brutos.tools.ConsolidadorAnaliseAcao;
import br.com.miranda.gestor.ativos.brutos.tools.MontadorDecisaoDeterministica;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.BRAPI_SERVICE;

@Slf4j
@RestController
@RequestMapping("/analises")
@RequiredArgsConstructor
public class AnaliseAcaoController {

    private final ServicoAnaliseAcao servicoAnaliseAcao;

    /**
     * Consolida as análises persistidas de um ativo e deriva a decisão por regras deterministicas.
     */
    @GetMapping("/{simbolo}/analise")
    public RespostaAnaliseIaDTO buscarPorSimbolo(@PathVariable String simbolo) {
        log.info("{}-Buscando analises e montando decisao para simbolo: {}", BRAPI_SERVICE, simbolo);
        List<AnaliseAcaoEntity> analises = servicoAnaliseAcao.buscarPorSimbolo(simbolo);

        if (Objects.isNull(analises) || analises.isEmpty()) {
            log.warn("{}-Nenhuma analise encontrada para simbolo: {}", BRAPI_SERVICE, simbolo);
            return RespostaAnaliseIaDTO.builder()
                    .resumo("Nenhuma analise encontrada para o simbolo: " + simbolo)
                    .build();
        }

        AnaliseConsolidadaDTO consolidado = ConsolidadorAnaliseAcao.consolidar(analises);
        return MontadorDecisaoDeterministica.montar(consolidado);
    }
}
