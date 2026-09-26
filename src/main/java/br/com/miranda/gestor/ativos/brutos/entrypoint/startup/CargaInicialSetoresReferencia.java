package br.com.miranda.gestor.ativos.brutos.entrypoint.startup;

import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivoMonitorado;
import br.com.miranda.gestor.ativos.brutos.tools.SetoresReferencia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

/**
 * Unica responsabilidade: na subida da aplicacao, garantir que todo ticker
 * do universo de referencia por setor ({@link SetoresReferencia}) esta
 * registrado em ativo_monitorado - so uma vez por ticker (registrarReferenciaSetor
 * e idempotente), depois disso o AgendadorCacheAtivos ja cuida do resto.
 *
 * ApplicationRunner (nao @PostConstruct): roda depois que todo o contexto -
 * incluindo o agendador - ja esta de pe, evitando corrida com o scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CargaInicialSetoresReferencia implements ApplicationRunner {

    private final ServicoAtivoMonitorado servicoAtivoMonitorado;

    @Override
    public void run(ApplicationArguments args) {
        var tickers = SetoresReferencia.TICKERS_POR_SETOR.values().stream()
                .flatMap(List::stream)
                // A lista e fotografia manual: se um ticker mudar de codigo
                // antes de ela ser revista, a identidade corrige na subida.
                .map(servicoAtivoMonitorado::canonizar)
                .distinct()
                .toList();

        log.info("{} - Carga inicial do universo de referencia por setor: {} ticker(s)", SERVICO, tickers.size());
        tickers.forEach(servicoAtivoMonitorado::registrarReferenciaSetor);
        servicoAtivoMonitorado.desativarReferenciasObsoletas(Set.copyOf(tickers));
    }
}
