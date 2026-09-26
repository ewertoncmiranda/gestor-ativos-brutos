package br.com.miranda.gestor.ativos.brutos.external.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Edição da newsletter de comunicados: os documentos de um período agrupados
 * por ticker da carteira monitorada ({@code GET /comunicados/newsletter}).
 *
 * <p>Empresas ordenadas pelo documento mais relevante que tiveram no período
 * (fato relevante antes de aviso de assembleia) e, no empate, por volume.
 * {@code semanaAnterior}/{@code semanaSeguinte} existem para o painel navegar
 * sem precisar calcular semana ISO no navegador.
 */
@Data
@Builder
public class NewsletterComunicadosDTO {

    /** Semana ISO (ex.: 2026-W39), ou nulo quando o período foi dado por desde/ate. */
    private String semana;
    private String semanaAnterior;
    private String semanaSeguinte;
    private LocalDate desde;
    private LocalDate ate;
    private List<String> categorias;
    private int totalDocumentos;
    private LocalDate dadosAte;
    private String fonte;
    private String aviso;
    private List<EmpresaNaEdicao> empresas;

    @Data
    @Builder
    public static class EmpresaNaEdicao {
        private String simbolo;
        private int total;
        /** Categoria -> quantidade, na ordem de relevância. */
        private Map<String, Long> porCategoria;
        private List<ComunicadoDTO> comunicados;
    }
}
