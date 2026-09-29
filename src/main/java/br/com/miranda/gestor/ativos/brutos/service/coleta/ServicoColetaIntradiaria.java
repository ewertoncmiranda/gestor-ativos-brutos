package br.com.miranda.gestor.ativos.brutos.service.coleta;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoCotaBrapiEsgotada;
import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.CotacaoAtualEntity;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAtivoMonitorado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCotacaoAtual;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoCache;
import br.com.miranda.gestor.ativos.brutos.service.orcamento.ModoColeta;
import br.com.miranda.gestor.ativos.brutos.service.orcamento.OrcamentoBrapi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Decide SE cada rotina da BRAPI roda agora (pregao aberto, chave presente,
 * orcamento do mes) e delega a coleta ao ServicoAtualizacaoCache. E o unico
 * lugar que trata o 429: interrompe e registra BRAPI_ORCAMENTO=ERRO.
 */
@Slf4j
@Service
public class ServicoColetaIntradiaria {

    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");

    /** A BRAPI (plano Gratuito) so atualiza o dado a cada 30 min. */
    static final Duration JANELA_ATUALIZACAO_BRAPI = Duration.ofMinutes(30);

    private final ServicoAtualizacaoCache cache;
    private final SeletorDeColetaBrapi seletor;
    private final OrcamentoBrapi orcamento;
    private final RepositorioAtivoMonitorado ativos;
    private final RepositorioCotacaoAtual cotacoes;
    private final Clock relogio;

    @Autowired
    public ServicoColetaIntradiaria(ServicoAtualizacaoCache cache, SeletorDeColetaBrapi seletor,
                                    OrcamentoBrapi orcamento, RepositorioAtivoMonitorado ativos,
                                    RepositorioCotacaoAtual cotacoes) {
        this(cache, seletor, orcamento, ativos, cotacoes, Clock.system(ZONA_BRASIL));
    }

    public ServicoColetaIntradiaria(ServicoAtualizacaoCache cache, SeletorDeColetaBrapi seletor,
                                    OrcamentoBrapi orcamento, RepositorioAtivoMonitorado ativos,
                                    RepositorioCotacaoAtual cotacoes, Clock relogio) {
        this.cache = cache;
        this.seletor = seletor;
        this.orcamento = orcamento;
        this.ativos = ativos;
        this.cotacoes = cotacoes;
        this.relogio = relogio;
    }

    /** Ciclo das :05 e :35 (10h-17h): cotacao dos favoritos, se o orcamento deixar. */
    public void executarCiclo() {
        if (!seletor.pregaoAberto()) {
            return;
        }
        List<AtivoMonitoradoEntity> favoritos = seletor.elegiveis(ativos.findByAtivoTrue());
        if (favoritos.isEmpty()) {
            return;
        }
        ZonedDateTime agora = ZonedDateTime.now(relogio).withZoneSameInstant(ZONA_BRASIL);
        ModoColeta modo = orcamento.modoPara(agora, favoritos.size());
        if (!modo.rodaNoMinuto(agora.getMinute())) {
            log.info("(COLETA-INTRADIARIA)-Ciclo das {} pulado: modo {}", agora.toLocalTime(), modo);
            return;
        }
        executarProtegido(agora, () -> cache.coletarCotacoes(favoritos));
    }

    /** Perfis da BRAPI (so favoritos, semanal por ativo), no cron das 08:30. */
    public void atualizarPerfis() {
        ZonedDateTime agora = ZonedDateTime.now(relogio).withZoneSameInstant(ZONA_BRASIL);
        if (!orcamento.cabeChamadaAvulsa(agora)) {
            log.warn("(COLETA-INTRADIARIA)-Perfis nao atualizados: cota do mes esgotada");
            return;
        }
        executarProtegido(agora, cache::atualizarPerfilEmpresa);
    }

    /**
     * Ao favoritar: se o ativo nao tem candle nenhum, o historico de 3 meses
     * uma unica vez; depois a cotacao agora. Sem chave ou sem cota, fica para
     * o proximo ciclo - favoritar nunca falha por causa da BRAPI.
     *
     * A ordem importa: a cotacao grava o candle do dia, e com ele o
     * "nao tem candle nenhum" do historico ja seria falso - o favorito novo
     * ficava so com o candle de hoje (visto em 2026-09-29 com ABCB4, ITUB3...).
     *
     * Refavoritar (ou tirar e pôr de volta) um ativo com cotacao buscada ha
     * menos de 30 min nao chama a BRAPI: ela devolveria o mesmo dado.
     */
    public void coletarAoFavoritar(String simbolo) {
        ZonedDateTime agora = ZonedDateTime.now(relogio).withZoneSameInstant(ZONA_BRASIL);
        if (!seletor.habilitada() || !orcamento.cabeChamadaAvulsa(agora)) {
            return;
        }
        executarProtegido(agora, () -> {
            cache.preencherHistoricoInicial(simbolo);
            if (cotacaoRecente(simbolo)) {
                log.info("(COLETA-INTRADIARIA)-Cotacao de {} tem menos de {} min; sem nova chamada",
                        simbolo, JANELA_ATUALIZACAO_BRAPI.toMinutes());
                return;
            }
            cache.coletarCotacao(simbolo);
        });
    }

    private boolean cotacaoRecente(String simbolo) {
        // atualizado_em e gravado com LocalDateTime.now() do container (UTC):
        // compara no mesmo fuso, nao no de Brasilia do relogio.
        LocalDateTime agoraNoFusoGravado = LocalDateTime.ofInstant(relogio.instant(), ZoneId.systemDefault());
        return cotacoes.findBySimbolo(simbolo)
                .map(CotacaoAtualEntity::getAtualizadoEm)
                .filter(em -> Duration.between(em, agoraNoFusoGravado).compareTo(JANELA_ATUALIZACAO_BRAPI) < 0)
                .isPresent();
    }

    private void executarProtegido(ZonedDateTime agora, Runnable coleta) {
        try {
            coleta.run();
        } catch (ExcecaoCotaBrapiEsgotada e) {
            orcamento.registrarCotaEsgotada(agora, e.getMessage());
        } catch (RuntimeException e) {
            log.error("(COLETA-INTRADIARIA)-Falha na coleta: {}", e.getMessage(), e);
        }
    }
}
