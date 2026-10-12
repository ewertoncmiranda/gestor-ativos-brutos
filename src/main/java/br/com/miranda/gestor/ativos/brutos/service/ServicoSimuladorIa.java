package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import br.com.miranda.gestor.ativos.brutos.external.SimuladorIaAnaliseEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorIaAnaliseDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorIaAnalise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

/**
 * SIM-GES-5: Análise IA sob demanda com cache diário.
 * Verifica cache antes de chamar insider-ia; nunca cobra token duas vezes no mesmo dia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoSimuladorIa {

    private final RepositorioSimuladorIaAnalise repositorio;
    private final RepositorioSimuladorAtivo repositorioAtivo;

    /**
     * Busca análise IA para um símbolo, verificando cache do dia.
     * Se não houver em cache, chamaria insider-ia (stub aqui - integração futura).
     * Retorna resposta com flags emCache e salvoEm.
     */
    public SimuladorIaAnaliseDTO analisarSimbolo(String simbolo) {
        LocalDate hoje = LocalDate.now();
        log.info("{} - Buscando análise IA para {} em {}", SERVICO, simbolo, hoje);

        // Busca ativo_id pelo simbolo
        Optional<SimuladorAtivoEntity> ativoOpt = repositorioAtivo.findBySimbolo(simbolo);
        if (ativoOpt.isEmpty()) {
            throw new IllegalArgumentException("Ativo não monitorado no simulador: " + simbolo);
        }
        Integer ativoId = ativoOpt.get().getId();

        // Verifica cache do dia
        return repositorio.findBySimboloAndDataAnalise(simbolo, hoje)
                .map(entidade -> {
                    log.info("{} - Análise em cache encontrada para {}", SERVICO, simbolo);
                    return SimuladorIaAnaliseDTO.de(entidade, true);
                })
                .orElseGet(() -> {
                    log.info("{} - Análise não em cache; chamando insider-ia para {}", SERVICO, simbolo);
                    // TODO: integração com insider-ia (POST /insider/analise/{simbolo})
                    // Por enquanto, retorna stub vazio
                    SimuladorIaAnaliseEntity nova = new SimuladorIaAnaliseEntity();
                    nova.setAtivoId(ativoId);
                    nova.setDataAnalise(LocalDateTime.now());
                    nova.setDadosEntradaJson("{}");
                    nova.setAnaliseTexto("Integração com insider-ia pendente");
                    nova.setModeloIa("stub");
                    nova.setTokensUsados(0);

                    SimuladorIaAnaliseEntity salva = repositorio.save(nova);
                    log.info("{} - Análise salva para {} (não em cache)", SERVICO, simbolo);
                    return SimuladorIaAnaliseDTO.de(salva, false);
                });
    }
}

