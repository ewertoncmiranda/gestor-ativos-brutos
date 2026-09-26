package br.com.miranda.gestor.ativos.brutos.external.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Comunicados oficiais de um ticker, paginados, mais recente primeiro
 * ({@code GET /empresas/{simbolo}/comunicados}).
 *
 * <p>{@code dadosAte} acompanha a resposta porque a CVM republica a base cerca
 * de uma vez por semana: "nenhum comunicado esta semana" pode ser só atraso da
 * fonte, e o leitor precisa saber a diferença.
 */
@Data
@Builder
public class LinhaDoTempoComunicadosDTO {

    private String simbolo;
    private List<String> categorias;
    private LocalDate desde;
    private LocalDate ate;
    private int pagina;
    private int tamanho;
    private long total;
    private int totalPaginas;
    private LocalDate dadosAte;
    private String fonte;
    private String aviso;
    private List<ComunicadoDTO> comunicados;
}
