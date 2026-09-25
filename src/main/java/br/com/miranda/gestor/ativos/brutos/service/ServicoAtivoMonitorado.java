package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAtivoMonitorado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoAtivoMonitorado {

    private static final int INTERVALO_PADRAO_SEGUNDOS = 30;

    private final RepositorioAtivoMonitorado repositorio;

    /**
     * Registra um ativo para monitoramento recorrente (cotacao + serie historica a cada
     * INTERVALO_PADRAO_SEGUNDOS). Se o ativo ja estiver cadastrado, so reativa.
     */
    public AtivoMonitoradoEntity registrar(String codigoAtivo) {
        String simbolo = codigoAtivo.trim().toUpperCase();

        AtivoMonitoradoEntity entidade = repositorio.findBySimbolo(simbolo)
                .orElseGet(() -> {
                    AtivoMonitoradoEntity nova = new AtivoMonitoradoEntity();
                    nova.setSimbolo(simbolo);
                    nova.setTipoColeta(TipoColeta.COTACAO_E_HISTORICO);
                    nova.setIntervaloSegundos(INTERVALO_PADRAO_SEGUNDOS);
                    // A coluna e NOT NULL; o DEFAULT CURRENT_TIMESTAMP do banco so vale
                    // quando a coluna e omitida do INSERT, e o Hibernate sempre a inclui.
                    nova.setAtualizadoEm(LocalDateTime.now());
                    return nova;
                });

        entidade.setAtivo(Boolean.TRUE);
        AtivoMonitoradoEntity salva = repositorio.save(entidade);
        log.info("{} - Ativo monitorado registrado/reativado: {}", SERVICO, simbolo);
        return salva;
    }

    public List<AtivoMonitoradoEntity> listar() {
        return repositorio.findAllByOrderBySimboloAsc();
    }

    public List<AtivoMonitoradoEntity> listarAtivosParaMonitorar() {
        return repositorio.findByAtivoTrue();
    }

    /**
     * Marca a entidade como processada agora - usado pelo agendador apos cada ciclo,
     * pra que o proximo ciclo so ocorra depois de intervaloSegundos.
     */
    public void marcarProcessado(AtivoMonitoradoEntity entidade) {
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorio.save(entidade);
    }
}
