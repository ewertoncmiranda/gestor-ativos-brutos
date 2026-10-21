package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.PainelMacroDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.PainelMacroDTO.AtivoAcima;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicoPainelMacroTest {

    private RepositorioIndiceMacro repositorio;
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        repositorio = mock(RepositorioIndiceMacro.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        when(repositorio.findByCodigoSerieOrderByDataDesc(anyString())).thenReturn(List.of());
    }

    private ServicoPainelMacro servico(LocalDate hoje) {
        Clock c = Clock.fixed(hoje.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return new ServicoPainelMacro(repositorio, jdbc, c);
    }

    private static IndiceMacroEntity p(String serie, LocalDate data, String valor) {
        var e = new IndiceMacroEntity();
        e.setCodigoSerie(serie);
        e.setData(data);
        e.setValor(new BigDecimal(valor));
        return e;
    }

    private void serie(String codigo, IndiceMacroEntity... desc) {
        when(repositorio.findByCodigoSerieOrderByDataDesc(codigo)).thenReturn(List.of(desc));
    }

    @SuppressWarnings("unchecked")
    private void ey(List<AtivoAcima> ativos) {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn((List) ativos);
    }

    @Test
    void juroRealUsaFocusDoAnoSeguinteDepoisDeJunho() {
        serie("SELIC", p("SELIC", LocalDate.of(2026, 10, 15), "13.75"));
        serie("FOCUS_IPCA_2027", p("FOCUS_IPCA_2027", LocalDate.of(2026, 10, 10), "4.00"));
        serie("FOCUS_IPCA_2026", p("FOCUS_IPCA_2026", LocalDate.of(2026, 10, 10), "5.00"));
        ey(List.of());

        PainelMacroDTO.JuroReal j = servico(LocalDate.of(2026, 10, 20)).montar().juroReal();

        assertThat(j.fonteIpca()).isEqualTo("FOCUS_IPCA_2027");
        assertThat(j.valor()).isEqualByComparingTo("9.38"); // 1.1375/1.04 - 1
        assertThat(j.leitura()).isEqualTo("Juro real de 9,38%: a renda fixa paga isso sem risco; "
                + "ações precisam render mais que 13,75% de earnings yield.");
    }

    @Test
    void juroRealUsaFocusDoAnoCorrenteAteJunho() {
        serie("SELIC", p("SELIC", LocalDate.of(2026, 6, 1), "13.75"));
        serie("FOCUS_IPCA_2026", p("FOCUS_IPCA_2026", LocalDate.of(2026, 5, 29), "5.00"));
        ey(List.of());
        assertThat(servico(LocalDate.of(2026, 6, 30)).montar().juroReal().fonteIpca()).isEqualTo("FOCUS_IPCA_2026");
    }

    @Test
    void juroRealCaiParaIpca12mSemFocus() {
        serie("SELIC", p("SELIC", LocalDate.of(2026, 10, 15), "10.00"));
        serie("IPCA_12M", p("IPCA_12M", LocalDate.of(2026, 9, 1), "4.50"));
        ey(List.of());
        PainelMacroDTO.JuroReal j = servico(LocalDate.of(2026, 10, 20)).montar().juroReal();
        assertThat(j.fonteIpca()).isEqualTo("IPCA_12M");
        assertThat(j.valor()).isEqualByComparingTo("5.26");
    }

    @Test
    void cicloAltaQuedaEstavel() {
        var s = servico(LocalDate.of(2026, 10, 20));
        LocalDate hoje = LocalDate.of(2026, 10, 15);
        assertThat(s.ciclo(List.of(p("SELIC", hoje, "14.25"), p("SELIC", hoje.minusDays(100), "13.75"))).ciclo())
                .isEqualTo("ALTA");
        assertThat(s.ciclo(List.of(p("SELIC", hoje, "13.25"), p("SELIC", hoje.minusDays(91), "13.75"))).ciclo())
                .isEqualTo("QUEDA");
        assertThat(s.ciclo(List.of(p("SELIC", hoje, "13.755"), p("SELIC", hoje.minusDays(90), "13.75"))).ciclo())
                .isEqualTo("ESTAVEL");
        assertThat(s.ciclo(List.of(p("SELIC", hoje, "13.75"))).motivo()).contains("90 dias");
    }

    @Test
    void variacoesEmPontosPercentuaisEPercentual() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        serie("SELIC", p("SELIC", d, "13.75"), p("SELIC", d.minusMonths(1), "13.25"),
                p("SELIC", d.minusMonths(3), "12.75"), p("SELIC", d.minusMonths(12), "10.50"));
        serie("DOLAR", p("DOLAR", d, "5.50"), p("DOLAR", d.minusMonths(1), "5.00"));
        ey(List.of());

        var series = servico(LocalDate.of(2026, 10, 20)).montar().series();
        var selic = series.stream().filter(x -> x.codigo().equals("SELIC")).findFirst().orElseThrow();
        var dolar = series.stream().filter(x -> x.codigo().equals("DOLAR")).findFirst().orElseThrow();

        assertThat(selic.variacao1m()).isEqualByComparingTo("0.50");
        assertThat(selic.variacao3m()).isEqualByComparingTo("1.00");
        assertThat(selic.variacao12m()).isEqualByComparingTo("3.25");
        assertThat(selic.unidadeVariacao()).isEqualTo("p.p.");
        assertThat(dolar.variacao1m()).isEqualByComparingTo("10.00");
        assertThat(dolar.variacao3m()).isNull();
        assertThat(dolar.unidadeVariacao()).isEqualTo("%");
    }

    @Test
    void igpm12mCompostoENuloSeFaltarMes() {
        List<IndiceMacroEntity> meses = new ArrayList<>();
        for (int i = 0; i < 12; i++) meses.add(p("IGPM", LocalDate.of(2026, 9, 1).minusMonths(i), "1.00"));
        assertThat(ServicoPainelMacro.acumulado12m(meses)).isEqualByComparingTo("12.68");
        meses.remove(5);
        assertThat(ServicoPainelMacro.acumulado12m(meses)).isNull();
    }

    @Test
    void serieAusenteViraNuloComMotivoSemErro() {
        ey(List.of());
        PainelMacroDTO painel = servico(LocalDate.of(2026, 10, 20)).montar();
        assertThat(painel.series()).allSatisfy(s -> {
            assertThat(s.valor()).isNull();
            assertThat(s.motivo()).contains("sem dados");
        });
        assertThat(painel.juroReal().valor()).isNull();
        assertThat(painel.juroReal().motivo()).isNotBlank();
        assertThat(painel.cicloSelic().ciclo()).isNull();
        assertThat(painel.barreira().earningsYieldMinimo()).isNull();
        assertThat(painel.barreira().motivo()).isNotBlank();
    }

    @Test
    void barreiraMedianaEAtivosAcimaOrdenados() {
        serie("SELIC", p("SELIC", LocalDate.of(2026, 10, 15), "13.75"));
        ey(List.of(new AtivoAcima("AAAA3", new BigDecimal("5")), new AtivoAcima("BBBB3", new BigDecimal("15")),
                new AtivoAcima("CCCC3", new BigDecimal("20")), new AtivoAcima("DDDD3", new BigDecimal("10"))));

        PainelMacroDTO.Barreira b = servico(LocalDate.of(2026, 10, 20)).montar().barreira();

        assertThat(b.earningsYieldMinimo()).isEqualByComparingTo("13.75");
        assertThat(b.earningsYieldMediano()).isEqualByComparingTo("12.50");
        assertThat(b.ativosAcimaDaBarreira()).extracting(AtivoAcima::simbolo).containsExactly("CCCC3", "BBBB3");
        assertThat(b.leitura()).contains("não supera");
    }

    @Test
    void falhaNaLeituraDoEarningsYieldNaoDerrubaOPainel() {
        serie("SELIC", p("SELIC", LocalDate.of(2026, 10, 15), "13.75"));
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new RuntimeException("sem tabela"));
        PainelMacroDTO.Barreira b = servico(LocalDate.of(2026, 10, 20)).montar().barreira();
        assertThat(b.earningsYieldMediano()).isNull();
        assertThat(b.motivo()).contains("Falha");
    }
}
