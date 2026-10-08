package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoListagemDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioListagemAtivos.Filtro;
import br.com.miranda.gestor.ativos.brutos.service.ServicoListagemAtivos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tabela unica de ativos do painel (TASK-UX-5 / REQ-UX-8): base + monitorados
 * numa resposta paginada, com preco, variacao, sparkline, sinal, ultimo
 * comunicado e selos por linha. Substitui, para a listagem, as chamadas de
 * {@code /base/ativos}, {@code /favoritos} e {@code /ativos/registrados} -
 * que continuam valendo. GET sem efeito colateral.
 */
@RestController
@RequiredArgsConstructor
public class ListagemAtivosController {

    private static final int TAMANHO_PAGINA_PADRAO = 30;

    private final ServicoListagemAtivos servico;

    /**
     * @param q           prefixo do simbolo ou trecho do nome da empresa
     * @param setor       setor exato (valores de {@code /base/setores})
     * @param uf          UF da sede ({@code cvm_empresa.uf_municipio}, V18; REQ-ETL-1 do painel)
     * @param favoritos   so favoritos (coleta intradiaria BRAPI)
     * @param monitorados so ativos cadastrados em ativo_monitorado
     */
    @GetMapping("/painel/ativos")
    public Page<AtivoListagemDTO> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String setor,
            @RequestParam(required = false) String uf,
            @RequestParam(defaultValue = "false") boolean favoritos,
            @RequestParam(defaultValue = "false") boolean monitorados,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_PADRAO) int tamanho) {
        Filtro filtro = new Filtro(normalizarOuNulo(q), normalizarOuNulo(setor),
                ufOuNulo(uf), favoritos, monitorados);
        return servico.listar(filtro, pagina, tamanho);
    }

    private static String ufOuNulo(String valor) {
        String uf = normalizarOuNulo(valor);
        return uf == null ? null : uf.toUpperCase(java.util.Locale.ROOT);
    }

    private static String normalizarOuNulo(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.trim();
    }
}
