package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoLimiteFavoritos;
import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAtivoMonitorado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoAtivoMonitorado {

    // Favorito: cotacao intradiaria a cada 15 min, so no pregao (infra V13).
    // Era 30 s - um favorito sozinho estourava a cota gratuita da BRAPI.
    private static final int INTERVALO_PADRAO_SEGUNDOS = 900;

    // Universo de referencia por setor: cotacao serve so pra visao "Mercado por
    // setor", nao pra decisao de trading - 1h de atraso e aceitavel e evita
    // multiplicar por ~10x o volume de chamadas a BRAPI que os poucos ativos
    // efetivamente monitorados (30s) hoje geram. Sem historico diario (so
    // COTACAO): candle desses ativos nao e usado em lugar nenhum ainda.
    private static final int INTERVALO_REFERENCIA_SEGUNDOS = 3600;

    private final RepositorioAtivoMonitorado repositorio;
    private final RepositorioIdentidadeAtivo repositorioIdentidade;

    // Teto de favoritos que a cota gratuita da BRAPI comporta a cada 30 min:
    // (13.500 - 390) / (16 ciclos x 23 pregoes) ~ 35 (plano de atualizacao diaria, D3).
    @Value("${brapi.favoritos.max:35}")
    private int maxFavoritos = 35;

    /**
     * Codigo canonico do ativo (ELET3 -> AXIA3). Todo registro passa por aqui:
     * o universo guarda um codigo so por empresa, o mesmo que a BRAPI devolve.
     */
    public String canonizar(String codigoAtivo) {
        String simbolo = codigoAtivo.trim().toUpperCase();
        String canonico = repositorioIdentidade.canonico(simbolo);
        if (!canonico.equals(simbolo)) {
            log.info("{} - {} e codigo antigo; usando o canonico {}", SERVICO, simbolo, canonico);
        }
        return canonico;
    }

    /**
     * Registra um ativo como favorito (cotacao + historico intradiario a cada
     * INTERVALO_PADRAO_SEGUNDOS). Se o ativo ja existir como referencia de
     * setor (tipoColeta REFERENCIA_DIARIA/COTACAO, sem BRAPI), promove pra
     * favorito - sem isso, favoritar um ativo que ja e referencia (ex.:
     * PETR4) so reativava sem nunca ligar a coleta intradiaria.
     */
    public AtivoMonitoradoEntity registrar(String codigoAtivo) {
        String simbolo = canonizar(codigoAtivo);

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

        boolean jaEraFavorito = Boolean.TRUE.equals(entidade.getAtivo())
                && entidade.getTipoColeta() == TipoColeta.COTACAO_E_HISTORICO;
        if (!jaEraFavorito && listarFavoritos().size() >= maxFavoritos) {
            throw new ExcecaoLimiteFavoritos(maxFavoritos);
        }

        if (entidade.getTipoColeta() != TipoColeta.COTACAO_E_HISTORICO) {
            entidade.setTipoColeta(TipoColeta.COTACAO_E_HISTORICO);
            entidade.setIntervaloSegundos(INTERVALO_PADRAO_SEGUNDOS);
        }

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
        String simbolo = canonizar(codigoAtivo);
        if (repositorio.findBySimbolo(simbolo).isPresent()) {
            return;
        }

        AtivoMonitoradoEntity nova = new AtivoMonitoradoEntity();
        nova.setSimbolo(simbolo);
        nova.setTipoColeta(TipoColeta.REFERENCIA_DIARIA);
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
        java.util.stream.Stream.of(TipoColeta.REFERENCIA_DIARIA, TipoColeta.COTACAO)
                .flatMap(tipo -> repositorio.findByTipoColeta(tipo).stream())
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

    /** Favoritos ativos (tipoColeta=COTACAO_E_HISTORICO) - a lista da tela de Favoritos. */
    public List<AtivoMonitoradoEntity> listarFavoritos() {
        return repositorio.findByTipoColetaAndAtivoTrueOrderBySimboloAsc(TipoColeta.COTACAO_E_HISTORICO);
    }

    /**
     * Desfavorita: so desativa (ativo=false), nunca apaga a linha - o mesmo
     * padrao de "soft delete" que desativarReferenciasObsoletas ja usa. So
     * mexe em favorito de verdade (COTACAO_E_HISTORICO); referencia de setor
     * nao se desfavorita por aqui.
     */
    public void desfavoritar(String codigoAtivo) {
        String simbolo = canonizar(codigoAtivo);
        repositorio.findBySimbolo(simbolo)
                .filter(entidade -> entidade.getTipoColeta() == TipoColeta.COTACAO_E_HISTORICO)
                .ifPresent(entidade -> {
                    entidade.setAtivo(Boolean.FALSE);
                    repositorio.save(entidade);
                    log.info("{} - Favorito desativado: {}", SERVICO, simbolo);
                });
    }

    public List<AtivoMonitoradoEntity> listarAtivosParaMonitorar() {
        return repositorio.findByAtivoTrue();
    }

    /**
     * Marca a ultima coleta (coluna "Ultima atualizacao" da tela). Um UPDATE
     * so da data, sem save da entidade: o save incrementava a versao (@Version)
     * a cada ciclo - foi assim que a RAIZ4, anos a 30 s, chegou a versao 1826.
     */
    public void marcarProcessado(AtivoMonitoradoEntity entidade) {
        repositorio.marcarAtualizado(entidade.getId(), LocalDateTime.now());
    }
}
