package br.com.miranda.gestor.ativos.brutos.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RepositorioCargasTest {

    private final RepositorioCargas repositorio = new RepositorioCargas(mock(JdbcTemplate.class), new ObjectMapper());

    @Test
    void metricasViramArvoreEJsonInvalidoViraNull() {
        assertEquals(412, repositorio.arvore("{\"gravados\":412}").get("gravados").asInt());
        assertNull(repositorio.arvore("{quebrado"));
        assertNull(repositorio.arvore(null));
        assertNull(repositorio.arvore("  "));
    }

    @Test
    void alertasEErrosViramListaDeTextos() {
        assertEquals(List.of("timeout na CVM", "{\"ano\":2026}"), repositorio.textos("[\"timeout na CVM\", {\"ano\": 2026}]"));
        assertEquals(List.of(), repositorio.textos("{\"nao\":\"lista\"}"));
        assertEquals(List.of(), repositorio.textos(null));
        assertEquals(List.of(), repositorio.textos("[quebrado"));
    }
}
