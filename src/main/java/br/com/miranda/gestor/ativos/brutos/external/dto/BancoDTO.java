package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Indicadores de banco (FONTES-GES-2): IF.data + P/VP. JSON camelCase. */
public record BancoDTO(
        String simbolo,
        boolean ehBanco,
        String setorCvm,
        String motivo,
        Trimestre ultimoTrimestre,
        List<Trimestre> serie,
        BigDecimal pvp,
        String pvpMotivo,
        BigDecimal roeSobrePvp,
        List<Selo> selos,
        String leitura) {

    public record Trimestre(LocalDate dataBase, BigDecimal basileia, BigDecimal capitalPrincipal,
                            BigDecimal inadimplencia, BigDecimal roe) {
    }

    /** status: OK, ALERTA, ACIMA, ABAIXO ou SEM_DADO. */
    public record Selo(String tipo, String status, BigDecimal valor, BigDecimal referencia, String leitura) {
    }

    public static BancoDTO naoBanco(String simbolo, String setor, String motivo) {
        return new BancoDTO(simbolo, false, setor, motivo, null, null, null, null, null, null, null);
    }
}
