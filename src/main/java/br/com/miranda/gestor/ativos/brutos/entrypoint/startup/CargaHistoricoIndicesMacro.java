package br.com.miranda.gestor.ativos.brutos.entrypoint.startup;

import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoIndicesMacro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

/**
 * Unica responsabilidade: na subida, garantir o historico diario do CDI desde
 * {@code indices.macro.historico.inicio}. O backtest e o diario de sinais do
 * gerar-insights comparam cada retorno com o CDI acumulado no mesmo periodo;
 * com os 10 pontos do ciclo horario essa comparacao nao existe.
 *
 * <p>Roda numa thread virtual para nao segurar a subida (a primeira carga sao
 * ~10 chamadas ao BCB e ~2.700 pontos). Nas subidas seguintes a serie ja esta
 * completa e o metodo nao faz chamada nenhuma.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CargaHistoricoIndicesMacro implements ApplicationRunner {

    private final ServicoAtualizacaoIndicesMacro servicoIndicesMacro;

    @Value("${indices.macro.historico.inicio:2016-01-01}")
    private String inicio;

    @Override
    public void run(ApplicationArguments args) {
        LocalDate desde = LocalDate.parse(inicio);
        Thread.ofVirtual().name("carga-historico-cdi").start(() -> {
            try {
                servicoIndicesMacro.completarHistorico("CDI", desde, LocalDate.now());
            } catch (Exception e) {
                // Falha aqui so adia a validacao; o painel segue funcionando.
                log.error("{} - Falha ao completar historico do CDI: {}", SERVICO, e.getMessage(), e);
            }
        });
    }
}
