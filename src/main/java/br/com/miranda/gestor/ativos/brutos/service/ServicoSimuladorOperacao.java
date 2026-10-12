package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import br.com.miranda.gestor.ativos.brutos.external.SimuladorOperacaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.SimuladorOperacaoDiarioEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorOperacaoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorOperacao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorOperacaoDiario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoSimuladorOperacao {

    private final RepositorioSimuladorOperacao repositorio;
    private final RepositorioSimuladorOperacaoDiario repositorioDiario;
    private final RepositorioSimuladorAtivo repositorioAtivo;

    /**
     * SIM-GES-3: Lista operações ativas (PENDENTE ou ABERTA).
     */
    public List<SimuladorOperacaoDTO> listarOperacoes() {
        log.info("{} - Listando operações do simulador", SERVICO);
        // Retorna tudo exceto FECHADA
        return repositorio.findAll().stream()
                .filter(op -> !op.getStatus().equals(SimuladorOperacaoEntity.StatusOperacao.FECHADA))
                .map(SimuladorOperacaoDTO::de)
                .collect(Collectors.toList());
    }

    /**
     * SIM-GES-3: Cria uma nova operação com status PENDENTE.
     * Usa capital e versao_estrategia conforme migration V34.
     */
    public SimuladorOperacaoDTO criar(String simbolo, LocalDate dataEntrada,
                                       BigDecimal capital, String versaoEstrategia) {
        log.info("{} - Criando operação simulada: {} R$ {} em {} com estratégia {}", SERVICO, simbolo, capital, dataEntrada, versaoEstrategia);

        // Busca ativo_id pelo simbolo
        Optional<SimuladorAtivoEntity> ativoOpt = repositorioAtivo.findBySimbolo(simbolo);
        if (ativoOpt.isEmpty()) {
            throw new IllegalArgumentException("Ativo não monitorado no simulador: " + simbolo);
        }

        SimuladorOperacaoEntity operacao = new SimuladorOperacaoEntity();
        operacao.setAtivoId(ativoOpt.get().getId());
        operacao.setDataEntrada(dataEntrada);
        operacao.setCapital(capital);
        operacao.setVersaoEstrategia(versaoEstrategia);
        operacao.setStatus(SimuladorOperacaoEntity.StatusOperacao.PENDENTE);

        SimuladorOperacaoEntity salva = repositorio.save(operacao);
        log.info("{} - Operação criada: ID {}", SERVICO, salva.getId());
        return SimuladorOperacaoDTO.de(salva);
    }

    /**
     * SIM-GES-3: Deleta uma operação (só se status=PENDENTE).
     */
    public void deletar(Long operacaoId) {
        log.info("{} - Deletando operação: {}", SERVICO, operacaoId);

        repositorio.findById(operacaoId).ifPresentOrElse(
                operacao -> {
                    if (!operacao.getStatus().equals(SimuladorOperacaoEntity.StatusOperacao.PENDENTE)) {
                        log.warn("{} - Não é permitido deletar operação com status {}", SERVICO, operacao.getStatus());
                        throw new IllegalStateException("Apenas operações PENDENTE podem ser deletadas");
                    }
                    repositorio.delete(operacao);
                    log.info("{} - Operação deletada: {}", SERVICO, operacaoId);
                },
                () -> log.warn("{} - Operação não encontrada: {}", SERVICO, operacaoId)
        );
    }

    /**
     * SIM-GES-4: Rotina da manhã - abre operações PENDENTE cuja data_entrada <= hoje
     * e grava snapshot diário idempotente em simulador_operacao_diario.
     * Chamada pelo agendador de rotina (job diário).
     */
    @Transactional
    public void rotinaMatinal(LocalDate hoje) {
        log.info("{} - Executando rotina matinal do simulador para {}", SERVICO, hoje);

        // 1. Abre operações PENDENTE com data_entrada <= hoje
        List<SimuladorOperacaoEntity> pendentes = repositorio
                .findByStatus(SimuladorOperacaoEntity.StatusOperacao.PENDENTE);

        pendentes.stream()
                .filter(op -> !op.getDataEntrada().isAfter(hoje))
                .forEach(op -> {
                    op.setStatus(SimuladorOperacaoEntity.StatusOperacao.ABERTA);
                    repositorio.save(op);
                    log.info("{} - Operação aberta: ID {} (data_entrada={})", SERVICO, op.getId(), op.getDataEntrada());
                });

        // 2. Cria snapshot diário para operações ABERTA (idempotente)
        List<SimuladorOperacaoEntity> abertas = repositorio
                .findByStatus(SimuladorOperacaoEntity.StatusOperacao.ABERTA);

        abertas.forEach(op -> {
            // Verifica se já existe snapshot para este pregão
            if (repositorioDiario.findByOperacaoIdAndDataPregao(op.getId(), hoje).isEmpty()) {
                SimuladorOperacaoDiarioEntity snapshot = new SimuladorOperacaoDiarioEntity();
                snapshot.setOperacaoId(op.getId());
                snapshot.setDataPregao(hoje);
                // preco, retorno_dia_pct, retorno_acum_pct, pnl_rs virão da cotação do dia (serviço externo)
                repositorioDiario.save(snapshot);
                log.info("{} - Snapshot criado: operação {} no pregão {}", SERVICO, op.getId(), hoje);
            }
        });

        log.info("{} - Rotina matinal concluída", SERVICO);
    }
}

