package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.HistoricoAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.IndicadorFundamentalistaEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.FundamentosCvmDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioHistoricoAcao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndicadorFundamentalista;
import br.com.miranda.gestor.ativos.brutos.tools.CalculadoraMultiplos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Monta a visão de fundamentos CVM de um ativo.
 *
 * <p>São duas leituras independentes: o fundamento (lento, muda por trimestre)
 * e a cotação (rápida, muda em segundos). Cotação ausente não esconde o
 * fundamento — só deixa os múltiplos nulos.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ServicoFundamentosCvm {

    private static final String TIPO_PERIODO_PADRAO = "ANUAL";

    private final RepositorioIndicadorFundamentalista repositorioIndicador;
    private final RepositorioHistoricoAcao repositorioHistorico;

    public FundamentosCvmDTO buscarPorSimbolo(String simbolo) {
        String normalizado = simbolo == null ? "" : simbolo.trim().toUpperCase();

        Optional<IndicadorFundamentalistaEntity> fundamento = buscarFundamento(normalizado);
        if (fundamento.isEmpty()) {
            log.info("Sem fundamentos CVM carregados para {}", normalizado);
            return FundamentosCvmDTO.vazio(normalizado);
        }

        FundamentosCvmDTO dto = FundamentosCvmDTO.de(fundamento.get());
        dto.setDefasagemDias(
                CalculadoraMultiplos.defasagemEmDias(dto.getPeriodo(), LocalDate.now()));

        aplicarMultiplos(normalizado, dto);
        return dto;
    }

    private Optional<IndicadorFundamentalistaEntity> buscarFundamento(String simbolo) {
        try {
            return repositorioIndicador
                    .findFirstBySimboloAndTipoPeriodoOrderByPeriodoDesc(simbolo, TIPO_PERIODO_PADRAO)
                    .or(() -> repositorioIndicador.findFirstBySimboloOrderByPeriodoDesc(simbolo));
        } catch (Exception e) {
            log.error("Erro ao ler fundamentos de {}: {}", simbolo, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Deriva P/L e P/VP com a cotação mais recente. Sem cotação, os múltiplos
     * ficam nulos e o resto da resposta segue completo.
     */
    private void aplicarMultiplos(String simbolo, FundamentosCvmDTO dto) {
        Optional<HistoricoAcaoEntity> cotacao;
        try {
            cotacao = repositorioHistorico.findFirstBySimboloOrderByTimestampDesc(simbolo);
        } catch (Exception e) {
            log.error("Erro ao ler cotacao de {}: {}", simbolo, e.getMessage(), e);
            return;
        }

        if (cotacao.isEmpty() || cotacao.get().getPrecoFechamento() == null) {
            log.debug("Sem cotacao para {}; multiplos ficam nulos", simbolo);
            return;
        }

        BigDecimal preco = cotacao.get().getPrecoFechamento();
        dto.setPrecoReferencia(preco);
        dto.setPrecoEm(cotacao.get().getTimestamp());
        dto.setPrecoLucro(CalculadoraMultiplos.precoLucro(preco, dto.getLpa()));
        dto.setPrecoValorPatrimonial(
                CalculadoraMultiplos.precoValorPatrimonial(preco, dto.getVpa()));
    }
}
