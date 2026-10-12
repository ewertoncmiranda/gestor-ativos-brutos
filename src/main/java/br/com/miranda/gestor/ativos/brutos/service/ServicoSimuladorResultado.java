package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorResultadoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorResultado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoSimuladorResultado {

    private final RepositorioSimuladorResultado repositorio;

    /**
     * SIM-GES-2: Retorna todas as 7 estratégias para um ativo no pregão mais recente.
     * Se data for null, usa o pregão mais recente disponível.
     * Campos ausentes retornam null, nunca zero.
     */
    public List<SimuladorResultadoDTO> buscarResultados(String simbolo, LocalDate data) {
        log.info("{} - Buscando resultados simulador para {} em {}", SERVICO, simbolo, data);

        List<SimuladorResultadoDTO> resultados = repositorio.findResultados(simbolo, data)
                .stream()
                .map(SimuladorResultadoDTO::de)
                .collect(Collectors.toList());

        if (resultados.isEmpty()) {
            log.info("{} - Nenhum resultado encontrado para {} em {}", SERVICO, simbolo, data);
        }

        return resultados;
    }

    /**
     * Busca resultados do pregão mais recente disponível para um ativo.
     */
    public List<SimuladorResultadoDTO> buscarMaisRecente(String simbolo) {
        log.info("{} - Buscando resultados mais recentes para {}", SERVICO, simbolo);

        return repositorio.findMaisRecentePorSimbolo(simbolo)
                .stream()
                .map(SimuladorResultadoDTO::de)
                .collect(Collectors.toList());
    }
}

