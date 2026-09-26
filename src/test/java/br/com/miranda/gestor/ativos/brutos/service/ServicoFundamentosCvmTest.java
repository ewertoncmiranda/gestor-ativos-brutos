package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.HistoricoAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.IndicadorFundamentalistaEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.FundamentosCvmDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioHistoricoAcao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndicadorFundamentalista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicoFundamentosCvmTest {

    @Mock
    private RepositorioIndicadorFundamentalista repositorioIndicador;

    @Mock
    private RepositorioHistoricoAcao repositorioHistorico;

    @InjectMocks
    private ServicoFundamentosCvm servico;

    private IndicadorFundamentalistaEntity fundamentoWege3;

    @BeforeEach
    void preparar() {
        fundamentoWege3 = new IndicadorFundamentalistaEntity();
        fundamentoWege3.setSimbolo("WEGE3");
        fundamentoWege3.setCnpj("84.429.695/0001-11");
        fundamentoWege3.setPeriodo(LocalDate.of(2025, 12, 31));
        fundamentoWege3.setTipoPeriodo("ANUAL");
        fundamentoWege3.setLpa(new BigDecimal("1.519700"));
        fundamentoWege3.setVpa(new BigDecimal("4.151200"));
        fundamentoWege3.setRoe(new BigDecimal("36.6100"));
        fundamentoWege3.setFonte("CVM");
        fundamentoWege3.setTipoDoc("DFP");
        fundamentoWege3.setPlanoContas("GERAL");
        fundamentoWege3.setVersaoCvm(1);
    }

    private void comFundamento(IndicadorFundamentalistaEntity entidade) {
        when(repositorioIndicador.findFirstBySimboloAndTipoPeriodoOrderByPeriodoDesc(
                anyString(), anyString())).thenReturn(Optional.ofNullable(entidade));
        lenient().when(repositorioIndicador.findFirstBySimboloOrderByPeriodoDesc(anyString()))
                .thenReturn(Optional.empty());
    }

    private void comCotacao(BigDecimal preco) {
        HistoricoAcaoEntity cotacao = new HistoricoAcaoEntity();
        cotacao.setSimbolo("WEGE3");
        cotacao.setPrecoFechamento(preco);
        cotacao.setTimestamp(LocalDateTime.of(2026, 9, 25, 18, 0));
        when(repositorioHistorico.findFirstBySimboloOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.of(cotacao));
    }

    @Test
    void deriva_multiplos_cruzando_fundamento_com_a_cotacao_mais_recente() {
        comFundamento(fundamentoWege3);
        comCotacao(new BigDecimal("50.00"));

        FundamentosCvmDTO dto = servico.buscarPorSimbolo("WEGE3");

        assertEquals(new BigDecimal("32.9012"), dto.getPrecoLucro());
        assertEquals(new BigDecimal("12.0447"), dto.getPrecoValorPatrimonial());
        assertEquals(new BigDecimal("50.00"), dto.getPrecoReferencia());
        assertNotNull(dto.getPrecoEm());
    }

    @Test
    void sem_cotacao_o_fundamento_ainda_e_devolvido_com_multiplos_nulos() {
        comFundamento(fundamentoWege3);
        when(repositorioHistorico.findFirstBySimboloOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.empty());

        FundamentosCvmDTO dto = servico.buscarPorSimbolo("WEGE3");

        assertEquals(new BigDecimal("36.6100"), dto.getRoe());
        assertNull(dto.getPrecoLucro());
        assertNull(dto.getPrecoReferencia());
    }

    @Test
    void simbolo_sem_carga_devolve_dto_vazio_em_vez_de_erro() {
        comFundamento(null);

        FundamentosCvmDTO dto = servico.buscarPorSimbolo("NUNCA11");

        assertEquals("NUNCA11", dto.getSimbolo());
        assertNull(dto.getRoe());
        assertNull(dto.getPeriodo());
    }

    @Test
    void simbolo_e_normalizado_para_maiusculo_sem_espaco() {
        comFundamento(fundamentoWege3);
        when(repositorioHistorico.findFirstBySimboloOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.empty());

        FundamentosCvmDTO dto = servico.buscarPorSimbolo("  wege3 ");

        assertEquals("WEGE3", dto.getSimbolo());
    }

    @Test
    void preenche_a_defasagem_para_o_leitor_saber_a_idade_do_balanco() {
        comFundamento(fundamentoWege3);
        when(repositorioHistorico.findFirstBySimboloOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.empty());

        FundamentosCvmDTO dto = servico.buscarPorSimbolo("WEGE3");

        assertNotNull(dto.getDefasagemDias());
    }

    @Test
    void falha_de_leitura_do_banco_nao_propaga_excecao_para_o_controller() {
        when(repositorioIndicador.findFirstBySimboloAndTipoPeriodoOrderByPeriodoDesc(
                anyString(), anyString())).thenThrow(new RuntimeException("banco fora"));

        FundamentosCvmDTO dto = servico.buscarPorSimbolo("WEGE3");

        assertEquals("WEGE3", dto.getSimbolo());
        assertNull(dto.getRoe());
    }

    @Test
    void lpa_negativo_nao_gera_preco_lucro() {
        fundamentoWege3.setLpa(new BigDecimal("-1.50"));
        comFundamento(fundamentoWege3);
        comCotacao(new BigDecimal("50.00"));

        FundamentosCvmDTO dto = servico.buscarPorSimbolo("WEGE3");

        assertNull(dto.getPrecoLucro());
        assertNotNull(dto.getPrecoValorPatrimonial());
    }
}
