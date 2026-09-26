package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.PontoSerieSgsDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBancoCentral;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Escritor unico dos indices macro (Selic, CDI, IPCA...). So esse servico
 * fala com o Banco Central; o controller de leitura so consulta
 * indice_macro, mesmo principio do cache-aside da BRAPI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoAtualizacaoIndicesMacro {

    private static final DateTimeFormatter FORMATO_BCB = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Codigo da serie SGS -> quantos pontos recentes buscar por ciclo. Selic
    // e CDI sao diarios (mantem uns dias de historico); IPCA e mensal, um
    // ponto por mes ja cobre bastante tempo com poucos pontos.
    private static final Map<String, Integer> SERIES = Map.of(
            "SELIC", 432,
            "CDI", 12,
            "IPCA", 433
    );
    private static final int PONTOS_POR_CICLO = 10;
    // 10 pontos diarios ~ 2 semanas corridas; alem disso o ciclo nao fecha o buraco.
    private static final int DIAS_SEM_BURACO = 14;
    // Feriado de ano-novo + fim de semana podem empurrar o 1o dia util ate ~5 dias.
    private static final int FOLGA_INICIO_DIAS = 7;

    private final ClienteBancoCentral clienteBancoCentral;
    private final RepositorioIndiceMacro repositorioIndiceMacro;

    /**
     * Garante a serie diaria completa desde {@code desde}: busca o que falta
     * antes do ponto mais antigo e depois do mais recente. O ciclo horario
     * so traz os ultimos 10 pontos - suficiente para exibir a taxa atual, nao
     * para o backtest e o diario de sinais, que acumulam o CDI dia a dia.
     *
     * <p>Idempotente: com a serie completa nao faz nenhuma chamada. Fatia o
     * periodo ano a ano porque o BCB limita o intervalo por consulta.
     *
     * @return quantos pontos foram gravados
     */
    public int completarHistorico(String codigoLogico, LocalDate desde, LocalDate hoje) {
        Integer codigoSgs = SERIES.get(codigoLogico);
        if (codigoSgs == null) {
            throw new IllegalArgumentException("Serie macro desconhecida: " + codigoLogico);
        }

        var maisAntigo = repositorioIndiceMacro.findFirstByCodigoSerieOrderByDataAsc(codigoLogico)
                .map(IndiceMacroEntity::getData);
        var maisRecente = repositorioIndiceMacro.findFirstByCodigoSerieOrderByDataDesc(codigoLogico)
                .map(IndiceMacroEntity::getData);

        int gravados = 0;
        if (maisAntigo.isEmpty()) {
            gravados += buscarEGravar(codigoLogico, codigoSgs, desde, hoje);
        } else {
            // Serie diaria so tem dia util: comecar em 04/01 quando se pediu
            // 01/01 e serie completa, nao buraco - sem a folga, toda subida
            // voltaria a consultar o BCB por um periodo sem nenhum ponto.
            if (maisAntigo.get().isAfter(desde.plusDays(FOLGA_INICIO_DIAS))) {
                gravados += buscarEGravar(codigoLogico, codigoSgs, desde, maisAntigo.get().minusDays(1));
            }
            // Gestor fora do ar por mais de ~2 semanas deixa buraco que o ciclo
            // de 10 pontos nao cobre.
            if (maisRecente.get().isBefore(hoje.minusDays(DIAS_SEM_BURACO))) {
                gravados += buscarEGravar(codigoLogico, codigoSgs, maisRecente.get().plusDays(1), hoje);
            }
        }
        if (gravados > 0) {
            log.info("(SERVICO) - Historico de {} completado: {} ponto(s)", codigoLogico, gravados);
        }
        return gravados;
    }

    private int buscarEGravar(String codigoLogico, int codigoSgs, LocalDate inicio, LocalDate fim) {
        int gravados = 0;
        for (LocalDate fatia = inicio; !fatia.isAfter(fim); fatia = fatia.plusYears(1)) {
            LocalDate fimDaFatia = fatia.plusYears(1).minusDays(1).isBefore(fim)
                    ? fatia.plusYears(1).minusDays(1)
                    : fim;
            for (PontoSerieSgsDTO ponto : clienteBancoCentral.consultarSeriePeriodo(codigoSgs, fatia, fimDaFatia)) {
                if (persistir(codigoLogico, ponto)) {
                    gravados++;
                }
            }
        }
        return gravados;
    }

    public void atualizarIndicesMacro() {
        SERIES.forEach((codigoLogico, codigoSgs) -> {
            try {
                var pontos = clienteBancoCentral.consultarSerie(codigoSgs, PONTOS_POR_CICLO);
                pontos.forEach(ponto -> persistir(codigoLogico, ponto));
            } catch (Exception e) {
                log.error("(SERVICO) - Erro ao atualizar indice macro {}: {}", codigoLogico, e.getMessage(), e);
            }
        });
    }

    private boolean persistir(String codigoLogico, PontoSerieSgsDTO ponto) {
        if (ponto.data() == null || ponto.valor() == null) {
            return false;
        }
        LocalDate data = LocalDate.parse(ponto.data(), FORMATO_BCB);
        IndiceMacroEntity entidade = repositorioIndiceMacro.findByCodigoSerieAndData(codigoLogico, data)
                .orElseGet(IndiceMacroEntity::new);
        entidade.setCodigoSerie(codigoLogico);
        entidade.setData(data);
        entidade.setValor(ponto.valor());
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorioIndiceMacro.save(entidade);
        return true;
    }
}
