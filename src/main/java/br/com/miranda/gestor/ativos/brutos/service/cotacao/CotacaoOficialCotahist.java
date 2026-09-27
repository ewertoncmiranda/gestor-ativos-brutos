package br.com.miranda.gestor.ativos.brutos.service.cotacao;

import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPregoes;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPregoes.Pregao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Fechamento oficial mais recente (COTAHIST) e a variacao sobre o pregao
 * anterior. Reaproveita RepositorioPregoes, o mesmo das velas por periodo, com
 * os codigos antigos do mesmo papel emendados.
 */
@Component
@RequiredArgsConstructor
public class CotacaoOficialCotahist implements FonteCotacaoRecente {

    // Folga para feriados prolongados ao buscar os dois ultimos pregoes.
    private static final int DIAS_DE_BUSCA = 15;
    private static final LocalTime FECHAMENTO_B3 = LocalTime.of(18, 0);

    private final RepositorioPregoes repositorio;

    @Override
    public Optional<CotacaoRecente> cotacao(String simbolo) {
        LocalDate hoje = LocalDate.now();
        List<Pregao> pregoes = repositorio.oficiais(
                repositorio.codigosDoPapel(simbolo), hoje.minusDays(DIAS_DE_BUSCA), hoje);
        if (pregoes.isEmpty()) {
            return Optional.empty();
        }
        Pregao ultimo = pregoes.get(pregoes.size() - 1);
        return Optional.of(new CotacaoRecente(ultimo.fechamento(), variacao(pregoes, ultimo),
                ultimo.data().atTime(FECHAMENTO_B3), "B3_COTAHIST"));
    }

    private static BigDecimal variacao(List<Pregao> pregoes, Pregao ultimo) {
        if (pregoes.size() < 2) {
            return null;
        }
        BigDecimal anterior = pregoes.get(pregoes.size() - 2).fechamento();
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return ultimo.fechamento().divide(anterior, 6, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
