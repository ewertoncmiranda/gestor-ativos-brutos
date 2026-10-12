package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAtivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoSimuladorAtivo {

    private final RepositorioSimuladorAtivo repositorio;
    private final RepositorioIdentidadeAtivo repositorioIdentidade;

    public String canonizar(String codigoAtivo) {
        String simbolo = codigoAtivo.trim().toUpperCase();
        try {
            String canonico = repositorioIdentidade.canonico(simbolo);
            if (!canonico.equals(simbolo)) {
                log.info("{} - {} é código antigo; usando o canônico {}", SERVICO, simbolo, canonico);
            }
            return canonico;
        } catch (Exception e) {
            log.error("{} - Erro ao canonizar o símbolo {}: {}", SERVICO, simbolo, e.getMessage());
            throw new IllegalArgumentException("Símbolo inválido ou não encontrado: " + simbolo);
        }
    }

    public SimuladorAtivoDTO registrar(String codigoAtivo) {
        String simbolo = canonizar(codigoAtivo);

        Optional<SimuladorAtivoEntity> existente = repositorio.findBySimbolo(simbolo);
        SimuladorAtivoEntity entidade = existente
                .orElseGet(() -> {
                    SimuladorAtivoEntity nova = new SimuladorAtivoEntity();
                    nova.setSimbolo(simbolo);
                    nova.setJanelaPregoes(63);
                    nova.setCapitalInicial(new BigDecimal("100000.00"));
                    nova.setDataInclusao(LocalDate.now());
                    nova.setAtivo(Boolean.TRUE);
                    return nova;
                });

        if (existente.isPresent()) {
            entidade.setAtivo(Boolean.TRUE);
        }

        SimuladorAtivoEntity salva = repositorio.save(entidade);
        log.info("{} - Ativo simulado registrado/reativado: {}", SERVICO, simbolo);
        return SimuladorAtivoDTO.de(salva);
    }

    public void desativar(String codigoAtivo) {
        String simbolo = codigoAtivo.trim().toUpperCase();
        repositorio.findBySimbolo(simbolo)
                .ifPresent(entidade -> {
                    entidade.setAtivo(Boolean.FALSE);
                    repositorio.save(entidade);
                    log.info("{} - Ativo simulado desativado: {}", SERVICO, simbolo);
                });
    }

    public List<SimuladorAtivoDTO> listarAtivos() {
        return repositorio.findByAtivoTrueOrderBySimboloAsc()
                .stream()
                .map(SimuladorAtivoDTO::de)
                .collect(Collectors.toList());
    }

    public List<SimuladorAtivoDTO> listarTodos() {
        return repositorio.findAllByOrderBySimboloAsc()
                .stream()
                .map(SimuladorAtivoDTO::de)
                .collect(Collectors.toList());
    }
}
