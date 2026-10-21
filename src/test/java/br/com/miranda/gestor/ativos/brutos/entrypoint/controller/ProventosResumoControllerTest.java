package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.service.ServicoProventosResumo;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProventosResumoControllerTest {
    @Test void datasInvalidasOuPeriodoInvertidoDevolvem400SemConsultarRepositorio() throws Exception {
        var servico = mock(ServicoProventosResumo.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ProventosResumoController(servico)).build();
        mvc.perform(get("/ativos/ITUB4/proventos-resumo").param("de", "invalida")).andExpect(status().isBadRequest());
        mvc.perform(get("/ativos/ITUB4/proventos-resumo").param("de", "2026-05-05").param("ate", "2026-05-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/ativos/ITUB4/proventos-resumo").param("de", "2015-01-01"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servico);
    }
    @Test void endpointRepassaJanelaSelecionada() throws Exception {
        var servico = mock(ServicoProventosResumo.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ProventosResumoController(servico)).build();
        mvc.perform(get("/ativos/ITUB4/proventos-resumo").param("de", "2026-05-01").param("ate", "2026-05-05"))
                .andExpect(status().isOk());
        verify(servico).resumo("ITUB4", java.time.LocalDate.parse("2026-05-01"), java.time.LocalDate.parse("2026-05-05"));
    }
}
