package br.com.miranda.gestor.ativos.brutos.service;
import br.com.miranda.gestor.ativos.brutos.external.InsightAcao;
import br.com.miranda.gestor.ativos.brutos.repository.InsightAcaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class InsightAcaoService {

    private final InsightAcaoRepository repository;

    public List<InsightAcao> buscarPorSimbolo(String simbolo) {
        try {
            return repository.findBySimbolo(simbolo);
        } catch (Exception e) {
            log.error("Erro ao buscar insights para simbolo: {}, erro: {}", simbolo, e.getMessage(), e);
            return List.of();
        }

    }

    public List<InsightAcao> buscarPorSimboloNative(String simbolo) {
        return repository.findBySimboloNative(simbolo);
    }
}

