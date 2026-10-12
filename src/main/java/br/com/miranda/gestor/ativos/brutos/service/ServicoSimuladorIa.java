package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorIaAnaliseDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorIaAnalise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

import static br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorIaAnaliseDTO.STATUS_INDISPONIVEL;
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
     * Se não houver em cache, retorna indisponibilidade explícita até existir cliente insider-ia.
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
        // Reutiliza somente respostas reais. Linhas sintéticas gravadas por versões
        // antigas não podem ser apresentadas ao usuário como análise da IA.
        return repositorio.findBySimboloAndDataAnalise(simbolo, hoje)
                .filter(this::temAnaliseReal)
                .map(entidade -> {
                    log.info("{} - Análise em cache encontrada para {}", SERVICO, simbolo);
                    return SimuladorIaAnaliseDTO.de(entidade, true);
                })
                .orElseGet(() -> {
                    log.info("{} - Análise não em cache e integração insider-ia indisponível para {}", SERVICO, simbolo);
                    return SimuladorIaAnaliseDTO.builder()
                            .simbolo(ativoOpt.get().getSimbolo())
                            .status(STATUS_INDISPONIVEL)
                            .erro("Integração insider-ia não configurada.")
                            .emCache(false)
                            .build();
                });
    }

    private boolean temAnaliseReal(br.com.miranda.gestor.ativos.brutos.external.SimuladorIaAnaliseEntity entidade) {
        String modelo = entidade.getModeloIa();
        return entidade.getAnaliseTexto() != null
                && !entidade.getAnaliseTexto().isBlank()
                && modelo != null
                && !modelo.isBlank()
                && !"stub".equalsIgnoreCase(modelo)
                && !"indisponivel".equalsIgnoreCase(modelo);
    }
}

