package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.*;
import br.com.miranda.gestor.ativos.brutos.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

@Slf4j
@RestController
@RequestMapping("/simulador")
@RequiredArgsConstructor
public class SimuladorOperacoesController {

    private final ServicoSimuladorResultado servicoSimuladorResultado;
    private final ServicoSimuladorOperacao servicoSimuladorOperacao;
    private final ServicoSimuladorIa servicoSimuladorIa;
    private final ServicoSimuladorRetroativo servicoSimuladorRetroativo;
    private final ServicoSimuladorCenario servicoSimuladorCenario;

    /**
     * SIM-GES-2: GET /simulador/resultados?simbolo=PETR4&data=2026-10-12
     * Retorna as 7 estratégias para o ativo no pregão (ou mais recente se data=null).
     * Sem efeito colateral (GET puro).
     */
    @GetMapping("/resultados")
    public ResponseEntity<List<SimuladorResultadoDTO>> buscarResultados(
            @RequestParam String simbolo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        log.info("{} - Requisição para resultados simulador: {} em {}", CONTROLADOR, simbolo, data);
        List<SimuladorResultadoDTO> resultados = servicoSimuladorResultado.buscarResultados(simbolo, data);
        return ResponseEntity.ok(resultados);
    }

    /**
     * SIM-GES-3: GET /simulador/operacoes
     * Lista todas as operações ativas (PENDENTE ou ABERTA).
     * Sem efeito colateral.
     */
    @GetMapping("/operacoes")
    public ResponseEntity<List<SimuladorOperacaoDTO>> listarOperacoes() {
        log.info("{} - Requisição para listar operações", CONTROLADOR);
        return ResponseEntity.ok(servicoSimuladorOperacao.listarOperacoes());
    }

    /**
     * SIM-GES-3: POST /simulador/operacoes?simbolo=PETR4&dataEntrada=2026-10-12&capital=10000&versaoEstrategia=MOMENTO_PURO_2026.10.12-1
     * Cria uma nova operação manual com status PENDENTE.
     */
    @PostMapping("/operacoes")
    public ResponseEntity<SimuladorOperacaoDTO> criarOperacao(
            @RequestParam String simbolo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataEntrada,
            @RequestParam BigDecimal capital,
            @RequestParam String versaoEstrategia) {
        log.info("{} - Criando operação: {} R$ {} em {} com estratégia {}", CONTROLADOR, simbolo, capital, dataEntrada, versaoEstrategia);
        try {
            SimuladorOperacaoDTO operacao = servicoSimuladorOperacao.criar(simbolo, dataEntrada, capital, versaoEstrategia);
            return ResponseEntity.status(HttpStatus.CREATED).body(operacao);
        } catch (IllegalArgumentException e) {
            log.warn("{} - Erro ao criar operação: {}", CONTROLADOR, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * SIM-GES-8: GET /simulador/operacoes/{id}/historico
     * Retorna P&L diário calculado on-the-fly a partir de cotacao_b3_diaria e sinais de estratégia.
     */
    @GetMapping("/operacoes/{id}/historico")
    public ResponseEntity<SimuladorOperacaoHistoricoDTO> historicoOperacao(@PathVariable Long id) {
        log.info("{} - Requisição para histórico da operação: {}", CONTROLADOR, id);
        try {
            return ResponseEntity.ok(servicoSimuladorOperacao.historicoOperacao(id));
        } catch (IllegalArgumentException e) {
            log.warn("{} - Operação não encontrada: {}", CONTROLADOR, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * SIM-GES-8: GET /simulador/retroativo/historico?simbolo=X&inicio=Y&fim=Z&capital=N
     * Histórico P&L de um período (uso retroativo), sem vínculo com operação cadastrada.
     */
    @GetMapping("/retroativo/historico")
    public ResponseEntity<SimuladorOperacaoHistoricoDTO> historicoPeriodo(
            @RequestParam String simbolo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false, defaultValue = "10000") BigDecimal capital) {
        log.info("{} - Histórico período: {} de {} a {}", CONTROLADOR, simbolo, inicio, fim);
        try {
            return ResponseEntity.ok(servicoSimuladorOperacao.historicoPeriodo(simbolo, inicio, fim, capital));
        } catch (Exception e) {
            log.warn("{} - Erro no histórico período: {}", CONTROLADOR, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * SIM-GES-3: DELETE /simulador/operacoes/{id}
     * Deleta uma operação (só se status=PENDENTE).
     */
    @DeleteMapping("/operacoes/{id}")
    public ResponseEntity<Void> deletarOperacao(@PathVariable Long id) {
        log.info("{} - Deletando operação: {}", CONTROLADOR, id);
        try {
            servicoSimuladorOperacao.deletar(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            log.warn("{} - Erro ao deletar operação: {}", CONTROLADOR, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * SIM-GES-5: POST /simulador/ia/{simbolo}
     * Análise IA sob demanda com cache diário.
     * Verifica cache antes de chamar insider-ia (nunca cobra token duas vezes).
     * Resposta inclui emCache e salvoEm.
     */
    @PostMapping("/ia/{simbolo}")
    public ResponseEntity<SimuladorIaAnaliseDTO> analisarIa(@PathVariable String simbolo) {
        log.info("{} - Requisição para análise IA: {}", CONTROLADOR, simbolo);
        try {
            SimuladorIaAnaliseDTO analise = servicoSimuladorIa.analisarSimbolo(simbolo);
            return ResponseEntity.ok(analise);
        } catch (Exception e) {
            log.error("{} - Erro ao chamar insider-ia: {}", CONTROLADOR, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * SIM-GES-6: GET /simulador/retroativo?simbolo=PETR4&estrategia=MOMENTO_PURO&versao=v1&janela=21&inicio=2026-01-01
     * Valida retroativamente uma estratégia contra preços reais.
     * Retorna taxa de acerto, retorno médio e lista de entradas.
     */
    @GetMapping("/retroativo")
    public ResponseEntity<SimuladorRetroativoDTO> retroativo(
            @RequestParam String simbolo,
            @RequestParam String estrategia,
            @RequestParam(required = false, defaultValue = "v1") String versao,
            @RequestParam(required = false, defaultValue = "21") Integer janela,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio) {
        log.info("{} - Requisição retroativo: {} {} {} desde {}", CONTROLADOR, simbolo, estrategia, versao, inicio);
        try {
            SimuladorRetroativoDTO retroativo = servicoSimuladorRetroativo.analisar(simbolo, estrategia, versao, janela, inicio);
            return ResponseEntity.ok(retroativo);
        } catch (Exception e) {
            log.error("{} - Erro ao processar retroativo: {}", CONTROLADOR, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * SIM-GES-7: GET /simulador/cenario?simbolo=PETR4&data=2026-10-12&janela=21&capital=10000
     * Simula P&L para uma data e janela fornecidas.
     * Retorna resultado por estratégia, CDI e ganho/perda em R$.
     */
    @GetMapping("/cenario")
    public ResponseEntity<SimuladorCenarioDTO> cenario(
            @RequestParam String simbolo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false, defaultValue = "21") Integer janela,
            @RequestParam BigDecimal capital) {
        log.info("{} - Requisição cenário: {} de {} por {} pregões", CONTROLADOR, simbolo, data, janela);
        try {
            SimuladorCenarioDTO cenario = servicoSimuladorCenario.simular(simbolo, data, janela, capital);
            return ResponseEntity.ok(cenario);
        } catch (Exception e) {
            log.error("{} - Erro ao processar cenário: {}", CONTROLADOR, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
