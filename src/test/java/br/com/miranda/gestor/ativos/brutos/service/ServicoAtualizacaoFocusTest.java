package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.ExpectativaFocusDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteFocusBcb;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServicoAtualizacaoFocusTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);

    private final ClienteFocusBcb cliente = mock(ClienteFocusBcb.class);
    private final RepositorioIndiceMacro repositorio = mock(RepositorioIndiceMacro.class);
    private final ServicoAtualizacaoFocus servico = new ServicoAtualizacaoFocus(cliente, repositorio);

    @Test
    void gravaUmaSeriePorIndicadorEAnoComADataDaPesquisa() {
        when(repositorio.findByCodigoSerieAndData(any(), any())).thenReturn(Optional.empty());
        when(cliente.consultarAnuais(eq("Selic"), eq(List.of(2026, 2027)), anyInt())).thenReturn(List.of(
                new ExpectativaFocusDTO("Selic", "2026-10-16", "2026", new BigDecimal("13.25")),
                new ExpectativaFocusDTO("Selic", "2026-10-16", "2027", new BigDecimal("12.00"))));
        when(cliente.consultarAnuais(eq("IPCA"), any(), anyInt())).thenReturn(List.of(
                new ExpectativaFocusDTO("IPCA", "2026-10-16", "2026", new BigDecimal("5.219"))));

        assertEquals(3, servico.atualizar(HOJE));

        var gravadas = ArgumentCaptor.forClass(IndiceMacroEntity.class);
        verify(repositorio, times(3)).save(gravadas.capture());
        var porCodigo = gravadas.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(IndiceMacroEntity::getCodigoSerie, e -> e));
        assertEquals(java.util.Set.of("FOCUS_SELIC_2026", "FOCUS_SELIC_2027", "FOCUS_IPCA_2026"), porCodigo.keySet());
        assertEquals(LocalDate.of(2026, 10, 16), porCodigo.get("FOCUS_SELIC_2026").getData());
        assertEquals(new BigDecimal("12.00"), porCodigo.get("FOCUS_SELIC_2027").getValor());
    }

    @Test
    void regravarAMesmaPesquisaAtualizaOPontoExistente() {
        var existente = new IndiceMacroEntity();
        existente.setId(7L);
        when(repositorio.findByCodigoSerieAndData("FOCUS_SELIC_2026", LocalDate.of(2026, 10, 16))).thenReturn(Optional.of(existente));
        when(cliente.consultarAnuais(eq("Selic"), any(), anyInt())).thenReturn(List.of(
                new ExpectativaFocusDTO("Selic", "2026-10-16", "2026", new BigDecimal("13.50"))));
        when(cliente.consultarAnuais(eq("IPCA"), any(), anyInt())).thenReturn(List.of());

        servico.atualizar(HOJE);

        verify(repositorio).save(existente);
        assertEquals(new BigDecimal("13.50"), existente.getValor());
    }

    @Test
    void falhaDeUmIndicadorNaoDerrubaOOutroELinhaIncompletaEIgnorada() {
        when(repositorio.findByCodigoSerieAndData(any(), any())).thenReturn(Optional.empty());
        when(cliente.consultarAnuais(eq("Selic"), any(), anyInt())).thenThrow(new IllegalStateException("BCB fora"));
        when(cliente.consultarAnuais(eq("IPCA"), any(), anyInt())).thenReturn(List.of(
                new ExpectativaFocusDTO("IPCA", "2026-10-16", "2027", new BigDecimal("4.32")),
                new ExpectativaFocusDTO("IPCA", "2026-10-16", "2027", null),
                new ExpectativaFocusDTO("IPCA", "data ruim", "2027", BigDecimal.ONE)));

        assertEquals(1, servico.atualizar(HOJE));
        verify(repositorio, times(1)).save(any());
    }
}
