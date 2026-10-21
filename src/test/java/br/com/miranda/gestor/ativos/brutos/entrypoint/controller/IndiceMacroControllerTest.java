package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MACRO-GES-4: recorte por periodo sem mudar a chamada antiga. */
class IndiceMacroControllerTest {

    private final RepositorioIndiceMacro repositorio = mock(RepositorioIndiceMacro.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new IndiceMacroController(repositorio, mock(br.com.miranda.gestor.ativos.brutos.service.ServicoPainelMacro.class))).build();

    private static IndiceMacroEntity ponto(LocalDate data) {
        var entidade = new IndiceMacroEntity();
        entidade.setCodigoSerie("SELIC");
        entidade.setData(data);
        entidade.setValor(new BigDecimal("13.75"));
        entidade.setAtualizadoEm(LocalDateTime.now());
        return entidade;
    }

    @Test
    void semDesdeDevolveASerieInteiraComoAntes() throws Exception {
        when(repositorio.findByCodigoSerieOrderByDataDesc("SELIC")).thenReturn(List.of(ponto(LocalDate.of(2026, 10, 1))));
        mvc.perform(get("/indices-macro/selic")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        verify(repositorio, never()).findByCodigoSerieAndDataGreaterThanEqualOrderByDataDesc(any(), any());
    }

    @Test
    void comDesdeFiltraAPartirDaData() throws Exception {
        when(repositorio.findByCodigoSerieAndDataGreaterThanEqualOrderByDataDesc("SELIC", LocalDate.of(2025, 10, 20)))
                .thenReturn(List.of(ponto(LocalDate.of(2026, 10, 1)), ponto(LocalDate.of(2025, 10, 21))));
        mvc.perform(get("/indices-macro/SELIC").param("desde", "2025-10-20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void desdeInvalidoResponde400() throws Exception {
        mvc.perform(get("/indices-macro/SELIC").param("desde", "20/10/2025")).andExpect(status().isBadRequest());
        verifyNoInteractions(repositorio);
    }
}
