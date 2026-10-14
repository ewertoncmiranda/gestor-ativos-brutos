package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.HistoricoFundamentosDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioHistoricoFundamentos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicoHistoricoFundamentos {
    private final RepositorioHistoricoFundamentos repositorio;

    public HistoricoFundamentosDTO buscar(String simbolo, int anos) {
        String normalizado = simbolo == null ? "" : simbolo.trim().toUpperCase();
        return new HistoricoFundamentosDTO(normalizado, repositorio.buscar(normalizado, anos));
    }
}
