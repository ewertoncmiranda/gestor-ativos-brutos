package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSaudeDados.CargaRecente;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** CARGA-GES-3: saude dos dados com as cargas do ETL (vw_carga_ultima). */
class ServicoSaudeDadosCargasTest {

    private static final LocalDateTime ANTIGO = LocalDateTime.of(2026, 10, 10, 8, 0);
    private static final LocalDateTime ONTEM = LocalDateTime.of(2026, 10, 17, 10, 0);
    private static final LocalDateTime HOJE = LocalDateTime.of(2026, 10, 18, 9, 0);

    @Test
    void sucessoDaCargaMaisRecenteSubstituiOHistoricoDeArquivos() {
        Map<String, LocalDateTime> ultimas = new HashMap<>(Map.of("B3_COTAHIST", ANTIGO));
        Map<String, String> erros = new HashMap<>();
        ServicoSaudeDados.comCargas(ultimas, erros, Map.of("COTAHIST", new CargaRecente(ONTEM, "SUCESSO", ONTEM, null)));
        assertEquals(ONTEM, ultimas.get("B3_COTAHIST"));
        assertTrue(erros.isEmpty());
    }

    @Test
    void historicoMaisRecenteQueACargaPrevalece() {
        Map<String, LocalDateTime> ultimas = new HashMap<>(Map.of("CVM_DFP", HOJE));
        ServicoSaudeDados.comCargas(ultimas, new HashMap<>(), Map.of("DFP", new CargaRecente(ONTEM, "SUCESSO", ONTEM, null)));
        assertEquals(HOJE, ultimas.get("CVM_DFP"));
    }

    @Test
    void falhaDepoisDoUltimoSucessoViraErroComOMotivo() {
        Map<String, LocalDateTime> ultimas = new HashMap<>();
        Map<String, String> erros = new HashMap<>();
        ServicoSaudeDados.comCargas(ultimas, erros,
                Map.of("TTM", new CargaRecente(ONTEM, "FALHA", HOJE, "timeout na CVM")));
        assertEquals(ONTEM, ultimas.get("CVM_TTM"), "o ultimo sucesso continua valendo para a idade");
        assertEquals("Última carga falhou: timeout na CVM", erros.get("CVM_TTM"));
    }

    @Test
    void falhaSemNenhumSucessoTambemAparece() {
        Map<String, String> erros = new HashMap<>();
        ServicoSaudeDados.comCargas(new HashMap<>(), erros, Map.of("IPE", new CargaRecente(null, "FALHA", HOJE, null)));
        assertEquals("Última carga falhou", erros.get("CVM_IPE"));
    }

    @Test
    void alertaOuSucessoNaoGeramErroEEtapaSemFonteEIgnorada() {
        Map<String, String> erros = new HashMap<>();
        Map<String, LocalDateTime> ultimas = new HashMap<>();
        ServicoSaudeDados.comCargas(ultimas, erros, Map.of(
                "TTM", new CargaRecente(ONTEM, "ALERTA", HOJE, null),
                "LIQUIDEZ", new CargaRecente(HOJE, "SUCESSO", HOJE, null)));
        assertTrue(erros.isEmpty());
        assertFalse(ultimas.containsKey("LIQUIDEZ"));
    }

    @Test
    void semV44NadaMuda() {
        Map<String, LocalDateTime> ultimas = new HashMap<>(Map.of("CVM_DFP", ANTIGO));
        Map<String, String> erros = new HashMap<>(Map.of("CVM_DFP", "erro antigo"));
        ServicoSaudeDados.comCargas(ultimas, erros, Map.of());
        assertEquals(Map.of("CVM_DFP", ANTIGO), ultimas);
        assertEquals(Map.of("CVM_DFP", "erro antigo"), erros);
    }

    @Test
    void instrucoesNaoMandamMaisRodarComandoDoEtl() {
        assertFalse(ServicoSaudeDados.RODAR_CARGA.contains("etl-fundamentos-cvm --"));
        assertTrue(ServicoSaudeDados.RODAR_CARGA.contains("Rodar carga"));
    }
}
