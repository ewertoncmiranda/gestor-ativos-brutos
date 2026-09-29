package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.SaudeDadosDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.SaudeDadosDTO.Ativo;
import br.com.miranda.gestor.ativos.brutos.external.dto.SaudeDadosDTO.Cobertura;
import br.com.miranda.gestor.ativos.brutos.external.dto.SaudeDadosDTO.Fonte;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSaudeDados;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSaudeDados.CoberturaAtivo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Monta a saude dos dados (CTR-12). Regra de leitura: cada fonte tem um prazo
 * que cobre fim de semana e feriado da sua rotina; passou dele, ATRASADA. O
 * estado geral so e OK quando nenhuma fonte critica esta atrasada e nenhum
 * ativo esta partido em dois codigos.
 */
@Service
@RequiredArgsConstructor
public class ServicoSaudeDados {

    static final int JANELA_PRECOS_DIAS = 90;
    static final BigDecimal LIMITE_DIVERGENCIA = new BigDecimal("0.01");
    static final int MESES_BALANCO_VELHO = 18;
    static final int HORAS_SEM_INSIGHT = 72;

    private record Definicao(String codigo, String nome, int prazoHoras, boolean critica, String comoResolver) {
    }

    // Prazos: rotina + folga de fim de semana/feriado.
    private static final List<Definicao> FONTES = List.of(
            // BRAPI so para os favoritos e so no pregao (infra V13): fim de semana
            // sem atualizacao e normal, e sem chave o sistema segue pelo COTAHIST.
            new Definicao("COTACAO", "Cotação intradiária dos favoritos (BRAPI)", 80, false,
                    "Verificar a chave e a cota da BRAPI no gestor-ativos-brutos."),
            new Definicao("VELAS", "Velas dos favoritos (derivadas da cotação BRAPI)", 100, false,
                    "Verificar o ciclo intradiário do gestor (:05 e :35, 10h-17h) e o orçamento BRAPI_ORCAMENTO."),
            new Definicao("INSIGHTS_BASE", "Insights diários da camada Base (COTAHIST)", 100, true,
                    "Rotina 'B3 - Rotina da manha' ou: gerar-insights python -m app.insights_diarios --recuperar"),
            new Definicao("CDI", "CDI (Banco Central)", 120, true,
                    "Reiniciar o gestor: completa o histórico do CDI na subida."),
            new Definicao("CVM_DFP", "Balanços anuais (CVM DFP)", 192, true,
                    "Rotina 'B3 - Rotina da manha' ou: etl-fundamentos-cvm --ano <ano>"),
            new Definicao("CVM_TTM", "Últimos 12 meses (CVM ITR)", 192, false,
                    "Rotina 'B3 - Rotina da manha' ou: etl-fundamentos-cvm --ttm --ano <ano>"),
            new Definicao("CVM_IPE", "Comunicados (CVM IPE)", 80, false,
                    "Rotina 'B3 - Rotina da manha' ou: etl-fundamentos-cvm --comunicados"),
            new Definicao("B3_COTAHIST", "Preço oficial (B3 COTAHIST)", 192, false,
                    "Rotina 'B3 - Rotina da manha' ou: etl-fundamentos-cvm --cotahist --ano <ano>"),
            new Definicao("DIARIO", "Diário de sinais", 80, true,
                    "Rotina 'B3 - Rotina da manha' ou: gerar-insights python -m app.validacao.diario registrar --recuperar"),
            new Definicao("BACKUP_MYSQL", "Backup do banco", 30, true,
                    "Rotina 'B3 - Backup MySQL' no Agendador do Windows."),
            new Definicao("BACKTEST", "Backtest walk-forward", 24 * 35, false,
                    "python -m app.validacao.backtest (gerar-insights).")
    );

    private final RepositorioSaudeDados repositorio;

    public SaudeDadosDTO montar() {
        LocalDateTime agora = LocalDateTime.now();
        Map<String, LocalDateTime> ultimas = repositorio.ultimasAtualizacoes();
        Map<String, String> erros = repositorio.ultimosErros();

        RepositorioSaudeDados.CoberturaInsightsBase insightsBase = repositorio.coberturaInsightsBase();
        List<Fonte> fontes = FONTES.stream()
                .map(d -> fonte(d, ultimas.get(d.codigo()), erros.get(d.codigo()), agora))
                .map(f -> "INSIGHTS_BASE".equals(f.codigo()) ? comAtrasoDePregao(f, insightsBase) : f)
                .toList();

        List<CoberturaAtivo> cobertura = repositorio.cobertura();
        List<Ativo> ativos = cobertura.stream().map(c -> ativo(c, agora)).toList();

        List<SaudeDadosDTO.Divergencia> divergencias = repositorio
                .comparacaoDePrecos(JANELA_PRECOS_DIAS, LIMITE_DIVERGENCIA).stream()
                .map(d -> new SaudeDadosDTO.Divergencia(d.simbolo(), d.data(), d.fechamentoBrapi(),
                        d.fechamentoB3(), d.diferenca()))
                .toList();
        List<String> aliases = repositorio.aliasesNoUniverso().stream()
                .map(a -> a.simbolo() + " → " + a.canonico())
                .toList();

        return new SaudeDadosDTO(
                agora,
                estadoGeral(fontes, aliases, divergencias),
                fontes,
                totais(cobertura),
                ativos,
                new SaudeDadosDTO.ChecagemPrecos(JANELA_PRECOS_DIAS, LIMITE_DIVERGENCIA,
                        repositorio.paresComparados(JANELA_PRECOS_DIAS), divergencias),
                aliases);
    }

