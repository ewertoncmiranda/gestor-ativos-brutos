package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.AnaliseConsolidadaDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.FundamentosAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.FundamentosCvmDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaAnaliseIaDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAnaliseAcao;
import br.com.miranda.gestor.ativos.brutos.service.ServicoFundamentosCvm;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoCache;
import br.com.miranda.gestor.ativos.brutos.tools.ConsolidadorAnaliseAcao;
import br.com.miranda.gestor.ativos.brutos.tools.MontadorDecisaoDeterministica;
import br.com.miranda.gestor.ativos.brutos.tools.PerfilOperacaoClassificador;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.BRAPI_SERVICE;
import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

@Slf4j
@RestController
@RequestMapping("/analises")
@RequiredArgsConstructor
public class AnaliseAcaoController {

    private final ServicoAnaliseAcao servicoAnaliseAcao;
    private final ServicoAtualizacaoCache servicoAtualizacaoCache;
    private final ServicoFundamentosCvm servicoFundamentosCvm;

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

    /**
     * Devolve o detalhes_json bruto da análise mais recente do símbolo - os números e
     * classificações exatos calculados naquele ciclo pelo gerar-insights, sem a
     * consolidação/média aplicada por buscarPorSimbolo. Usado pela aba "Como funciona".
     */
    @GetMapping("/{simbolo}/fundamentos")
    public FundamentosAtivoDTO buscarFundamentos(@PathVariable String simbolo) {
        log.info("{}-Buscando fundamentos da ultima analise para simbolo: {}", BRAPI_SERVICE, simbolo);
        FundamentosAtivoDTO dto = servicoAnaliseAcao.buscarUltimaPorSimbolo(simbolo)
                .map(FundamentosAtivoDTO::de)
                .orElseGet(() -> FundamentosAtivoDTO.vazio(simbolo));

        PerfilOperacaoClassificador.Resultado perfil = PerfilOperacaoClassificador.classificar(dto.getDetalhes());
        dto.setPerfisAplicaveis(perfil.perfisAplicaveis());
        dto.setRiscoCompraAgora(perfil.riscoCompraAgora());
        dto.setRiscoVendaAgora(perfil.riscoVendaAgora());
        dto.setConfluenciaSinais(perfil.confluenciaSinais());
        servicoAtualizacaoCache.buscarPerfilComFallback(simbolo).ifPresent(dto::setPerfilEmpresa);
        return dto;
    }

    /**
     * Fundamentos contábeis do ativo a partir dos dados abertos da CVM
     * (DFP/ITR), carregados em lote pelo job {@code etl-fundamentos-cvm}.
     *
     * <p>Cobre justamente o que o plano gratuito da BRAPI não entrega: ROE,
     * ROIC, margens, dívida líquida e fluxo de caixa livre.
     *
     * <p>P/L e P/VP vêm calculados na hora, cruzando LPA/VPA do último
     * exercício com a cotação mais recente já coletada — ficam atuais sem
     * depender de nova execução do ETL.
     *
     * <p>Símbolo ainda não carregado devolve 200 com os campos nulos, para não
     * confundir "ticker inválido" com "ETL ainda não rodou para ele".
     */
    @GetMapping("/{simbolo}/fundamentos-cvm")
    public ResponseEntity<FundamentosCvmDTO> buscarFundamentosCvm(@PathVariable String simbolo) {
        log.info("{}-Buscando fundamentos CVM para simbolo: {}", CONTROLADOR, simbolo);
        return ResponseEntity.ok(servicoFundamentosCvm.buscarPorSimbolo(simbolo));
    }
}
