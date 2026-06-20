package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAnaliseAcao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServicoAnaliseAcao {

    private final RepositorioAnaliseAcao repositorio;

    /**
     * Busca as análises persistidas para um símbolo, retornando lista vazia em falhas de leitura.
     */
    public List<AnaliseAcaoEntity> buscarPorSimbolo(String simbolo) {
        try {
            return repositorio.findBySimbolo(simbolo);
        } catch (Exception e) {
            log.error("Erro ao buscar analises para simbolo: {}, erro: {}", simbolo, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Busca análises usando a consulta SQL nativa do repositório.
     */
    public List<AnaliseAcaoEntity> buscarPorSimboloConsultaNativa(String simbolo) {
        return repositorio.buscarPorSimboloConsultaNativa(simbolo);
    }
}
