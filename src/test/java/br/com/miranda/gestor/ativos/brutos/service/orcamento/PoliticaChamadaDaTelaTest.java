package br.com.miranda.gestor.ativos.brutos.service.orcamento;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoOrcamentoBrapiTela;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sem Mockito: consumo falso e relogio ajustavel. */
class PoliticaChamadaDaTelaTest {

    private static final ZoneId BRASIL = ZoneId.of("America/Sao_Paulo");
    private static final String URL = "https://brapi.dev/api/quote/WEGE3";

    private static final class ConsumoFalso implements RegistroConsumoBrapi {
        long total;
        long tela;

        @Override
        public void registrar(LocalDate dia, Endpoint endpoint, Origem origem) {
            total++;
            if (origem == Origem.TELA) {
                tela++;
            }
        }

        @Override
        public long consumidoNoMes(YearMonth mes) {
            return total;
        }

        @Override
        public long consumidoPelaTelaNoMes(YearMonth mes) {
            return tela;
        }
    }

    private static final class RelogioAjustavel extends Clock {
        Instant agora = Instant.parse("2026-09-29T13:00:00Z");

        @Override
        public ZoneId getZone() {
            return BRASIL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final ConsumoFalso consumo = new ConsumoFalso();
    private final RelogioAjustavel relogio = new RelogioAjustavel();
    // RepositorioExecucaoEtl nao e usado por cabeChamadaDaTela.
    private final OrcamentoBrapi orcamento = new OrcamentoBrapi(consumo, null, 13_500, 1_500);
    private final PoliticaChamadaDaTela politica = new PoliticaChamadaDaTela(orcamento, relogio);

    @Test
    void mesma_consulta_em_menos_de_30_min_reusa_a_resposta() {
        politica.guardar(URL, "{\"results\":[]}");
        relogio.agora = relogio.agora.plusSeconds(29 * 60);

        assertEquals("{\"results\":[]}", politica.respostaRecente(URL).orElseThrow());
    }

    @Test
    void com_30_min_ou_mais_a_resposta_venceu() {
        politica.guardar(URL, "{\"results\":[]}");
        relogio.agora = relogio.agora.plusSeconds(30 * 60);

        assertTrue(politica.respostaRecente(URL).isEmpty());
    }

    @Test
    void consulta_diferente_nao_reusa() {
        politica.guardar(URL, "{}");

        assertFalse(politica.respostaRecente("https://brapi.dev/api/quote/PETR4").isPresent());
    }

    @Test
    void teto_da_tela_bloqueia_mesmo_com_saldo_no_total() {
        consumo.total = 5_000;
        consumo.tela = 1_500;

        assertThrows(ExcecaoOrcamentoBrapiTela.class, () -> politica.exigirOrcamento("quote/WEGE3"));
    }

    @Test
    void total_do_mes_estourado_bloqueia_a_tela() {
        consumo.total = 13_500;
        consumo.tela = 10;

        assertThrows(ExcecaoOrcamentoBrapiTela.class, () -> politica.exigirOrcamento("quote/WEGE3"));
    }

    @Test
    void com_saldo_nos_dois_tetos_libera() {
        consumo.total = 5_000;
        consumo.tela = 1_499;

        assertDoesNotThrow(() -> politica.exigirOrcamento("quote/WEGE3"));
    }

    @Test
    void origem_tela_grava_com_sufixo() {
        assertEquals("quote-tela", RegistroConsumoBrapi.Origem.TELA.chave(RegistroConsumoBrapi.Endpoint.QUOTE));
        assertEquals("historical", RegistroConsumoBrapi.Origem.AGENDADA.chave(RegistroConsumoBrapi.Endpoint.HISTORICAL));
    }
}
