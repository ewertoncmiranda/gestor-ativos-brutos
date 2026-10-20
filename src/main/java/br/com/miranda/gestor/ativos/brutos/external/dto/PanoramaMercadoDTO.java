package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Panorama do mercado para o carrossel da Inicio (Plano MERC, contrato
 * GET /mercado/panorama do hub). Variacoes, coesao, presenca e spread sao
 * fracoes (0.0213 = 2,13%); o painel formata. Toda lista de ranking tem no
 * maximo 10 itens e posicao comeca em 1. Bloco sem dado vem vazio e o nome
 * dele entra em {@code indisponivel}.
 */
public final class PanoramaMercadoDTO {

    private PanoramaMercadoDTO() {
    }

    public record Panorama(String janela, LocalDate dataPregao, LocalDate dataInicial, Universo universo,
                           List<Setor> setores, List<Variacao> altas, List<Variacao> quedas, Liquidez liquidez,
                           Negociacao negociacao, Amplitude amplitude, List<VolumeAnormal> volumeAnormal,
                           Macro macro, List<Evento> eventos, List<String> indisponivel) {
    }

    /** Quantos ativos entraram em cada universo no ultimo pregao. */
    public record Universo(int liquidos, int presenca) {
    }

    /** Setor pela mediana; melhores/piores pelo desvio em relacao a mediana. */
    public record Setor(String setor, String grupo, int empresas, Double variacaoMediana, Double coesao,
                        String selo, List<EmpresaSetor> melhores, List<EmpresaSetor> piores) {
    }

    public record EmpresaSetor(String simbolo, String nome, double variacao, double desvio,
                               double volumeFinanceiro) {
    }

    /** Item de altas e quedas. */
    public record Variacao(int posicao, String simbolo, String nome, String setor, double variacao,
                           double volumeFinanceiro) {
    }

    public record Liquidez(List<ItemLiquidez> maiores, List<ItemLiquidez> menores) {
    }

    public record ItemLiquidez(int posicao, String simbolo, String nome, String setor, Double volumeMedio63d,
                               Double presenca, Double spreadEstimado) {
    }

    public record Negociacao(List<ItemNegociacao> maiores, List<ItemNegociacao> menores) {
    }

    public record ItemNegociacao(int posicao, String simbolo, String nome, String setor, double volumeFinanceiro,
                                 long negocios) {
    }

    /** Sem dado: campos nulos (o bloco entra em indisponivel). */
    public record Amplitude(Integer subiram, Integer cairam, Integer estaveis, Double acimaMedia21) {
    }

    /** MERC-GES-2. */
    public record VolumeAnormal(int posicao, String simbolo, double razao, double volumeFinanceiro) {
    }

    /** MERC-GES-2. */
    public record Macro(Double selic, Double cdiPeriodo, Double dolar, Double ipca12m) {
    }

    /** MERC-GES-2. */
    public record Evento(String simbolo, String tipo, LocalDate data, String fator) {
    }
}
