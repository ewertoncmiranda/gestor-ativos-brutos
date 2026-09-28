package br.com.miranda.gestor.ativos.brutos.entrypoint.schedule;

import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoIndicadoresIbge;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoIndicesMacro;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoProventos;
import br.com.miranda.gestor.ativos.brutos.service.coleta.ServicoColetaIntradiaria;
import br.com.miranda.gestor.ativos.brutos.service.coleta.ServicoSnapshotFechamento;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.AGENDADOR;

/**
 * Horarios fixos do gestor (plano infra PLANO-ATUALIZACAO-DIARIA, frente B).
 * Tudo por cron no fuso de Sao Paulo - nada roda na subida do container, e a
 * maquina desligada nao acumula disparos atrasados.
 *
 *   08:30 seg-sex        indices macro (BCB), IBGE, proventos e perfis da BRAPI
 *   :05 e :35, 10h-17h   cotacao dos favoritos (BRAPI; dado da BRAPI muda a cada 30 min)
 *   17:40 seg-sex        foto de fechamento dos favoritos, sem chamar a BRAPI
 *
 * O historico a cada 5 min foi removido: o candle do dia sai da cotacao, e o
 * historico de 3 meses so e buscado uma vez, ao favoritar.
 */
@Slf4j
@Component
@EnableScheduling
@AllArgsConstructor
public class AgendadorCacheAtivos {

    static final String ZONA = "America/Sao_Paulo";
    static final String CRON_MANHA = "0 30 8 * * MON-FRI";
    static final String CRON_INTRADIARIO = "0 5,35 10-17 * * MON-FRI";
    static final String CRON_FOTO_FECHAMENTO = "0 40 17 * * MON-FRI";

    private final ServicoColetaIntradiaria coletaIntradiaria;
    private final ServicoSnapshotFechamento snapshotFechamento;
    private final ServicoAtualizacaoIndicesMacro servicoAtualizacaoIndicesMacro;
    private final ServicoAtualizacaoIndicadoresIbge servicoAtualizacaoIndicadoresIbge;
    private final ServicoAtualizacaoProventos servicoAtualizacaoProventos;

    @Scheduled(cron = CRON_INTRADIARIO, zone = ZONA)
    public void atualizarCotacoes() {
        executar("cotacao intradiaria dos favoritos", coletaIntradiaria::executarCiclo);
    }

    @Scheduled(cron = CRON_FOTO_FECHAMENTO, zone = ZONA)
    public void fotografarFechamento() {
        executar("foto de fechamento dos favoritos", snapshotFechamento::fotografar);
    }

    @Scheduled(cron = CRON_MANHA, zone = ZONA)
    public void atualizarPerfilEmpresa() {
        executar("perfil de empresa", coletaIntradiaria::atualizarPerfis);
    }

    @Scheduled(cron = CRON_MANHA, zone = ZONA)
    public void atualizarIndicesMacro() {
        executar("indices macro", servicoAtualizacaoIndicesMacro::atualizarIndicesMacro);
    }

    /** O IBGE publica mensalmente; um disparo por dia util so repete o ultimo ponto ate sair outro. */
    @Scheduled(cron = CRON_MANHA, zone = ZONA)
    public void atualizarIndicadoresIbge() {
        executar("indicadores IBGE", servicoAtualizacaoIndicadoresIbge::atualizarIndicadores);
    }

    /**
     * A B3 so devolve os proventos dos ultimos 12 meses em cada consulta:
     * consultar todo dia util e o que constroi o historico com o tempo.
     */
    @Scheduled(cron = CRON_MANHA, zone = ZONA)
    public void atualizarProventos() {
        executar("proventos", servicoAtualizacaoProventos::atualizarProventos);
    }

    private static void executar(String rotina, Runnable acao) {
        try {
            acao.run();
        } catch (Exception e) {
            log.error("{}-Erro no ciclo de {}: {}", AGENDADOR, rotina, e.getMessage(), e);
        }
    }
}
