package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.CotacaoAtualEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoMonitoradoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCotacaoAtual;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivo;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivoMonitorado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

/**
 * Tela "Favoritos": a lista pessoal do usuario, com cotacao intradiaria via
 * BRAPI (infra V13, tipoColeta=COTACAO_E_HISTORICO) - diferente de
 * {@link BaseAtivosController}, que mostra o universo amplo sem BRAPI, e
 * diferente de {@code AtivoController#listarRegistrados}, que ainda mistura
 * favoritos com as referencias de setor.
 *
 * Responsabilidade unica (favoritos, so favoritos); as mutacoes de
 * ativo_monitorado continuam concentradas em {@link ServicoAtivoMonitorado} -
 * este controller so traduz HTTP para chamadas nele, sem regra propria.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FavoritosController {

    private final ServicoAtivoMonitorado servicoAtivoMonitorado;
    private final ServicoAtivo servicoAtivo;
    private final RepositorioCotacaoAtual repositorioCotacaoAtual;

    @GetMapping("/favoritos")
    public ResponseEntity<List<AtivoMonitoradoDTO>> listar() {
        Map<String, CotacaoAtualEntity> cotacoesPorSimbolo = repositorioCotacaoAtual.findAll().stream()
                .collect(Collectors.toMap(CotacaoAtualEntity::getSimbolo, Function.identity()));

        List<AtivoMonitoradoDTO> favoritos = servicoAtivoMonitorado.listarFavoritos().stream()
                .map((AtivoMonitoradoEntity entidade) -> AtivoMonitoradoDTO.de(entidade, cotacoesPorSimbolo.get(entidade.getSimbolo())))
                .toList();
        return ResponseEntity.ok(favoritos);
    }

    /**
     * Favorita e ja dispara a primeira coleta agora, sem esperar o proximo
     * ciclo do agendador - mesmo comportamento que
     * {@code AtivoController#registrarAtivo} ja tinha.
     */
    @PostMapping("/favoritos/{simbolo}")
    public ResponseEntity<Void> favoritar(@PathVariable String simbolo) {
        servicoAtivoMonitorado.registrar(simbolo);
        try {
            servicoAtivo.processarRobusto(simbolo);
        } catch (Exception e) {
            log.warn("{}-Falha na coleta imediata ao favoritar {}; o agendador tentara novamente: {}",
                    CONTROLADOR, simbolo, e.getMessage());
        }
        log.info("{}-Favorito adicionado: {}", CONTROLADOR, simbolo);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/favoritos/{simbolo}")
    public ResponseEntity<Void> desfavoritar(@PathVariable String simbolo) {
        servicoAtivoMonitorado.desfavoritar(simbolo);
        log.info("{}-Favorito removido: {}", CONTROLADOR, simbolo);
        return ResponseEntity.noContent().build();
    }
}
