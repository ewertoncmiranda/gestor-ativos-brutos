package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteIbgeSidra;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Segundo escritor do cache indice_macro (o primeiro e
 * ServicoAtualizacaoIndicesMacro, que fala com o Banco Central) - mesma
 * tabela, mesmo repositorio, mesmo controller de leitura (IndiceMacroController)
 * e mesmo componente de tela (IndicesMacroPage): so muda a fonte (IBGE SIDRA)
 * e o formato do periodo, que aqui vem AAAAMM ou AAAA em vez de dd/MM/yyyy.
 *
 * Deliberadamente restrito a series simples (1 variavel, nivel Brasil, sem
 * dimensao de classificacao) - e o mesmo limite do ClienteIbgeSidra.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoAtualizacaoIndicadoresIbge {

    /**
     * Codigo logico -> (id do agregado, id da variavel) no catalogo do
     * SIDRA. Cada entrada foi validada ao vivo (curl direto na API, nao so
     * leitura de documentacao) antes de entrar aqui:
     * - DESEMPREGO: tabela 6381 (PNAD Continua, trimestral movel, mensal),
     *   variavel 4099 - serie ativa, ultimo ponto de 2026-07.
     * Deliberadamente NAO inclui PIB anual (tabela 6784: defasagem de anos,
     * fim em 2023 - baixo valor pra um painel que preza dado fresco) nem
     * Producao Fisica Industrial (tabela 8888: exige dimensao de
     * classificacao pra escolher "Industria geral", que ClienteIbgeSidra
     * ainda nao suporta).
     */
    private static final Map<String, int[]> SERIES = Map.of(
            "DESEMPREGO", new int[]{6381, 4099}
    );
    private static final int PONTOS_POR_CICLO = 6;

    private final ClienteIbgeSidra clienteIbgeSidra;
    private final RepositorioIndiceMacro repositorioIndiceMacro;

    public void atualizarIndicadores() {
        SERIES.forEach((codigoLogico, agregadoEVariavel) -> {
            try {
                var pontos = clienteIbgeSidra.consultarSerie(agregadoEVariavel[0], agregadoEVariavel[1], PONTOS_POR_CICLO);
                pontos.forEach((periodo, valor) -> persistir(codigoLogico, periodo, valor));
            } catch (Exception e) {
                log.error("(SERVICO) - Erro ao atualizar indicador IBGE {}: {}", codigoLogico, e.getMessage(), e);
            }
        });
    }

    private void persistir(String codigoLogico, String periodo, String valorTexto) {
        LocalDate data;
        BigDecimal valor;
        try {
            data = periodoParaData(periodo);
            valor = new BigDecimal(valorTexto);
        } catch (Exception e) {
            // Formato de periodo/valor inesperado (tabela do IBGE mudou):
            // pula so este ponto, nao derruba o indicador inteiro.
            log.warn("(SERVICO) - Ponto ignorado do indicador {} (periodo={}, valor={}): {}",
                    codigoLogico, periodo, valorTexto, e.getMessage());
            return;
        }

        IndiceMacroEntity entidade = repositorioIndiceMacro.findByCodigoSerieAndData(codigoLogico, data)
                .orElseGet(IndiceMacroEntity::new);
        entidade.setCodigoSerie(codigoLogico);
        entidade.setData(data);
        entidade.setValor(valor);
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorioIndiceMacro.save(entidade);
    }

    /**
     * O SIDRA usa periodo AAAAMM (mensal) ou AAAA (anual) - sem dia. Usa o
     * dia 1 do mes/ano como convencao, mesma ideia de "data de referencia"
     * que IndicesMacroPage.js ja exibe pras series do BCB.
     */
    private LocalDate periodoParaData(String periodo) {
        if (periodo.length() == 6) {
            int ano = Integer.parseInt(periodo.substring(0, 4));
            int mes = Integer.parseInt(periodo.substring(4, 6));
            return LocalDate.of(ano, mes, 1);
        }
        if (periodo.length() == 4) {
            return LocalDate.of(Integer.parseInt(periodo), 1, 1);
        }
        throw new IllegalArgumentException("Formato de periodo SIDRA desconhecido: " + periodo);
    }
}
