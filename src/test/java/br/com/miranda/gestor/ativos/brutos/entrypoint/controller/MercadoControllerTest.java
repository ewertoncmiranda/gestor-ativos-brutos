package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.exceptions.handler.TratadorGlobalExcecoes;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPanoramaMercado.LinhaAtivo;
import br.com.miranda.gestor.ativos.brutos.service.ServicoPanoramaMercado;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MERC-GES-1: contrato de GET /mercado/panorama (camelCase, fracoes, datas
 * ISO, 200 com indisponivel, 400 para janela fora do catalogo).
 */
class MercadoControllerTest {

    private static final LocalDate PREGAO = LocalDate.of(2026, 10, 19);

    private static MockMvc mvc(RepositorioPanoramaMercado repo) {
        return MockMvcBuilders.standaloneSetup(new MercadoController(new ServicoPanoramaMercado(repo)))
                .setControllerAdvice(new TratadorGlobalExcecoes())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    private static List<LocalDate> pregoes() {
        List<LocalDate> datas = new ArrayList<>();
        LocalDate d = PREGAO;
        while (datas.size() < 22) {
            if (d.getDayOfWeek().getValue() <= 5 && !d.equals(LocalDate.of(2026, 10, 12))) datas.add(d);
            d = d.minusDays(1);
        }
        return datas;
    }

    private static RepositorioPanoramaMercado repoComDados() {
        var repo = mock(RepositorioPanoramaMercado.class);
        when(repo.ultimoPregao()).thenReturn(Optional.of(PREGAO));
        when(repo.pregoesAte(any(), anyInt())).thenReturn(pregoes());
        when(repo.ativos(any(), any(), any(), any(), any())).thenReturn(List.of(
                new LinhaAtivo("ITUB4", "ITAU UNIBANCO", "Bancos", "FINANCEIRO", 1.2e9, 1.0, 0.0006,
                        103.0, 100.0, 6.5e9, 209039L, 99.0, false, false),
                new LinhaAtivo("BBDC4", "BRADESCO", "Bancos", "FINANCEIRO", 6.0e8, 1.0, 0.0008,
                        102.0, 100.0, 3.0e9, 150000L, 101.0, false, false),
                new LinhaAtivo("BBAS3", "BANCO DO BRASIL", "Bancos", "FINANCEIRO", 5.0e8, 0.98, 0.0007,
                        98.0, 100.0, 2.0e9, 120000L, 99.0, false, false)));
        return repo;
    }

    @Test
    void panoramaNoFormatoDoContrato() throws Exception {
        mvc(repoComDados()).perform(get("/mercado/panorama").param("janela", "5d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.janela").value("5d"))
                .andExpect(jsonPath("$.dataPregao").value("2026-10-19"))
                .andExpect(jsonPath("$.dataInicial").value("2026-10-13"))
                .andExpect(jsonPath("$.universo.liquidos").value(3))
                .andExpect(jsonPath("$.universo.presenca").value(3))
                .andExpect(jsonPath("$.setores[0].setor").value("Bancos"))
                .andExpect(jsonPath("$.setores[0].grupo").value("FINANCEIRO"))
                .andExpect(jsonPath("$.setores[0].empresas").value(3))
                .andExpect(jsonPath("$.setores[0].variacaoMediana", closeTo(0.02, 1e-9), Double.class))
                .andExpect(jsonPath("$.setores[0].selo").exists())
                .andExpect(jsonPath("$.setores[0].coesao").exists())
                .andExpect(jsonPath("$.setores[0].melhores[0].simbolo").value("ITUB4"))
                .andExpect(jsonPath("$.setores[0].melhores[0].nome").value("ITAU UNIBANCO"))
                .andExpect(jsonPath("$.setores[0].melhores[0].desvio").exists())
                .andExpect(jsonPath("$.setores[0].melhores[0].volumeFinanceiro").value(6.5e9))
                .andExpect(jsonPath("$.setores[0].piores[0].simbolo").value("BBAS3"))
                .andExpect(jsonPath("$.altas", hasSize(2)))
                .andExpect(jsonPath("$.altas[0].posicao").value(1))
                .andExpect(jsonPath("$.altas[0].simbolo").value("ITUB4"))
                .andExpect(jsonPath("$.altas[0].setor").value("Bancos"))
                .andExpect(jsonPath("$.quedas[0].simbolo").value("BBAS3"))
                .andExpect(jsonPath("$.quedas[0].variacao", closeTo(-0.02, 1e-9), Double.class))
                .andExpect(jsonPath("$.liquidez.maiores[0].volumeMedio63d").value(1.2e9))
                .andExpect(jsonPath("$.liquidez.maiores[0].presenca").value(1.0))
                .andExpect(jsonPath("$.liquidez.maiores[0].spreadEstimado").value(0.0006))
                .andExpect(jsonPath("$.liquidez.menores[0].simbolo").value("BBAS3"))
                .andExpect(jsonPath("$.negociacao.maiores[0].volumeFinanceiro").value(6.5e9))
                .andExpect(jsonPath("$.negociacao.maiores[0].negocios").value(209039))
                .andExpect(jsonPath("$.amplitude.subiram").value(2))
                .andExpect(jsonPath("$.amplitude.cairam").value(1))
                .andExpect(jsonPath("$.amplitude.estaveis").value(0))
                .andExpect(jsonPath("$.amplitude.acimaMedia21").value(2.0 / 3))
                .andExpect(jsonPath("$.volumeAnormal", hasSize(0)))
                .andExpect(jsonPath("$.macro").exists())
                .andExpect(jsonPath("$.eventos", hasSize(0)))
                .andExpect(jsonPath("$.indisponivel", contains("volumeAnormal", "macro", "eventos")));
    }

    @Test
    void janelaPadraoE5d() throws Exception {
        mvc(repoComDados()).perform(get("/mercado/panorama"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.janela").value("5d"));
    }

    @Test
    void janelaForaDoCatalogoE400() throws Exception {
        mvc(repoComDados()).perform(get("/mercado/panorama").param("janela", "3m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JANELA_INVALIDA"));
    }

    @Test
    void semCotacaoE200ComTudoIndisponivel() throws Exception {
        var repo = mock(RepositorioPanoramaMercado.class);
        when(repo.ultimoPregao()).thenReturn(Optional.empty());
        mvc(repo).perform(get("/mercado/panorama").param("janela", "ano"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.janela").value("ano"))
                .andExpect(jsonPath("$.indisponivel", hasSize(9)))
                .andExpect(jsonPath("$.setores", hasSize(0)))
                .andExpect(jsonPath("$.liquidez.maiores", hasSize(0)));
    }
}
