package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.HistoricoFundamentosDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoHistoricoFundamentos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoricoFundamentosControllerTest {
    @Mock private ServicoHistoricoFundamentos servico;
    @InjectMocks private HistoricoFundamentosController controller;

    @Test
    void usa_cinco_anos_por_padrao_e_retorna_historico_vazio() {
        when(servico.buscar("WEGE3", 5)).thenReturn(new HistoricoFundamentosDTO("WEGE3", List.of()));
        assertEquals(List.of(), controller.buscar("WEGE3", 5).pontos());
        verify(servico).buscar("WEGE3", 5);
    }

    @Test
    void rejeita_anos_fora_do_intervalo() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class, () -> controller.buscar("WEGE3", 11));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(servico);
    }
}
