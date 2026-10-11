package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.Alerta;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.Alertas;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.Consenso;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.EventoTimeline;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.Filtros;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.OpcaoFiltro;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.Tese;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.TickerCard;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.TickerDetalhe;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.Tickers;
import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalV2DTO.Timeline;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacionalV2;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacionalV2.Snapshot;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOperacionalV2.TimelineLinha;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Servico de leitura da Operabilidade V2. Todos os metodos sao GET-only por desenho: delegam apenas para
 * consultas JDBC e montam DTOs estaveis para o front.
 */
@Service
@RequiredArgsConstructor
public class ServicoOperacionalV2 {

    private static final int PAGINA_PADRAO = 0;
    private static final int TAMANHO_PADRAO = 50;
    private static final Pattern SIMBOLO = Pattern.compile("^[A-Z0-9]{4,12}$");
    private static final Pattern TOKEN = Pattern.compile("^[A-Z0-9_\\-]{1,40}$");
    private static final List<String> SEM_V2 = List.of(
            "Sem dados de Operabilidade V2. A infra/migration ou o gerar-insights ainda não materializou snapshots.");

    private final RepositorioOperacionalV2 repositorio;

    public Tickers tickers(String status, String liquidez, String setor, String sinal, String confianca,
                           String posicao, String evento, Integer pagina, Integer tamanho) {
        int pag = pagina == null ? PAGINA_PADRAO : Math.max(0, pagina);
        int tam = tamanho == null ? TAMANHO_PADRAO : Math.max(1, Math.min(tamanho, RepositorioOperacionalV2.LIMITE_MAXIMO));
        List<Snapshot> filtrados = snapshotsAtuais().stream()
                .filter(s -> igual(s.statusOperacional(), normalizarToken(status, "status")))
                .filter(s -> igual(s.faixaLiquidez(), normalizarToken(liquidez, "liquidez")))
                .filter(s -> igual(s.setor(), normalizarTexto(setor)))
                .filter(s -> igual(s.sinalPrincipal(), normalizarToken(sinal, "sinal")))
                .filter(s -> igual(s.confiancaFaixa(), normalizarToken(confianca, "confianca")))
                .filter(s -> igual(s.posicaoStatus(), normalizarToken(posicao, "posicao")))
                .filter(s -> igual(s.ultimoEventoTipo(), normalizarToken(evento, "evento")))
                .toList();
        int total = filtrados.size();
        List<TickerCard> itens = filtrados.stream().skip((long) pag * tam).limit(tam).map(this::card).toList();
        return new Tickers(OperacionalV2DTO.MODO, OperacionalV2DTO.AVISO, pag, tam, total, itens,
                total == 0 ? SEM_V2 : List.of());
    }

    public TickerDetalhe ticker(String simbolo) {
        String s = simbolo(simbolo);
        return repositorio.snapshot(s).map(this::detalhe).orElseGet(() -> new TickerDetalhe(
                OperacionalV2DTO.MODO, OperacionalV2DTO.AVISO, s, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, SEM_V2));
    }

    public Consenso consenso(String simbolo) {
        TickerDetalhe d = ticker(simbolo);
        return new Consenso(d.modo(), d.aviso(), d.simbolo(), d.dataPregao(), d.versaoRegra(), d.consenso(),
                d.consenso() == null ? avisoSemCampo("consenso", d.avisos()) : d.avisos());
    }

    public Tese tese(String simbolo) {
        TickerDetalhe d = ticker(simbolo);
        return new Tese(d.modo(), d.aviso(), d.simbolo(), d.dataPregao(), d.versaoRegra(), d.tese(),
                d.tese() == null ? avisoSemCampo("tese", d.avisos()) : d.avisos());
    }

