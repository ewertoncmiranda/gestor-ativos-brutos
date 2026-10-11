package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contratos de leitura da Operabilidade V2. As tabelas sao escritas por infra/gerar-insights; este backend
 * apenas le snapshots e timeline para o cockpit e para o front. Nada aqui cria ordem, recomendacao ou efeito
 * colateral.
 */
public final class OperacionalV2DTO {

    public static final String MODO = OperacionalDTO.MODO;
    public static final String AVISO = OperacionalDTO.AVISO;

    private OperacionalV2DTO() {
    }

    public record TickerCard(String simbolo, LocalDate dataPregao, String versaoRegra, String statusOperacional,
                             String faixaLiquidez, String setor, String sinalPrincipal, JsonNode sinalTecnico,
                             String opiniaoIa, String confiancaFaixa, String posicaoStatus,
                             String ultimoEventoTipo, List<JsonNode> motivosBloqueio, LocalDateTime atualizadoEm) {
    }

    public record TickerDetalhe(String modo, String aviso, String simbolo, LocalDate dataPregao, String versaoRegra,
                                String statusOperacional, String faixaLiquidez, String setor, String sinalPrincipal,
                                JsonNode sinalTecnico, String opiniaoIa, String confiancaFaixa, String posicaoStatus,
                                String ultimoEventoTipo, JsonNode payload, JsonNode consenso, JsonNode tese,
                                LocalDateTime atualizadoEm, List<String> avisos) {
    }

    public record Tickers(String modo, String aviso, int pagina, int tamanho, int total, List<TickerCard> itens,
                          List<String> avisos) {
    }

    public record Consenso(String modo, String aviso, String simbolo, LocalDate dataPregao, String versaoRegra,
                           JsonNode consenso, List<String> avisos) {
    }

    public record Tese(String modo, String aviso, String simbolo, LocalDate dataPregao, String versaoRegra,
                       JsonNode tese, List<String> avisos) {
    }

    public record EventoTimeline(String eventId, String simbolo, LocalDate dataEvento, String versaoRegra,
                                 String tipo, String severidade, String titulo, JsonNode payload,
                                 LocalDateTime criadoEm) {
    }

    public record Timeline(String modo, String aviso, String simbolo, int limite, int total,
                           List<EventoTimeline> itens, List<String> avisos) {
    }

    public record OpcaoFiltro(String valor, long total) {
    }

    public record Filtros(String modo, String aviso, List<OpcaoFiltro> statusOperacional,
                          List<OpcaoFiltro> faixaLiquidez, List<OpcaoFiltro> liquidez, List<OpcaoFiltro> setor,
                          List<OpcaoFiltro> sinalPrincipal, List<OpcaoFiltro> confiancaFaixa,
                          List<OpcaoFiltro> posicaoStatus, List<OpcaoFiltro> ultimoEventoTipo,
                          List<OpcaoFiltro> risco, List<OpcaoFiltro> bloqueio, List<OpcaoFiltro> eventoRecente,
                          List<OpcaoFiltro> operacao, List<OpcaoFiltro> scoreNaoCalibrado,
                          List<OpcaoFiltro> evidencia,
                          List<String> avisos) {
    }

    public record Alerta(String id, boolean ativo, String severidade, String categoria, String simbolo, String titulo,
                         LocalDate dataEvento, LocalDateTime createdAt, JsonNode payload) {
    }

    public record Alertas(String modo, String aviso, int limite, int total, List<Alerta> itens,
                          List<String> avisos) {
    }
}
