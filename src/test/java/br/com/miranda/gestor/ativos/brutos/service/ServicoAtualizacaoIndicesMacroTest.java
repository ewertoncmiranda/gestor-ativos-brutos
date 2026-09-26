package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.PontoSerieSgsDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBancoCentral;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicoAtualizacaoIndicesMacroTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 26);

    @Mock
    private ClienteBancoCentral cliente;

    @Mock
    private RepositorioIndiceMacro repositorio;

    @InjectMocks
    private ServicoAtualizacaoIndicesMacro servico;

    private static IndiceMacroEntity ponto(LocalDate data) {
        IndiceMacroEntity entidade = new IndiceMacroEntity();
        entidade.setData(data);
        return entidade;
    }

    private void comSerieEntre(LocalDate maisAntigo, LocalDate maisRecente) {
        when(repositorio.findFirstByCodigoSerieOrderByDataAsc("CDI")).thenReturn(Optional.ofNullable(maisAntigo).map(d -> ponto(d)));
        when(repositorio.findFirstByCodigoSerieOrderByDataDesc("CDI")).thenReturn(Optional.ofNullable(maisRecente).map(d -> ponto(d)));
    }

    @Test
    void serie_vazia_busca_do_inicio_ate_hoje_fatiando_ano_a_ano() {
        comSerieEntre(null, null);
        when(cliente.consultarSeriePeriodo(anyInt(), any(), any())).thenReturn(List.of());

        servico.completarHistorico("CDI", LocalDate.of(2024, 1, 1), HOJE);

        verify(cliente).consultarSeriePeriodo(12, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        verify(cliente).consultarSeriePeriodo(12, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        verify(cliente).consultarSeriePeriodo(12, LocalDate.of(2026, 1, 1), HOJE);
        verifyNoMoreInteractions(cliente);
    }

    @Test
    void completa_so_o_que_falta_antes_do_ponto_mais_antigo() {
        comSerieEntre(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 24));
        when(cliente.consultarSeriePeriodo(anyInt(), any(), any())).thenReturn(List.of());

        servico.completarHistorico("CDI", LocalDate.of(2026, 1, 1), HOJE);

        verify(cliente).consultarSeriePeriodo(12, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 10));
        verifyNoMoreInteractions(cliente);
    }

    @Test
    void serie_completa_nao_chama_o_banco_central() {
        comSerieEntre(LocalDate.of(2016, 1, 4), LocalDate.of(2026, 9, 24));

        int gravados = servico.completarHistorico("CDI", LocalDate.of(2016, 1, 1), HOJE);

        assertEquals(0, gravados);
        verifyNoInteractions(cliente);
    }

    @Test
    void buraco_recente_maior_que_o_ciclo_horario_e_preenchido() {
        comSerieEntre(LocalDate.of(2016, 1, 4), LocalDate.of(2026, 8, 1));
        when(cliente.consultarSeriePeriodo(anyInt(), any(), any())).thenReturn(List.of());

        servico.completarHistorico("CDI", LocalDate.of(2016, 1, 1), HOJE);

        verify(cliente).consultarSeriePeriodo(12, LocalDate.of(2026, 8, 2), HOJE);
    }

    @Test
    void grava_os_pontos_recebidos_e_conta_so_os_validos() {
        comSerieEntre(null, null);
        when(cliente.consultarSeriePeriodo(anyInt(), any(), any())).thenReturn(List.of(
                new PontoSerieSgsDTO("04/01/2016", new BigDecimal("0.052496")),
                new PontoSerieSgsDTO(null, new BigDecimal("0.05"))));
        when(repositorio.findByCodigoSerieAndData(anyString(), any())).thenReturn(Optional.empty());

        int gravados = servico.completarHistorico("CDI", LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 31));

        assertEquals(1, gravados);
        verify(repositorio, times(1)).save(any());
    }

    @Test
    void serie_desconhecida_e_erro_de_programacao() {
        assertThrows(IllegalArgumentException.class,
                () -> servico.completarHistorico("DOLAR", LocalDate.of(2016, 1, 1), HOJE));
    }
}
