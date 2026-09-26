package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAnaliseAcao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServicoAnaliseAcao {

    private final RepositorioAnaliseAcao repositorio;

    // Consolidacao so com analises recentes: sem janela, a recomendacao de
    // meses atras (outro preco, outro balanco) pesava igual a de hoje.
    @Value("${analise.consolidacao.janela-dias:30}")
    private int janelaDias = 30;

    /**
     * Busca as análises dos últimos {@code janelaDias} dias para um símbolo; sem
     * nenhuma na janela, cai para a mais recente (ativo pouco atualizado não
     * some da tela). Lista vazia em falhas de leitura.
     */
    public List<AnaliseAcaoEntity> buscarPorSimbolo(String simbolo) {
        try {
            List<AnaliseAcaoEntity> recentes = repositorio.findBySimboloAndDataAnaliseGreaterThanEqual(
                    simbolo, LocalDateTime.now().minusDays(janelaDias));
            if (!recentes.isEmpty()) {
                return recentes;
            }
            return repositorio.findFirstBySimboloOrderByDataAnaliseDesc(simbolo).map(List::of).orElse(List.of());
        } catch (Exception e) {
            log.error("Erro ao buscar analises para simbolo: {}, erro: {}", simbolo, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Busca a análise mais recente do símbolo, com o detalhes_json bruto (não consolidado/mediado),
     * usada para expor os fundamentos e cálculos exatos que embasaram a última decisão.
     */
    public Optional<AnaliseAcaoEntity> buscarUltimaPorSimbolo(String simbolo) {
        try {
            return repositorio.findFirstBySimboloOrderByDataAnaliseDesc(simbolo);
        } catch (Exception e) {
            log.error("Erro ao buscar ultima analise para simbolo: {}, erro: {}", simbolo, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Busca análises usando a consulta SQL nativa do repositório.
     */
    public List<AnaliseAcaoEntity> buscarPorSimboloConsultaNativa(String simbolo) {
        return repositorio.buscarPorSimboloConsultaNativa(simbolo);
    }
}
