package br.com.miranda.gestor.ativos.brutos.service.orcamento;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoOrcamentoBrapiTela;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Regras das chamadas a BRAPI disparadas pela tela (requisicao HTTP), fora do
 * agendador:
 *  - a mesma consulta feita ha menos de 30 min devolve a resposta guardada:
 *    no plano Gratuito o dado so muda a cada 30 min, entao repetir o clique
 *    so gastava cota (varios endpoints consultam "sem persistir");
 *  - consulta nova so com saldo no teto da tela (OrcamentoBrapi).
 *
 * Memoria de processo, de proposito: e so para nao repetir chamada; reiniciar
 * o gestor esvazia e o pior caso e uma chamada a mais.
 */
@Component
public class PoliticaChamadaDaTela {

    static final Duration VALIDADE = Duration.ofMinutes(30);
    static final int LIMITE_DE_ENTRADAS = 500;
    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");

    private final OrcamentoBrapi orcamento;
    private final Clock relogio;
    private final ConcurrentHashMap<String, Resposta> memoria = new ConcurrentHashMap<>();

    private record Resposta(String corpo, Instant em) {
    }

    @Autowired
    public PoliticaChamadaDaTela(OrcamentoBrapi orcamento) {
        this(orcamento, Clock.system(ZONA_BRASIL));
    }

    public PoliticaChamadaDaTela(OrcamentoBrapi orcamento, Clock relogio) {
        this.orcamento = orcamento;
        this.relogio = relogio;
    }

    /** Corpo da mesma consulta, se feita ha menos de 30 min. */
    public Optional<String> respostaRecente(String url) {
        Resposta guardada = memoria.get(url);
        if (guardada == null) {
            return Optional.empty();
        }
        if (vencida(guardada)) {
            memoria.remove(url, guardada);
            return Optional.empty();
        }
        return Optional.of(guardada.corpo());
    }

    /** Lanca ExcecaoOrcamentoBrapiTela quando a tela nao pode mais chamar a BRAPI neste mes. */
    public void exigirOrcamento(String recurso) {
        if (!orcamento.cabeChamadaDaTela(ZonedDateTime.now(relogio))) {
            throw new ExcecaoOrcamentoBrapiTela(recurso);
        }
    }

    public void guardar(String url, String corpo) {
        if (memoria.size() >= LIMITE_DE_ENTRADAS) {
            memoria.values().removeIf(this::vencida);
            if (memoria.size() >= LIMITE_DE_ENTRADAS) {
                memoria.clear();
            }
        }
        memoria.put(url, new Resposta(corpo, relogio.instant()));
    }

    private boolean vencida(Resposta resposta) {
        return Duration.between(resposta.em(), relogio.instant()).compareTo(VALIDADE) >= 0;
    }
}
