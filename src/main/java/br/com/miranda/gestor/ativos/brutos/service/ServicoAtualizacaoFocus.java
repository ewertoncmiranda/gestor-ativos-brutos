package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.IndiceMacroEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.ExpectativaFocusDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteFocusBcb;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIndiceMacro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * MACRO-GES-2: grava as medianas do Focus (Selic e IPCA do ano corrente e do
 * seguinte) em indice_macro, como FOCUS_SELIC_2026, FOCUS_IPCA_2027 etc., com a
 * data da pesquisa. Idempotente por (codigo, data): regravar a mesma pesquisa
 * so atualiza o valor. Falha do BCB so registra log; o ciclo segue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoAtualizacaoFocus {

    static final Map<String, String> INDICADORES = Map.of("Selic", "FOCUS_SELIC", "IPCA", "FOCUS_IPCA");
    // ~5 semanas de pesquisas por ano projetado: basta para a variacao recente.
    static final int PESQUISAS_POR_CICLO = 5;

    private final ClienteFocusBcb clienteFocus;
    private final RepositorioIndiceMacro repositorioIndiceMacro;

    public void atualizar() {
        atualizar(LocalDate.now(Clock.systemDefaultZone()));
    }

    int atualizar(LocalDate hoje) {
        List<Integer> anos = List.of(hoje.getYear(), hoje.getYear() + 1);
        int gravados = 0;
        for (var indicador : INDICADORES.entrySet()) {
            try {
                for (ExpectativaFocusDTO linha : clienteFocus.consultarAnuais(indicador.getKey(), anos, PESQUISAS_POR_CICLO)) {
                    if (persistir(indicador.getValue(), linha)) {
                        gravados++;
                    }
                }
            } catch (Exception e) {
                log.error("(SERVICO) - Erro ao atualizar Focus {}: {}", indicador.getKey(), e.getMessage(), e);
            }
        }
        return gravados;
    }

    static String codigo(String prefixo, String ano) {
        return prefixo + "_" + ano;
    }

    private boolean persistir(String prefixo, ExpectativaFocusDTO linha) {
        if (linha.mediana() == null || linha.dataReferencia() == null || linha.data() == null) {
            return false;
        }
        LocalDate data;
        try {
            data = LocalDate.parse(linha.data());
        } catch (DateTimeParseException e) {
            return false;
        }
        String codigo = codigo(prefixo, linha.dataReferencia().trim());
        IndiceMacroEntity entidade = repositorioIndiceMacro.findByCodigoSerieAndData(codigo, data)
                .orElseGet(IndiceMacroEntity::new);
        entidade.setCodigoSerie(codigo);
        entidade.setData(data);
        entidade.setValor(linha.mediana());
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorioIndiceMacro.save(entidade);
        return true;
    }
}
