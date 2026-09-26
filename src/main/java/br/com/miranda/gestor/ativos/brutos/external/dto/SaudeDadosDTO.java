package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Saude dos dados (contrato infra#CTR-12): a nota de "confiabilidade dos
 * dados" medida em vez de opinada.
 *
 * @param fontes     idade de cada fonte contra o prazo esperado dela
 * @param cobertura  totais: quantos ativos tem preco, balanco, os dois, TTM
 * @param ativos     o que falta em cada ativo do universo
 * @param precos     checagem cruzada BRAPI x COTAHIST
 * @param aliases    codigos antigos que ainda estao no universo (deveria ser vazio)
 */
public record SaudeDadosDTO(
        LocalDateTime geradoEm,
        String estadoGeral,
        List<Fonte> fontes,
        Cobertura cobertura,
        List<Ativo> ativos,
        ChecagemPrecos precos,
        List<String> aliases) {

    /** estado: OK, ATRASADA, SEM_DADO, ERRO. */
    public record Fonte(String codigo, String nome, LocalDateTime atualizadoEm, Long idadeHoras,
                        int prazoHoras, String estado, String ultimoErro, String comoResolver) {
    }

    public record Cobertura(int ativos, int comPreco, int comFundamentos, int comPrecoEFundamentos,
                            int comTtm, int comEntregaConhecida, int semCnpj) {
    }

    public record Ativo(String simbolo, boolean monitorado, String cnpj, LocalDate ultimaVela,
                        LocalDateTime cotacaoEm, LocalDate periodoAnual, LocalDate entregaAnual,
                        LocalDate periodoTtm, LocalDateTime ultimoInsight, LocalDate ultimoPregaoB3,
                        List<String> problemas) {
    }

    public record ChecagemPrecos(int janelaDias, BigDecimal limite, long paresComparados,
                                 List<Divergencia> divergencias) {
    }

    public record Divergencia(String simbolo, LocalDate data, BigDecimal fechamentoBrapi,
                              BigDecimal fechamentoB3, BigDecimal diferenca) {
    }
}
