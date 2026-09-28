package br.com.miranda.gestor.ativos.brutos.service.coleta;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.CotacaoAtualEntity;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCotacaoAtual;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSnapshotFechamentoBrapi;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSnapshotFechamentoBrapi.Foto;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivoMonitorado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Foto das 17:40 (plano de atualizacao diaria, D4/B5): copia a ultima cotacao
 * de cada favorito (a do ciclo das 17:35) para snapshot_fechamento_brapi, onde
 * a conciliacao da manha seguinte a compara com o COTAHIST. Nenhuma chamada a
 * BRAPI - custo zero de cota.
 *
 * Favorito sem cotacao do dia entra como SEM_DADO, para a conciliacao saber
 * que faltou. Se NENHUM favorito tem cotacao do dia (feriado, sem chave,
 * coleta desligada pelo orcamento), nao grava nada.
 */
@Slf4j
@Service
public class ServicoSnapshotFechamento {

    static final String OK = "OK";
    static final String SEM_DADO = "SEM_DADO";
    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");

    private final ServicoAtivoMonitorado favoritos;
    private final RepositorioCotacaoAtual cotacoes;
    private final RepositorioSnapshotFechamentoBrapi fotos;
    private final Clock relogio;

    @Autowired
    public ServicoSnapshotFechamento(ServicoAtivoMonitorado favoritos, RepositorioCotacaoAtual cotacoes,
                                     RepositorioSnapshotFechamentoBrapi fotos) {
        this(favoritos, cotacoes, fotos, Clock.system(ZONA_BRASIL));
    }

    public ServicoSnapshotFechamento(ServicoAtivoMonitorado favoritos, RepositorioCotacaoAtual cotacoes,
                                     RepositorioSnapshotFechamentoBrapi fotos, Clock relogio) {
        this.favoritos = favoritos;
        this.cotacoes = cotacoes;
        this.fotos = fotos;
        this.relogio = relogio;
    }

    /** @return quantas fotos foram gravadas agora */
    public int fotografar() {
        LocalDateTime agora = LocalDateTime.now(relogio.withZone(ZONA_BRASIL));
        LocalDate hoje = agora.toLocalDate();
        List<Foto> lote = montar(favoritos.listarFavoritos(), hoje, agora);
        if (lote.stream().noneMatch(f -> OK.equals(f.status()))) {
            log.info("(SNAPSHOT-FECHAMENTO)-Nenhum favorito com cotacao de {}; nada a fotografar", hoje);
            return 0;
        }
        int gravadas = 0;
        try {
            for (Foto foto : lote) {
                gravadas += fotos.gravar(foto);
            }
        } catch (DataAccessException e) {
            log.error("(SNAPSHOT-FECHAMENTO)-Falha ao gravar (migracao V15 aplicada?): {}",
                    e.getMostSpecificCause().getMessage());
        }
        log.info("(SNAPSHOT-FECHAMENTO)-{} foto(s) de {} gravada(s) ({} favorito(s))", gravadas, hoje, lote.size());
        return gravadas;
    }

    List<Foto> montar(List<AtivoMonitoradoEntity> lista, LocalDate hoje, LocalDateTime agora) {
        List<Foto> lote = new ArrayList<>();
        for (AtivoMonitoradoEntity favorito : lista) {
            String simbolo = favorito.getSimbolo();
            Optional<CotacaoAtualEntity> cotacao = cotacoes.findBySimbolo(simbolo)
                    .filter(c -> c.getRegularMarketPrice() != null)
                    .filter(c -> hoje.equals(horarioDoDado(c.getRegularMarketTime()).map(LocalDateTime::toLocalDate).orElse(null)));
            lote.add(cotacao
                    .map(c -> new Foto(simbolo, hoje, c.getRegularMarketOpen(), c.getRegularMarketDayHigh(),
                            c.getRegularMarketDayLow(), c.getRegularMarketPrice(), volume(c.getRegularMarketVolume()),
                            horarioDoDado(c.getRegularMarketTime()).orElse(null), agora, OK))
                    .orElseGet(() -> new Foto(simbolo, hoje, null, null, null, null, null, null, agora, SEM_DADO)));
        }
        return lote;
    }

    /** regularMarketTime vem da BRAPI em ISO UTC ("2026-09-28T20:07:00.000Z"). */
    static Optional<LocalDateTime> horarioDoDado(String regularMarketTime) {
        try {
            return Optional.of(LocalDateTime.ofInstant(Instant.parse(regularMarketTime), ZONA_BRASIL));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Long volume(BigDecimal volume) {
        return volume == null ? null : volume.longValue();
    }
}
