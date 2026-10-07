package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaHistoricoAcoesDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EventoSerieHistoricaTest {
    private RespostaHistoricoAcoesDTO serie(String instante, long took, String fechamento) {
        var candle = new RespostaHistoricoAcoesDTO.PrecoHistoricoAcaoDTO(1L, null, null, null, new BigDecimal(fechamento), null, null);
        var dados = new RespostaHistoricoAcoesDTO.SerieHistoricaAcaoDTO("1d", "3mo", List.of(candle));
        var resultado = new RespostaHistoricoAcoesDTO.ResultadoHistoricoAcaoDTO("PETR4", "PETR4", false, dados);
        return new RespostaHistoricoAcoesDTO(List.of(resultado), instante, took);
    }
    @Test void chaveIgnoraTransporteMasDistingueCorrecao() throws Exception {
        var mapper = new ObjectMapper();
        var a = mapper.readTree(EventoSerieHistorica.serializar(serie("a", 1, "30")));
        var b = mapper.readTree(EventoSerieHistorica.serializar(serie("b", 2, "30")));
        var c = mapper.readTree(EventoSerieHistorica.serializar(serie("a", 1, "31")));
        assertEquals("1.0", a.path("schemaVersion").asText());
        assertEquals(a.path("dedupKey"), b.path("dedupKey"));
        assertNotEquals(a.path("dedupKey"), c.path("dedupKey"));
    }
}
