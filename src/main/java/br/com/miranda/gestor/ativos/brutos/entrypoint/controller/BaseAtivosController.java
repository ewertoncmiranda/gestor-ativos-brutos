package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoBaseDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioBaseAtivos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tela "Base": o universo amplo de ativos que o ecossistema conhece
 * (cvm_ticker/cvm_empresa, TASK-59) - diferente de {@link AtivoController},
 * que so lista os favoritos do usuario (ativo_monitorado). Somente leitura,
 * sem chamar a BRAPI: preco vem do fechamento oficial (cotacao_b3_diaria).
 */
@RestController
@RequiredArgsConstructor
public class BaseAtivosController {

    private static final int TAMANHO_PAGINA_PADRAO = 30;

    private final RepositorioBaseAtivos repositorioBaseAtivos;

    @GetMapping("/base/ativos")
    public Page<AtivoBaseDTO> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String setor,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_PADRAO) int tamanho) {
        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by("simbolo"));
        String qNormalizado = normalizarOuNulo(q);
        String setorNormalizado = normalizarOuNulo(setor);
        return repositorioBaseAtivos.buscar(qNormalizado, setorNormalizado, pageable);
    }

    @GetMapping("/base/setores")
    public List<String> listarSetores() {
        return repositorioBaseAtivos.setoresDisponiveis();
    }

    private String normalizarOuNulo(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.trim();
    }
}
