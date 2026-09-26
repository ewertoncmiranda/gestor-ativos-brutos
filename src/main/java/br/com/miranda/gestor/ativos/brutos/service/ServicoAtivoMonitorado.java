package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAtivoMonitorado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoAtivoMonitorado {

    private static final int INTERVALO_PADRAO_SEGUNDOS = 30;

    // Universo de referencia por setor: cotacao serve so pra visao "Mercado por
    // setor", nao pra decisao de trading - 1h de atraso e aceitavel e evita
    // multiplicar por ~10x o volume de chamadas a BRAPI que os poucos ativos
    // efetivamente monitorados (30s) hoje geram. Sem historico diario (so
    // COTACAO): candle desses ativos nao e usado em lugar nenhum ainda.
    private static final int INTERVALO_REFERENCIA_SEGUNDOS = 3600;

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

    /**
     * Registra um ativo so como referencia de setor (cotacao a cada
     * INTERVALO_REFERENCIA_SEGUNDOS, sem historico diario). Se o simbolo ja
     * estiver registrado - inclusive como monitorado de verdade, com
     * intervalo mais curto - nao toca nele: registro de referencia nunca
     * rebaixa um ativo que ja esta sendo acompanhado de perto.
     */
    public void registrarReferenciaSetor(String codigoAtivo) {
        String simbolo = codigoAtivo.trim().toUpperCase();
        if (repositorio.findBySimbolo(simbolo).isPresent()) {
            return;
        }

        AtivoMonitoradoEntity nova = new AtivoMonitoradoEntity();
        nova.setSimbolo(simbolo);
        nova.setTipoColeta(TipoColeta.COTACAO);
        nova.setIntervaloSegundos(INTERVALO_REFERENCIA_SEGUNDOS);
        nova.setAtivo(Boolean.TRUE);
        nova.setAtualizadoEm(LocalDateTime.now());
        repositorio.save(nova);
        log.info("{} - Ativo de referencia de setor registrado: {}", SERVICO, simbolo);
    }

    /**
     * Desativa todo registro de referencia de setor (tipoColeta=COTACAO) cujo
     * simbolo nao esta mais em tickersValidos. Usado quando o universo de
     * referencia hardcoded encolhe (ex.: reduzido depois de estourar cota da
     * BRAPI) - sem isso, o agendador continuaria tentando atualizar tickers
     * que ja nao fazem parte do universo, so gastando cota sem propósito.
     * Nao toca em nada com tipoColeta=COTACAO_E_HISTORICO: aquele e o ativo
     * monitorado de verdade, cadastrado pelo usuario, nunca desativado por aqui.
     */
    public void desativarReferenciasObsoletas(Set<String> tickersValidos) {
        repositorio.findByTipoColeta(TipoColeta.COTACAO).stream()
                .filter(entidade -> entidade.getAtivo() && !tickersValidos.contains(entidade.getSimbolo()))
                .forEach(entidade -> {
                    entidade.setAtivo(Boolean.FALSE);
                    repositorio.save(entidade);
                    log.info("{} - Referencia de setor obsoleta desativada: {}", SERVICO, entidade.getSimbolo());
                });
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
