package br.com.miranda.gestor.ativos.brutos.service.coleta;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBrApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Unico lugar que decide QUEM consulta a BRAPI (monitoramento em camadas,
 * infra V13): liga cada {@link TipoColeta} a sua {@link PoliticaDeColeta} e
 * desliga tudo quando nao ha chave da BRAPI - o sistema segue funcionando so
 * com o preco oficial do dia anterior. QUANDO e o cron do agendador; QUANTO,
 * o OrcamentoBrapi.
 */
@Component
public class SeletorDeColetaBrapi {

    private final Map<TipoColeta, PoliticaDeColeta> politicas;
    private final ClienteBrApi clienteBrApi;
    private final Clock relogio;

    @Autowired
    public SeletorDeColetaBrapi(ClienteBrApi clienteBrApi) {
        this(clienteBrApi, Clock.systemUTC(), politicasPadrao());
    }

    public SeletorDeColetaBrapi(ClienteBrApi clienteBrApi, Clock relogio, Map<TipoColeta, PoliticaDeColeta> politicas) {
        this.clienteBrApi = clienteBrApi;
        this.relogio = relogio;
        this.politicas = politicas;
    }

    static Map<TipoColeta, PoliticaDeColeta> politicasPadrao() {
        Map<TipoColeta, PoliticaDeColeta> mapa = new EnumMap<>(TipoColeta.class);
        mapa.put(TipoColeta.COTACAO_E_HISTORICO, new ColetaIntradiariaNoPregao());
        mapa.put(TipoColeta.REFERENCIA_DIARIA, new SemColetaBrapi());
        mapa.put(TipoColeta.COTACAO, new SemColetaBrapi()); // legado = referencia diaria
        return mapa;
    }

    public boolean habilitada() {
        return clienteBrApi.habilitado();
    }

    /** Ativos cuja camada usa a BRAPI (os favoritos), independente do horario. */
    public List<AtivoMonitoradoEntity> elegiveis(List<AtivoMonitoradoEntity> ativos) {
        if (!habilitada()) {
            return List.of();
        }
        return ativos.stream().filter(a -> politica(a).usaBrapi()).toList();
    }

    /** Se o pregao esta aberto agora (dia util, 10:00-18:30 em Sao Paulo). */
    public boolean pregaoAberto() {
        return JanelaDePregao.B3.aberta(ZonedDateTime.now(relogio));
    }

    private PoliticaDeColeta politica(AtivoMonitoradoEntity ativo) {
        return politicas.getOrDefault(ativo.getTipoColeta(), new SemColetaBrapi());
    }
}
