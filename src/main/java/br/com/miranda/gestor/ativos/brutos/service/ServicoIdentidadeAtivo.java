package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.IdentidadeAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivoF360;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicoIdentidadeAtivo {
    private final RepositorioIdentidadeAtivoF360 repositorio;

    public IdentidadeAtivoDTO buscar(String simbolo) {
        String normalizado = simbolo == null ? "" : simbolo.trim().toUpperCase();
        return repositorio.buscar(normalizado);
    }
}
