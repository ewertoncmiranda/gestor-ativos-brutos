package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.SaudeOpcoesDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOpcoes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SaudeOpcoesControllerTest {

    @Test
    void devolveMetricasObrigatoriasSemEfeitoColateral() {
        var esperado = new SaudeOpcoesDTO(
                SaudeOpcoesDTO.StatusSaudeOpcoes.OK, "cadeia valida", 120, 20, 100, 80,
                3, 7, List.of(new SaudeOpcoesDTO.PrefixoSuspeito("BOVA", 40)),
                LocalDate.of(2026, 10, 9), new BigDecimal("16.67"));
        var repo = new RepoFake(Optional.of(esperado));
        var controller = new SaudeOpcoesController(repo);

        assertEquals(esperado, controller.saude());
        assertEquals(esperado, controller.saude());
        assertEquals(2, repo.consultas);
    }

    @Test
    void explicaQuandoViewsAindaNaoExistem() {
        var controller = new SaudeOpcoesController(new RepoFake(Optional.empty()));

        var resposta = controller.saude();

        assertEquals(SaudeOpcoesDTO.StatusSaudeOpcoes.BASE_INDISPONIVEL, resposta.status());
        assertEquals(0, resposta.totalRegistrosOpcoes());
        assertNull(resposta.ultimoPregaoComOpcoesValidas());
    }

    private static final class RepoFake extends RepositorioOpcoes {
        private final Optional<SaudeOpcoesDTO> resposta;
        private int consultas;

        private RepoFake(Optional<SaudeOpcoesDTO> resposta) {
            super(null);
            this.resposta = resposta;
        }

        @Override
        public Optional<SaudeOpcoesDTO> saude() {
            consultas++;
            return resposta;
        }
    }
}
