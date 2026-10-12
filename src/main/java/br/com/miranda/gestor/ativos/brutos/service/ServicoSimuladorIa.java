package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoInsiderIaIndisponivel;
import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import br.com.miranda.gestor.ativos.brutos.external.SimuladorIaAnaliseEntity;
import br.com.miranda.gestor.ativos.brutos.external.SimuladorResultadoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsiderIaAnaliseRespostaDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsiderIaResultadoItemDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorIaAnaliseDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteInsiderIa;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorIaAnalise;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorResultado;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorIaAnaliseDTO.STATUS_INDISPONIVEL;
import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

/**
 * SIM-GES-5: Análise IA sob demanda com cache diário.
 * Verifica cache antes de chamar insider-ia; nunca cobra token duas vezes no mesmo dia.
 * Se insider-ia estiver offline, degrada graciosamente para STATUS_INDISPONIVEL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoSimuladorIa {

    private final RepositorioSimuladorIaAnalise repositorio;
    private final RepositorioSimuladorAtivo repositorioAtivo;
    private final RepositorioSimuladorResultado repositorioResultado;
    private final ClienteInsiderIa clienteInsiderIa;
    private final ObjectMapper objectMapper;

    /**
     * Busca análise IA para um símbolo, verificando cache do dia.
     * Se não houver em cache, chama insider-ia e salva o resultado.
     * Se insider-ia estiver indisponível, retorna STATUS_INDISPONIVEL sem lançar exceção.
     */
    public SimuladorIaAnaliseDTO analisarSimbolo(String simbolo) {
        LocalDate hoje = LocalDate.now();
        log.info("{} - Buscando análise IA para {} em {}", SERVICO, simbolo, hoje);

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
                .orElseGet(() -> chamarInsiderIaOuIndisponivel(simbolo, ativoOpt.get()));
    }

    private SimuladorIaAnaliseDTO chamarInsiderIaOuIndisponivel(String simbolo, SimuladorAtivoEntity ativo) {
        try {
            List<SimuladorResultadoEntity> resultados =
                    repositorioResultado.findMaisRecentePorSimbolo(simbolo);

            List<InsiderIaResultadoItemDTO> itens = resultados.stream()
                    .map(r -> InsiderIaResultadoItemDTO.builder()
                            .versao(r.getVersaoEstrategia())
                            .quintil(r.getQuintil())
                            .retornoPct(r.getRetornoPct())
                            .build())
                    .collect(Collectors.toList());

            InsiderIaAnaliseRespostaDTO resposta = clienteInsiderIa.analisarSimulador(simbolo, itens);

            SimuladorIaAnaliseEntity entidade = new SimuladorIaAnaliseEntity();
            entidade.setAtivoId(ativo.getId());
            entidade.setDataAnalise(LocalDateTime.now());
            entidade.setAnaliseTexto(resposta.getAnalise());
            entidade.setModeloIa(resposta.getModelo());
            entidade.setTokensUsados(resposta.getTokensUsados());
            entidade.setDadosEntradaJson(serializarItens(itens));

            SimuladorIaAnaliseEntity salva = repositorio.save(entidade);
            log.info("{} - Análise IA salva para simbolo={} modelo={}", SERVICO, simbolo, resposta.getModelo());

            return SimuladorIaAnaliseDTO.de(salva, false);

        } catch (ExcecaoInsiderIaIndisponivel e) {
            log.warn("{} - insider-ia indisponível para {}; retornando STATUS_INDISPONIVEL. Causa: {}",
                    SERVICO, simbolo, e.getMessage());
            return SimuladorIaAnaliseDTO.builder()
                    .simbolo(simbolo)
                    .status(STATUS_INDISPONIVEL)
                    .erro("Serviço insider-ia temporariamente indisponível.")
                    .emCache(false)
                    .build();
        }
    }

    private String serializarItens(List<InsiderIaResultadoItemDTO> itens) {
        try {
            return objectMapper.writeValueAsString(itens);
        } catch (JsonProcessingException e) {
            log.warn("{} - Falha ao serializar dados_entrada_json: {}", SERVICO, e.getMessage());
            return "[]";
        }
    }

    private boolean temAnaliseReal(SimuladorIaAnaliseEntity entidade) {
        String modelo = entidade.getModeloIa();
        return entidade.getAnaliseTexto() != null
                && !entidade.getAnaliseTexto().isBlank()
                && modelo != null
                && !modelo.isBlank()
                && !"stub".equalsIgnoreCase(modelo)
                && !"indisponivel".equalsIgnoreCase(modelo);
    }
}
