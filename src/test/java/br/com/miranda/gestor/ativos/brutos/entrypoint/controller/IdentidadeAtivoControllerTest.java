package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.IdentidadeAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoIdentidadeAtivo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentidadeAtivoControllerTest {
    @Mock private ServicoIdentidadeAtivo servico;
    @InjectMocks private IdentidadeAtivoController controller;

    @Test
    void retorna_identidade_ou_campos_nulos_para_ticker_sem_cadastro() {
        when(servico.buscar("DESCONHECIDO11")).thenReturn(IdentidadeAtivoDTO.vazio("DESCONHECIDO11"));
        IdentidadeAtivoDTO dto = controller.buscar("DESCONHECIDO11");
        assertEquals("DESCONHECIDO11", dto.simbolo());
        assertNull(dto.cnpj());
        assertNull(dto.freeFloat());
    }
}
