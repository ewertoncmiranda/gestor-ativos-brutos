package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.CategoriaComunicado;
import br.com.miranda.gestor.ativos.brutos.external.ComunicadoCvmEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Um documento oficial da CVM. Só metadados e o link para o documento no RAD:
 * o conteúdo não é copiado (contrato {@code infra#CTR-10}).
 */
@Data
@Builder
public class ComunicadoDTO {

    private String protocolo;
    private String categoria;
    private String categoriaRotulo;
    private String categoriaOriginal;
    private String tipo;
    private String especie;
    private String assunto;
    private LocalDate dataReferencia;
    private LocalDate dataEntrega;
    private Integer versao;
    private String link;

    public static ComunicadoDTO de(ComunicadoCvmEntity entidade) {
        CategoriaComunicado categoria = CategoriaComunicado.doBanco(entidade.getCategoria());
        return ComunicadoDTO.builder()
                .protocolo(entidade.getProtocoloCvm())
                .categoria(entidade.getCategoria())
                .categoriaRotulo(categoria != null ? categoria.getRotulo() : entidade.getCategoriaOriginal())
                .categoriaOriginal(entidade.getCategoriaOriginal())
                .tipo(entidade.getTipo())
                .especie(entidade.getEspecie())
                .assunto(entidade.getAssunto())
                .dataReferencia(entidade.getDataReferencia())
                .dataEntrega(entidade.getDataEntrega())
                .versao(entidade.getVersao())
                .link(entidade.getLinkDownload())
                .build();
    }
}
