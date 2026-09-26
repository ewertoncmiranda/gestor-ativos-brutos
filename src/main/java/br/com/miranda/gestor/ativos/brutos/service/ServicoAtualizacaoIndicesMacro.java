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

    private final ClienteBancoCentral clienteBancoCentral;
    private final RepositorioIndiceMacro repositorioIndiceMacro;

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

    private void persistir(String codigoLogico, PontoSerieSgsDTO ponto) {
        if (ponto.data() == null || ponto.valor() == null) {
            return;
        }
        LocalDate data = LocalDate.parse(ponto.data(), FORMATO_BCB);
        IndiceMacroEntity entidade = repositorioIndiceMacro.findByCodigoSerieAndData(codigoLogico, data)
                .orElseGet(IndiceMacroEntity::new);
        entidade.setCodigoSerie(codigoLogico);
        entidade.setData(data);
        entidade.setValor(ponto.valor());
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorioIndiceMacro.save(entidade);
    }
}