    private static Fonte fonte(Definicao d, LocalDateTime ultima, String erro, LocalDateTime agora) {
        Long idade = ultima == null ? null : Math.max(0, Duration.between(ultima, agora).toHours());
        String estado;
        if (ultima == null) {
            estado = erro != null ? "ERRO" : "SEM_DADO";
        } else if (idade > d.prazoHoras()) {
            estado = "ATRASADA";
        } else {
            estado = "OK";
        }
        return new Fonte(d.codigo(), d.nome(), ultima, idade, d.prazoHoras(), estado,
                erro == null ? null : resumir(erro), d.comoResolver());
    }

    /**
     * Insight da camada Base atrasado de verdade: existe pregao no COTAHIST
     * sem insight correspondente (data_pregao_referencia), mesmo que o ultimo
     * insight ainda esteja dentro do prazo em horas.
     */
    static Fonte comAtrasoDePregao(Fonte f, RepositorioSaudeDados.CoberturaInsightsBase c) {
        if (c == null || c.ultimoPregaoB3() == null) {
            return f;
        }
        if (c.ultimoPregaoComInsight() != null && !c.ultimoPregaoComInsight().isBefore(c.ultimoPregaoB3())) {
            return f;
        }
        String motivo = "Pregão de " + c.ultimoPregaoB3() + " sem insight"
                + (c.ultimoPregaoComInsight() == null ? "" : " (último com insight: " + c.ultimoPregaoComInsight() + ")");
        return new Fonte(f.codigo(), f.nome(), f.atualizadoEm(), f.idadeHoras(), f.prazoHoras(),
                "ATRASADA", motivo, f.comoResolver());
    }

    static Ativo ativo(CoberturaAtivo c, LocalDateTime agora) {
        List<String> problemas = new ArrayList<>();
        LocalDate hoje = agora.toLocalDate();
        if (c.cnpj() == null) {
            problemas.add("Sem CNPJ: balanço e comunicados não se ligam ao preço");
        }
        if (c.ultimaVela() == null) {
            problemas.add("Sem vela diária");
        } else if (c.ultimaVela().isBefore(hoje.minusDays(5))) {
            problemas.add("Última vela em " + c.ultimaVela());
        }
        if (c.periodoAnual() == null) {
            problemas.add("Sem balanço anual");
        } else {
            if (c.periodoAnual().isBefore(hoje.minusMonths(MESES_BALANCO_VELHO))) {
                problemas.add("Balanço anual antigo (" + c.periodoAnual() + ")");
            }
            if (c.entregaAnual() == null) {
                problemas.add("Balanço sem data de entrega: fica fora do backtest");
            }
        }
        if (c.periodoTtm() == null) {
            problemas.add("Sem lucro dos últimos 12 meses (TTM)");
        }
        if (c.ultimoInsight() == null || c.ultimoInsight().isBefore(agora.minusHours(HORAS_SEM_INSIGHT))) {
            problemas.add("Sem análise nas últimas " + HORAS_SEM_INSIGHT + " h");
        }
        if (c.ultimoPregaoB3() == null) {
            problemas.add("Sem preço oficial da B3 (COTAHIST)");
        }
        return new Ativo(c.simbolo(), "COTACAO_E_HISTORICO".equals(c.tipoColeta()), c.cnpj(), c.ultimaVela(),
                c.cotacaoEm(), c.periodoAnual(), c.entregaAnual(), c.periodoTtm(), c.ultimoInsight(),
                c.ultimoPregaoB3(), problemas);
    }

    static Cobertura totais(List<CoberturaAtivo> ativos) {
        int comPreco = 0, comFundamentos = 0, ambos = 0, ttm = 0, entrega = 0, semCnpj = 0;
        for (CoberturaAtivo a : ativos) {
            boolean preco = a.ultimaVela() != null;
            boolean fundamentos = a.periodoAnual() != null;
            comPreco += preco ? 1 : 0;
            comFundamentos += fundamentos ? 1 : 0;
            ambos += preco && fundamentos ? 1 : 0;
            ttm += a.periodoTtm() != null ? 1 : 0;
            entrega += a.entregaAnual() != null ? 1 : 0;
            semCnpj += a.cnpj() == null ? 1 : 0;
        }
        return new Cobertura(ativos.size(), comPreco, comFundamentos, ambos, ttm, entrega, semCnpj);
    }

    private static String estadoGeral(List<Fonte> fontes, List<String> aliases,
                                      List<SaudeDadosDTO.Divergencia> divergencias) {
        boolean criticaRuim = fontes.stream().anyMatch(f -> !"OK".equals(f.estado())
                && FONTES.stream().anyMatch(d -> d.codigo().equals(f.codigo()) && d.critica()));
        if (criticaRuim || !aliases.isEmpty()) {
            return "PROBLEMA";
        }
        boolean algumaRuim = fontes.stream().anyMatch(f -> !"OK".equals(f.estado()));
        return algumaRuim || !divergencias.isEmpty() ? "ATENCAO" : "OK";
    }

    private static String resumir(String erro) {
        String linha = erro.lines().findFirst().orElse(erro);
        return linha.length() > 200 ? linha.substring(0, 200) + "…" : linha;
    }
}
