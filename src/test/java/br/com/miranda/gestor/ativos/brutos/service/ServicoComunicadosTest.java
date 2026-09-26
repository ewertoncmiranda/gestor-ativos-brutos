package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.ComunicadoCvmEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.LinhaDoTempoComunicadosDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.NewsletterComunicadosDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.NewsletterComunicadosDTO.EmpresaNaEdicao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioComunicadoCvm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicoComunicadosTest {

    private static final String CNPJ_PETROBRAS = "33.000.167/0001-01";
    private static final String CNPJ_WEG = "84.429.695/0001-11";

    @Mock
    private RepositorioComunicadoCvm repositorio;

    @InjectMocks
    private ServicoComunicados servico;

    private static ComunicadoCvmEntity documento(
            String protocolo, String cnpj, String categoria, LocalDate entrega) {
        ComunicadoCvmEntity entidade = new ComunicadoCvmEntity();
        entidade.setProtocoloCvm(protocolo);
        entidade.setCnpj(cnpj);
        entidade.setCategoria(categoria);
        entidade.setCategoriaOriginal(categoria);
        entidade.setDataEntrega(entrega);
        entidade.setVersao(1);
        entidade.setLinkDownload("https://www.rad.cvm.gov.br/?numProtocolo=" + protocolo);
        return entidade;
    }

    // --- linha do tempo ---------------------------------------------------

    @Test
    void linha_do_tempo_usa_categorias_padrao_sem_assembleia_e_periodo_aberto() {
        when(repositorio.buscarPorSimbolo(anyString(), anyCollection(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        LinhaDoTempoComunicadosDTO dto = servico.buscarPorSimbolo(" petr4 ", null, null, null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> categorias = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).buscarPorSimbolo(eq("PETR4"), categorias.capture(),
                eq(LocalDate.of(2000, 1, 1)), eq(LocalDate.of(9999, 12, 31)), pagina.capture());
        assertFalse(categorias.getValue().contains("ASSEMBLEIA"));
        assertTrue(categorias.getValue().contains("FATO_RELEVANTE"));
        assertEquals(PageRequest.of(0, 20), pagina.getValue());
        assertEquals("PETR4", dto.getSimbolo());
        assertEquals(ServicoComunicados.FONTE, dto.getFonte());
    }

    @Test
    void linha_do_tempo_aceita_categorias_separadas_por_virgula_e_em_minusculo() {
        when(repositorio.buscarPorSimbolo(anyString(), anyCollection(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        LinhaDoTempoComunicadosDTO dto = servico.buscarPorSimbolo(
                "PETR4", List.of("proventos,fato_relevante"), null, null, null, null);

        // Ordenadas por relevância, não pela ordem do pedido
        assertEquals(List.of("FATO_RELEVANTE", "PROVENTOS"), dto.getCategorias());
    }

    @Test
    void linha_do_tempo_mapeia_documento_com_rotulo_e_link() {
        ComunicadoCvmEntity fato = documento("1569745", CNPJ_PETROBRAS, "FATO_RELEVANTE",
                LocalDate.of(2026, 9, 19));
        fato.setAssunto("Petrobras informa sobre adesão à nova subvenção econômica");
        when(repositorio.buscarPorSimbolo(anyString(), anyCollection(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(fato), PageRequest.of(0, 20), 1));
        when(repositorio.dataEntregaMaisRecente()).thenReturn(Optional.of(LocalDate.of(2026, 9, 19)));

        LinhaDoTempoComunicadosDTO dto = servico.buscarPorSimbolo("PETR4", null, null, null, null, null);

        assertEquals(1, dto.getTotal());
        assertEquals(LocalDate.of(2026, 9, 19), dto.getDadosAte());
        assertEquals("Fato relevante", dto.getComunicados().get(0).getCategoriaRotulo());
        assertTrue(dto.getComunicados().get(0).getLink().contains("1569745"));
    }

    @Test
    void categoria_desconhecida_e_erro_do_cliente() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> servico.buscarPorSimbolo("PETR4", List.of("BOATO"), null, null, null, null));
        assertTrue(erro.getMessage().contains("BOATO"));
        verifyNoInteractions(repositorio);
    }

    @Test
    void periodo_invertido_e_tamanho_fora_do_limite_sao_erro_do_cliente() {
        assertThrows(IllegalArgumentException.class, () -> servico.buscarPorSimbolo(
                "PETR4", null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> servico.buscarPorSimbolo("PETR4", null, null, null, 0, 101));
        assertThrows(IllegalArgumentException.class,
                () -> servico.buscarPorSimbolo("PETR4", null, null, null, -1, 10));
    }

    // --- newsletter ---------------------------------------------------------

    @Test
    void sem_semana_usa_a_semana_do_documento_mais_recente_e_nao_a_corrente() {
        when(repositorio.dataEntregaMaisRecente()).thenReturn(Optional.of(LocalDate.of(2026, 9, 19)));
        when(repositorio.tickersMonitorados()).thenReturn(List.<Object[]>of());

        NewsletterComunicadosDTO dto = servico.newsletter(null, null, null, null);

        assertEquals("2026-W38", dto.getSemana());
        assertEquals(LocalDate.of(2026, 9, 14), dto.getDesde());
        assertEquals(LocalDate.of(2026, 9, 20), dto.getAte());
        assertEquals("2026-W37", dto.getSemanaAnterior());
        assertEquals("2026-W39", dto.getSemanaSeguinte());
    }

    @Test
    void semana_iso_explicita_define_o_periodo() {
        when(repositorio.tickersMonitorados()).thenReturn(List.<Object[]>of());

        NewsletterComunicadosDTO dto = servico.newsletter("2026-w01", null, null, null);

        // Semana ISO 1 de 2026 começa na segunda 29/12/2025
        assertEquals(LocalDate.of(2025, 12, 29), dto.getDesde());
        assertEquals("2026-W01", dto.getSemana());
    }

    @Test
    void semana_malformada_ou_periodo_pela_metade_e_erro_do_cliente() {
        assertThrows(IllegalArgumentException.class, () -> servico.newsletter("semana-39", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> servico.newsletter(null, LocalDate.of(2026, 9, 1), null, null));
    }

    @Test
    void newsletter_consulta_so_os_cnpjs_da_carteira_no_periodo() {
        when(repositorio.tickersMonitorados()).thenReturn(List.of(
                new Object[]{"PETR4", CNPJ_PETROBRAS}, new Object[]{"WEGE3", CNPJ_WEG}));

        servico.newsletter(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);

        verify(repositorio).findByCnpjInAndCategoriaInAndDataEntregaBetween(
                argThat(cnpjs -> cnpjs.containsAll(List.of(CNPJ_PETROBRAS, CNPJ_WEG))),
                anyCollection(), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
    }

    // --- agrupamento --------------------------------------------------------

    @Test
    void empresa_com_fato_relevante_vem_antes_de_empresa_com_mais_avisos() {
        LocalDate dia = LocalDate.of(2026, 9, 15);
        List<ComunicadoCvmEntity> documentos = List.of(
                documento("1", CNPJ_WEG, "AVISO_ACIONISTAS", dia),
                documento("2", CNPJ_WEG, "AVISO_ACIONISTAS", dia),
                documento("3", CNPJ_WEG, "AVISO_ACIONISTAS", dia),
                documento("4", CNPJ_PETROBRAS, "COMUNICADO_MERCADO", dia),
                documento("5", CNPJ_PETROBRAS, "FATO_RELEVANTE", dia.minusDays(3)));

        List<EmpresaNaEdicao> empresas = ServicoComunicados.agruparPorTicker(documentos,
                Map.of(CNPJ_PETROBRAS, List.of("PETR4"), CNPJ_WEG, List.of("WEGE3")));

        assertEquals(List.of("PETR4", "WEGE3"), empresas.stream().map(EmpresaNaEdicao::getSimbolo).toList());
        // Dentro da empresa: relevância antes de data
        assertEquals("FATO_RELEVANTE", empresas.get(0).getComunicados().get(0).getCategoria());
        assertEquals(Map.of("FATO_RELEVANTE", 1L, "COMUNICADO_MERCADO", 1L), empresas.get(0).getPorCategoria());
        assertEquals(3, empresas.get(1).getTotal());
    }

    @Test
    void companhia_com_dois_tickers_monitorados_aparece_nos_dois() {
        List<EmpresaNaEdicao> empresas = ServicoComunicados.agruparPorTicker(
                List.of(documento("1", CNPJ_PETROBRAS, "FATO_RELEVANTE", LocalDate.of(2026, 9, 15))),
                Map.of(CNPJ_PETROBRAS, List.of("PETR3", "PETR4")));

        assertEquals(List.of("PETR3", "PETR4"), empresas.stream().map(EmpresaNaEdicao::getSimbolo).toList());
    }

    @Test
    void semana_iso_formata_com_dois_digitos_e_ano_da_semana() {
        assertEquals("2026-W01", ServicoComunicados.formatarSemana(LocalDate.of(2025, 12, 29)));
        assertEquals(LocalDate.of(2026, 9, 21), ServicoComunicados.segundaDaSemana("2026-W39"));
    }
}
