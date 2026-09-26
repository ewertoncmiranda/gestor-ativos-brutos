package br.com.miranda.gestor.ativos.brutos.external;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Categorias de comunicado gravadas pelo {@code etl-fundamentos-cvm} em
 * {@code comunicado_cvm.categoria} (contrato {@code infra#CTR-08}).
 *
 * <p>A ordem de declaração é a ordem de relevância da newsletter
 * ({@code infra#CTR-10}): um fato relevante vem antes de um aviso de assembleia.
 */
public enum CategoriaComunicado {

    FATO_RELEVANTE("Fato relevante", true),
    PROVENTOS("Proventos", true),
    RESULTADOS("Resultados", true),
    COMUNICADO_MERCADO("Comunicado ao mercado", true),
    AVISO_ACIONISTAS("Aviso aos acionistas", true),
    CALENDARIO_EVENTOS("Calendário de eventos", true),
    ASSEMBLEIA("Assembleia", false);

    private final String rotulo;
    private final boolean padrao;

    CategoriaComunicado(String rotulo, boolean padrao) {
        this.rotulo = rotulo;
        this.padrao = padrao;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Menor é mais relevante. */
    public int getPrioridade() {
        return ordinal();
    }

    /** As que entram quando o cliente não filtra - as mesmas que o ETL carrega por padrão. */
    public static List<CategoriaComunicado> padrao() {
        return Arrays.stream(values()).filter(c -> c.padrao).toList();
    }

    /**
     * Converte o parâmetro HTTP. Valor desconhecido é erro do cliente
     * ({@link IllegalArgumentException} vira 400 no tratador global), não um
     * filtro que silenciosamente não casa nada.
     */
    public static CategoriaComunicado deParametro(String valor) {
        String normalizado = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(c -> c.name().equals(normalizado))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Categoria desconhecida: '" + valor + "'. Aceitas: "
                                + Arrays.toString(values())));
    }

    /** Leitura do banco: categoria que o gestor não conhece não derruba a resposta. */
    public static CategoriaComunicado doBanco(String valor) {
        try {
            return valueOf(valor);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
