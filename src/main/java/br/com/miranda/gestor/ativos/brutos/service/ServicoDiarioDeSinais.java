package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.DiarioDeSinaisDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.DiarioDeSinaisDTO.LinhaPlacar;
import br.com.miranda.gestor.ativos.brutos.external.dto.DiarioDeSinaisDTO.ResultadoDoHorizonte;
import br.com.miranda.gestor.ativos.brutos.external.dto.DiarioDeSinaisDTO.SinalNaLinhaDoTempo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioDiarioDeSinais;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Monta a visao do diario de sinais (paper trading) gravado pelo
 * gerar-insights. So le; contrato {@code infra#CTR-11}.
 */
@Service
@RequiredArgsConstructor
public class ServicoDiarioDeSinais {

    public static final List<Integer> HORIZONTES = List.of(21, 63, 126);
    public static final int AMOSTRA_MINIMA = 30;
    static final int LIMITE_PADRAO = 120;
    static final int LIMITE_MAXIMO = 1000;
    public static final String AVISO =
            "Registro de sinais para medir o acerto das regras, não recomendação de investimento. "
                    + "Retornos com custo de 0,10% ida e volta, sem proventos nem impostos.";

    private static final Set<String> COMPRA = Set.of("COMPRA_FORTE", "COMPRA_MODERADA", "COMPRA_TECNICA");
    private static final Set<String> VENDA = Set.of("VENDA_VALUATION", "VENDA_TECNICA");

    private final RepositorioDiarioDeSinais repositorio;

    public DiarioDeSinaisDTO montar(String simbolo, Integer limite) {
        String filtro = normalizar(simbolo);
        int quantidade = limite != null ? limite : LIMITE_PADRAO;
        if (quantidade < 1 || quantidade > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("limite deve estar entre 1 e " + LIMITE_MAXIMO);
        }

        RepositorioDiarioDeSinais.Totais totais = repositorio.totais(filtro);
        List<RepositorioDiarioDeSinais.Sinal> sinais = repositorio.sinaisRecentes(filtro, quantidade);
        Map<Long, Map<Integer, ResultadoDoHorizonte>> resultados = agruparResultados(
                repositorio.resultadosDe(sinais.stream().map(RepositorioDiarioDeSinais.Sinal::id).toList()));
        Map<Integer, BigDecimal> taxaBase = repositorio.taxaBaseDeAlta(filtro);

        return new DiarioDeSinaisDTO(
                filtro,
                totais.sinais(),
                totais.ativos(),
                totais.primeiroPregao(),
                totais.ultimoPregao(),
                totais.resultados(),
                Math.max(0, totais.sinais() * HORIZONTES.size() - totais.resultados()),
                HORIZONTES,
                AMOSTRA_MINIMA,
                repositorio.simbolosComSinal(),
                montarPlacar(repositorio.placar(filtro), taxaBase),
                sinais.stream().map(s -> new SinalNaLinhaDoTempo(
                        s.dataPregao(), s.simbolo(), s.recomendacao(), direcao(s.recomendacao()),
                        s.nivelRisco(), s.confiancaScore(), s.precoFechamento(), s.versaoRegra(),
                        resultados.getOrDefault(s.id(), Map.of()))).toList(),
                AVISO);
    }

    /**
     * Acerto medido contra a taxa-base da direcao apostada: para compra, a
     * fracao de janelas que subiram; para venda, a que caiu. Ordenado por
     * versao (mais nova primeiro), direcao e horizonte.
     */
    static List<LinhaPlacar> montarPlacar(List<RepositorioDiarioDeSinais.LinhaPlacar> linhas,
                                          Map<Integer, BigDecimal> taxaBaseDeAlta) {
        return linhas.stream()
                .map(l -> {
                    int direcao = direcao(l.recomendacao());
                    BigDecimal base = taxaBaseDeAlta.get(l.horizonte());
                    if (base != null && direcao < 0) {
                        base = BigDecimal.ONE.subtract(base);
                    }
                    return new LinhaPlacar(
                            l.versaoRegra(), l.recomendacao(), direcao, l.horizonte(), l.avaliados(),
                            l.comDirecao(), l.acertos(),
                            l.comDirecao() > 0 ? razao(l.acertos(), l.comDirecao()) : null,
                            direcao == 0 ? null : arredondar(base),
                            arredondar(l.retornoMedio()), arredondar(l.excessoMedioCdi()),
                            arredondar(l.excessoMedioBova11()),
                            l.avaliados() >= AMOSTRA_MINIMA);
                })
                .sorted(Comparator.comparing(LinhaPlacar::versaoRegra, Comparator.reverseOrder())
                        .thenComparing(LinhaPlacar::direcao, Comparator.reverseOrder())
                        .thenComparing(LinhaPlacar::recomendacao)
                        .thenComparingInt(LinhaPlacar::horizonte))
                .toList();
    }

    static int direcao(String recomendacao) {
        if (recomendacao == null) {
            return 0;
        }
        if (COMPRA.contains(recomendacao)) {
            return 1;
        }
        return VENDA.contains(recomendacao) ? -1 : 0;
    }

    private static Map<Long, Map<Integer, ResultadoDoHorizonte>> agruparResultados(
            List<RepositorioDiarioDeSinais.Resultado> resultados) {
        Map<Long, Map<Integer, ResultadoDoHorizonte>> porSinal = new HashMap<>();
        for (RepositorioDiarioDeSinais.Resultado r : resultados) {
            porSinal.computeIfAbsent(r.sinalId(), id -> new TreeMap<>()).put(r.horizonte(),
                    new ResultadoDoHorizonte(r.dataSaida(), r.retornoLiquido(), r.excessoCdi(),
                            r.excessoBova11(), r.acerto(), r.eventoSuspeito()));
        }
        return porSinal;
    }

    private static BigDecimal razao(long parte, long todo) {
        return BigDecimal.valueOf(parte).divide(BigDecimal.valueOf(todo), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal arredondar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(4, RoundingMode.HALF_UP);
    }

    private static String normalizar(String simbolo) {
        if (simbolo == null || simbolo.isBlank()) {
            return null;
        }
        return simbolo.trim().toUpperCase(Locale.ROOT);
    }
}
