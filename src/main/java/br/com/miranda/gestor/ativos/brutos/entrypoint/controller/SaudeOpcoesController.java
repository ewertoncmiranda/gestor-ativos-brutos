package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.SaudeOpcoesDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOpcoes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Endpoint GET-only de diagnostico da cadeia de opcoes (OPT-GES-3). */
@RestController
@RequestMapping("/opcoes")
@RequiredArgsConstructor
public class SaudeOpcoesController {

    private final RepositorioOpcoes repositorio;

    @GetMapping("/saude")
    public SaudeOpcoesDTO saude() {
        return repositorio.saude().orElseGet(() -> new SaudeOpcoesDTO(
                SaudeOpcoesDTO.StatusSaudeOpcoes.BASE_INDISPONIVEL,
                "As migrations de validacao e observabilidade da cadeia de opcoes ainda nao estao disponiveis.",
                0, 0, 0, 0, 0, 0, List.of(), null, BigDecimal.ZERO));
    }
}
