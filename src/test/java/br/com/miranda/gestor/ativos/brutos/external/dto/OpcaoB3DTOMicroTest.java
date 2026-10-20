package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OpcaoB3DTOMicroTest {
    private OpcaoB3DTO opcao(String compra, String venda) {
        return new OpcaoB3DTO("PETRA32", "78", "CALL", null, null, null,
                null, null, null, BigDecimal.ONE, null, 100L, 2, null,
                compra == null ? null : new BigDecimal(compra),
                venda == null ? null : new BigDecimal(venda), "1");
    }

    @Test
    void jsonAditivoIncluiDerivadosSemMudarCamposAntigos() throws Exception {
        var o = opcao("9", "11");
        var json = new ObjectMapper().valueToTree(o);
        assertEquals("PETRA32", json.get("simbolo").asText());
        assertEquals("78", json.get("bdi").asText());
        assertEquals("CALL", json.get("tipoOpcao").asText());
        assertEquals(1, json.get("fechamento").asInt());
        assertEquals(9, json.get("melhorOfertaCompra").asInt());
        assertEquals(11, json.get("melhorOfertaVenda").asInt());
        assertEquals("1", json.get("indiceCorrecao").asText());
        assertEquals(10, json.get("precoJusto").asInt());
        assertEquals(20, json.get("spreadPercentual").asInt());
    }

    @Test
    void ausenciasZeroECruzamentoNaoFabricamPreco() {
        for (var o : new OpcaoB3DTO[]{opcao(null, "10"), opcao("10", null),
                opcao("0", "10"), opcao("11", "10"), opcao("-1", "1")}) {
            assertNull(o.precoJusto());
            assertNull(o.spreadPercentual());
        }
        assertEquals(BigDecimal.TEN, opcao("10", "10").precoJusto());
        assertEquals(0, opcao("10", "10").spreadPercentual().signum());
    }

    @Test
    void construtorLegadoMantemOfertasEDerivadosNulos() {
        var o = new OpcaoB3DTO("X", "78", "CALL", null, null, null,
                null, null, null, null, null, null, null, null);
        assertNull(o.melhorOfertaCompra());
        assertNull(o.indiceCorrecao());
        assertTrue(new ObjectMapper().valueToTree(o).get("precoJusto").isNull());
    }
}
