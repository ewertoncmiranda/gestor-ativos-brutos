package br.com.miranda.gestor.ativos.brutos.entrypoint.schedule;

import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.AGENDADOR;

/**
 * Unico ponto que fala com a BRAPI pra manter o cache de mercado atualizado.
 * Substitui o antigo AgendadorAtivos, que chamava processarRobusto/processar
 * a cada tick E era chamado de novo, ao vivo, a cada requisicao HTTP do
 * frontend - o problema que este cache resolve.
 *
 * Cada tipo de dado tem seu proprio tick porque cada um envelhece num ritmo
 * diferente: cotacao em segundos, historico diario 1x/dia, perfil da empresa
 * 1x/semana. ServicoAtualizacaoCache decide, dentro de cada chamada, quem
 * realmente esta devido - o tick so define o teto de frequencia.
 */
@Slf4j
@Component
@EnableScheduling
@AllArgsConstructor
public class AgendadorCacheAtivos {

    private final ServicoAtualizacaoCache servicoAtualizacaoCache;

    @Scheduled(fixedDelay = 5000)
    public void atualizarCotacoes() {
        try {
            servicoAtualizacaoCache.atualizarCotacoes();
        } catch (Exception e) {
            log.error("{}-Erro no ciclo de atualizacao de cotacoes: {}", AGENDADOR, e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void atualizarHistoricoDiario() {
        try {
            servicoAtualizacaoCache.atualizarHistoricoDiario();
        } catch (Exception e) {
            log.error("{}-Erro no ciclo de atualizacao de historico diario: {}", AGENDADOR, e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void atualizarPerfilEmpresa() {
        try {
            servicoAtualizacaoCache.atualizarPerfilEmpresa();
        } catch (Exception e) {
            log.error("{}-Erro no ciclo de atualizacao de perfil de empresa: {}", AGENDADOR, e.getMessage(), e);
        }
    }
}
