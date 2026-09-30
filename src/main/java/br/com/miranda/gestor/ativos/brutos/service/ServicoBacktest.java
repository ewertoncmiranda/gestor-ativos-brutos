package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.BacktestDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RankingBacktestDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioBacktest;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioBacktestRanking;
import br.com.miranda.gestor.ativos.brutos.tools.IntervaloConfianca;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Monta a visao do ultimo backtest walk-forward (CTR-14). A taxa-base ja vem
 * gravada na direcao da aposta (alta para compra, queda para venda).
 */
@Service
@RequiredArgsConstructor
public class ServicoBacktest {

    public static final String AVISO =
            "Simulação sobre preços oficiais da B3 (COTAHIST, sem ajuste de proventos) e balanços da CVM "
                    + "pela data de entrega. Mede o método, não é recomendação de investimento. Resultado do "
                    + "período de teste é o que vale: o de calibração foi visto ao ajustar as regras.";

    public static final String AVISO_RANKING =
            "Mede se a regra ordena melhor do que pior (correlacao de Spearman entre o ranking previsto e o "
                    + "retorno real, e retorno medio por quintil), nao so se acerta uma classe. "
                    + AVISO;

    private final RepositorioBacktest repositorio;
    private final RepositorioBacktestRanking repositorioRanking;
    private final ObjectMapper objectMapper;

    /** Plano LAC, LAC-GES-1: metodo RANKING (infra V16). Vazio sem execucao ou sem a migracao. */
    public RankingBacktestDTO montarRanking() {
        return repositorioRanking.ultimaExecucao()
                .map(e -> {
                    List<RepositorioBacktestRanking.LinhaJanela> janelas = repositorioRanking.janelas(e.id());
                    Map<String, List<RepositorioBacktestRanking.LinhaQuintil>> quintisPorChave =
                            repositorioRanking.quintis(e.id()).stream()
                                    .collect(java.util.stream.Collectors.groupingBy(ServicoBacktest::chave));
                    return new RankingBacktestDTO(
                            new RankingBacktestDTO.Execucao(e.id(), e.finalizadoEm(), e.hipotese(),
                                    e.numeroTentativa(), e.esquemaValidacao()),
                            janelas.stream().map(j -> janelaRanking(j, quintisPorChave.get(chave(j)))).toList(),
                            AVISO_RANKING);
                })
                .orElseGet(() -> new RankingBacktestDTO(null, List.of(), AVISO_RANKING));
    }

    private static RankingBacktestDTO.Janela janelaRanking(RepositorioBacktestRanking.LinhaJanela j,
                                                            List<RepositorioBacktestRanking.LinhaQuintil> quintis) {
        List<RankingBacktestDTO.Quintil> lista = quintis == null ? List.of() : quintis.stream()
                .map(q -> new RankingBacktestDTO.Quintil(q.quintil(), q.retornoMedio(), q.ativos()))
                .toList();
        BigDecimal q1 = valorDoQuintil(lista, 1);
        BigDecimal q5 = valorDoQuintil(lista, 5);
        BigDecimal diferenca = q1 == null || q5 == null ? null : q5.subtract(q1);
        return new RankingBacktestDTO.Janela(j.versaoRegra(), j.janela(), j.horizonte(),
                j.icSpearmanMedio() == null ? null : j.icSpearmanMedio().setScale(4, RoundingMode.HALF_UP),
                IntervaloConfianca.media(j.icSpearmanMedio(), j.icSpearmanDesvio(), (long) j.meses()),
                j.meses(), j.mediaAtivos(), lista, diferenca);
    }

    private static BigDecimal valorDoQuintil(List<RankingBacktestDTO.Quintil> quintis, int numero) {
        return quintis.stream().filter(q -> q.quintil() == numero).findFirst()
                .map(RankingBacktestDTO.Quintil::retornoMedio).orElse(null);
    }

    private static String chave(RepositorioBacktestRanking.LinhaJanela j) {
        return j.versaoRegra() + '\u0000' + j.janela() + '\u0000' + j.horizonte();
    }

    private static String chave(RepositorioBacktestRanking.LinhaQuintil q) {
        return q.versaoRegra() + '\u0000' + q.janela() + '\u0000' + q.horizonte();
    }

    public BacktestDTO montar() {
        return repositorio.ultimaExecucao()
                .map(e -> new BacktestDTO(
                        new BacktestDTO.Execucao(e.id(), e.finalizadoEm(), e.inicioPeriodo(), e.fimPeriodo(),
                                e.corteCalibracao(), e.ativos(), e.sinais(), json(e.parametrosJson()),
                                e.observacoes()),
                        repositorio.placar(e.id()).stream().map(ServicoBacktest::linha).toList(),
                        ServicoDiarioDeSinais.AMOSTRA_MINIMA,
                        AVISO))
                .orElseGet(() -> new BacktestDTO(null, List.of(), ServicoDiarioDeSinais.AMOSTRA_MINIMA, AVISO));
    }

    private static BacktestDTO.Linha linha(RepositorioBacktest.Linha l) {
        int direcao = ServicoDiarioDeSinais.direcao(l.recomendacao());
        BigDecimal taxaAcerto = l.acertos() == null || l.avaliados() == 0 ? null
                : BigDecimal.valueOf(l.acertos()).divide(BigDecimal.valueOf(l.avaliados()), 4, RoundingMode.HALF_UP);
        return new BacktestDTO.Linha(l.versaoRegra(), l.periodo(), l.recomendacao(), direcao, l.horizonte(),
                l.avaliados(), taxaAcerto, l.taxaBase(), l.retornoMedio(), l.excessoMedioCdi(),
                l.excessoMedioCarteira(), l.avaliados() >= ServicoDiarioDeSinais.AMOSTRA_MINIMA,
                bootstrap(l.icAcertoInferior(), l.icAcertoSuperior(),
                        l.acertos() == null ? null : IntervaloConfianca.wilson(l.acertos(), l.avaliados())),
                IntervaloConfianca.media(l.excessoMedioCdi(), l.desvioExcessoCdi(), l.nExcessoCdi()),
                bootstrap(l.icExcessoCarteiraInferior(), l.icExcessoCarteiraSuperior(),
                        IntervaloConfianca.media(l.excessoMedioCarteira(), l.desvioExcessoCarteira(),
                                l.nExcessoCarteira())),
                l.janelasComProvento(),
                l.icAcertoInferior() != null || l.icExcessoCarteiraInferior() != null ? "BOOTSTRAP_BLOCOS" : "ANALITICO");
    }

    /** Prefere o intervalo do bootstrap em blocos (gravado pelo backtest); sem ele, o analitico. */
    private static IntervaloConfianca.Intervalo bootstrap(BigDecimal inferior, BigDecimal superior,
                                                          IntervaloConfianca.Intervalo analitico) {
        if (inferior == null || superior == null) {
            return analitico;
        }
        return new IntervaloConfianca.Intervalo(inferior.setScale(4, RoundingMode.HALF_UP),
                superior.setScale(4, RoundingMode.HALF_UP));
    }

    private JsonNode json(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return objectMapper.readTree(texto);
        } catch (Exception e) {
            return null;
        }
    }
}