    public Timeline timeline(String simbolo, String tipo, String severidade, LocalDate inicio, LocalDate fim,
                             Integer limite) {
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("inicio nao pode ser depois de fim");
        }
        String s = simbolo(simbolo);
        int lim = limite == null ? TAMANHO_PADRAO : Math.max(1, Math.min(limite, RepositorioOperacionalV2.LIMITE_MAXIMO));
        List<EventoTimeline> itens = repositorio.timeline(s, normalizarToken(tipo, "tipo"),
                normalizarToken(severidade, "severidade"), inicio, fim, lim).stream().map(this::evento).toList();
        return new Timeline(OperacionalV2DTO.MODO, OperacionalV2DTO.AVISO, s, lim, itens.size(), itens,
                itens.isEmpty() ? SEM_V2 : List.of());
    }

    public Filtros filtros() {
        List<Snapshot> snapshots = snapshotsAtuais();
        List<String> avisos = snapshots.isEmpty() ? SEM_V2 : List.of();
        return new Filtros(OperacionalV2DTO.MODO, OperacionalV2DTO.AVISO,
                opcoes(snapshots, Snapshot::statusOperacional),
                opcoes(snapshots, Snapshot::faixaLiquidez),
                opcoes(snapshots, Snapshot::setor),
                opcoes(snapshots, Snapshot::sinalPrincipal),
                opcoes(snapshots, Snapshot::confiancaFaixa),
                opcoes(snapshots, Snapshot::posicaoStatus),
                opcoes(snapshots, Snapshot::ultimoEventoTipo),
                avisos);
    }

    public Alertas alertas(Integer limite) {
        int lim = limite == null ? TAMANHO_PADRAO : Math.max(1, Math.min(limite, RepositorioOperacionalV2.LIMITE_MAXIMO));
        List<Alerta> itens = repositorio.alertas(lim).stream().map(this::alerta).toList();
        return new Alertas(OperacionalV2DTO.MODO, OperacionalV2DTO.AVISO, lim, itens.size(), itens,
                itens.isEmpty() ? SEM_V2 : List.of());
    }

    private List<Snapshot> snapshotsAtuais() {
        Map<String, Snapshot> porSimbolo = new LinkedHashMap<>();
        repositorio.snapshotsRecentes().stream()
                .sorted(Comparator.comparing(Snapshot::dataPregao, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(s -> porSimbolo.putIfAbsent(s.simbolo(), s));
        return new ArrayList<>(porSimbolo.values());
    }

    private TickerDetalhe detalhe(Snapshot s) {
        return new TickerDetalhe(OperacionalV2DTO.MODO, OperacionalV2DTO.AVISO, s.simbolo(), s.dataPregao(),
                s.versaoRegra(), s.statusOperacional(), s.faixaLiquidez(), s.setor(), s.sinalPrincipal(),
                s.sinalTecnico(), s.opiniaoIa(), s.confiancaFaixa(), s.posicaoStatus(), s.ultimoEventoTipo(),
                s.payload(), s.consenso(), s.tese(), s.atualizadoEm(), List.of());
    }

    private TickerCard card(Snapshot s) {
        return new TickerCard(s.simbolo(), s.dataPregao(), s.versaoRegra(), s.statusOperacional(),
                s.faixaLiquidez(), s.setor(), s.sinalPrincipal(), s.sinalTecnico(), s.opiniaoIa(),
                s.confiancaFaixa(), s.posicaoStatus(), s.ultimoEventoTipo(), s.atualizadoEm());
    }

    private EventoTimeline evento(TimelineLinha e) {
        return new EventoTimeline(e.eventId(), e.simbolo(), e.dataEvento(), e.versaoRegra(), e.tipo(),
                e.severidade(), e.titulo(), e.payload(), e.criadoEm());
    }

    private Alerta alerta(TimelineLinha e) {
        return new Alerta(e.eventId(), e.severidade(), categoria(e), e.simbolo(), e.titulo(), e.dataEvento(),
                e.criadoEm(), e.payload());
    }

    private static List<OpcaoFiltro> opcoes(List<Snapshot> snapshots, Function<Snapshot, String> extrator) {
        return snapshots.stream().map(extrator).filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new OpcaoFiltro(e.getKey(), e.getValue())).toList();
    }

    private static String categoria(TimelineLinha e) {
        JsonNode payload = e.payload();
        if (payload != null) {
            JsonNode categoria = payload.path("categoria");
            if (categoria.isTextual() && !categoria.asText().isBlank()) {
                return categoria.asText();
            }
        }
        return e.tipo();
    }

    private static boolean igual(String valor, String filtro) {
        return filtro == null || (valor != null && valor.equalsIgnoreCase(filtro));
    }

    private static String simbolo(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("simbolo obrigatorio");
        }
        String s = valor.trim().toUpperCase(Locale.ROOT);
        if (!SIMBOLO.matcher(s).matches()) {
            throw new IllegalArgumentException("simbolo invalido: " + valor);
        }
        return s;
    }

    private static String normalizarToken(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String v = valor.trim().toUpperCase(Locale.ROOT);
        if (!TOKEN.matcher(v).matches()) {
            throw new IllegalArgumentException(nome + " invalido: " + valor);
        }
        return v;
    }

    private static String normalizarTexto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static List<String> avisoSemCampo(String campo, List<String> avisosAtuais) {
        List<String> avisos = new ArrayList<>(avisosAtuais);
        avisos.add("Snapshot encontrado, mas sem bloco de " + campo + " materializado.");
        return avisos;
    }
}
