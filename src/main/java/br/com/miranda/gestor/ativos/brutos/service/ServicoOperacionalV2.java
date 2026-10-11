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
                           String posicao, String evento, String risco, String eventoRecente, String operacao,
                           String bloqueio, String scoreNaoCalibrado, String evidencia, Integer pagina, Integer tamanho) {
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
                .filter(s -> filtrarOperacao(s, operacao))
                .filter(s -> filtrarBooleano(!motivosBloqueio(s.payload()).isEmpty(), bloqueio, "SIM", "NAO"))
                .filter(s -> filtrarBooleano("NAO_CALIBRADA".equals(s.confiancaFaixa()), scoreNaoCalibrado, "SIM", "NAO"))
                .filter(s -> filtrarBooleano(s.ultimoEventoTipo() != null, eventoRecente, "SIM", "NAO"))
                .filter(s -> filtrarBooleano(!"NAO_CALIBRADA".equals(s.confiancaFaixa()), evidencia, "SUFICIENTE", "INSUFICIENTE"))
                .filter(s -> filtrarRisco(s, risco))
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
                opcoes(snapshots, Snapshot::faixaLiquidez),
                opcoes(snapshots, Snapshot::setor),
                opcoes(snapshots, Snapshot::sinalPrincipal),
                opcoes(snapshots, Snapshot::confiancaFaixa),
                opcoes(snapshots, Snapshot::posicaoStatus),
                opcoes(snapshots, Snapshot::ultimoEventoTipo),
                opcoesDerivadas(snapshots, ServicoOperacionalV2::risco),
                opcoesDerivadas(snapshots, s -> motivosBloqueio(s.payload()).isEmpty() ? "NAO" : "SIM"),
                opcoesDerivadas(snapshots, s -> s.ultimoEventoTipo() == null ? "NAO" : "SIM"),
                opcoesDerivadas(snapshots, ServicoOperacionalV2::operacaoAtual),
                opcoesDerivadas(snapshots, s -> "NAO_CALIBRADA".equals(s.confiancaFaixa()) ? "SIM" : "NAO"),
                opcoesDerivadas(snapshots, s -> "NAO_CALIBRADA".equals(s.confiancaFaixa()) ? "INSUFICIENTE" : "SUFICIENTE"),
                avisos);
    }

    public Alertas alertas(Integer limite) {
        int lim = limite == null ? TAMANHO_PADRAO : Math.max(1, Math.min(limite, RepositorioOperacionalV2.LIMITE_MAXIMO));
        List<Snapshot> atuais = snapshotsAtuais();
        List<Alerta> todos = new ArrayList<>();
        Snapshot estadoSistema = atuais.stream().filter(s -> "BLOQUEADO".equals(s.statusOperacional())).findFirst().orElse(null);
        if (estadoSistema != null) {
            todos.add(new Alerta("sistema:BLOQUEADO:" + estadoSistema.dataPregao(), true, "CRITICO", "SISTEMA",
                    null, "Sistema operacional bloqueado", estadoSistema.dataPregao(), estadoSistema.atualizadoEm(),
                    estadoSistema.payload()));
        }
        for (Snapshot snapshot : atuais) {
            JsonNode motivos = snapshot.payload() == null ? null : snapshot.payload().path("motivosBloqueio");
            if (motivos != null && motivos.isArray() && !motivos.isEmpty()) {
                JsonNode primeiro = motivos.get(0);
                String codigo = primeiro.path("codigo").asText("BLOQUEIO_OPERACIONAL");
                boolean dado = codigo.contains("DADO") || codigo.contains("FUNDAMENTO");
                todos.add(new Alerta(snapshot.simbolo() + ":" + codigo + ":" + snapshot.dataPregao(), true,
                        dado ? "ATENCAO" : "CRITICO", dado ? "DADO" : "RISCO", snapshot.simbolo(),
                        primeiro.path("descricao").asText(primeiro.path("detalhe").asText("Bloqueio operacional atual")),
                        snapshot.dataPregao(), snapshot.atualizadoEm(), snapshot.payload()));
            }
            if ("ABERTA".equals(snapshot.posicaoStatus()) || "PENDENTE".equals(snapshot.posicaoStatus())) {
                todos.add(new Alerta(snapshot.simbolo() + ":POSICAO_" + snapshot.posicaoStatus() + ":" + snapshot.dataPregao(),
                        true, "INFO", "RISCO", snapshot.simbolo(),
                        "Posição simulada " + snapshot.posicaoStatus().toLowerCase(Locale.ROOT), snapshot.dataPregao(),
                        snapshot.atualizadoEm(), snapshot.payload()));
            }
        }
        todos.sort(Comparator.comparingInt((Alerta a) -> pesoSeveridade(a.severidade())).reversed()
                .thenComparing(Alerta::dataEvento, Comparator.nullsLast(Comparator.reverseOrder())));
        List<Alerta> itens = todos.stream().limit(lim).toList();
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
                s.confiancaFaixa(), s.posicaoStatus(), s.ultimoEventoTipo(), motivosBloqueio(s.payload()), s.atualizadoEm());
    }

    private static List<JsonNode> motivosBloqueio(JsonNode payload) {
        JsonNode motivos = payload == null ? null : payload.path("motivosBloqueio");
        if (motivos == null || !motivos.isArray()) return List.of();
        List<JsonNode> saida = new ArrayList<>();
        motivos.forEach(saida::add);
        return List.copyOf(saida);
    }

    private static boolean filtrarOperacao(Snapshot snapshot, String filtro) {
        String valor = normalizarToken(filtro, "operacao");
        if (valor == null) return true;
        if ("SEM_OPERACAO".equals(valor)) return snapshot.posicaoStatus() == null;
        if ("ABERTA".equals(valor)) return "ABERTA".equals(snapshot.posicaoStatus()) || "PENDENTE".equals(snapshot.posicaoStatus());
        return igual(snapshot.posicaoStatus(), valor);
    }

    private static boolean filtrarBooleano(boolean condicao, String filtro, String valorPositivo) {
        return filtrarBooleano(condicao, filtro, valorPositivo, null);
    }

    private static boolean filtrarBooleano(boolean condicao, String filtro, String valorPositivo,
                                           String valorNegativo) {
        String valor = normalizarToken(filtro, "filtro");
        if (valor == null) return true;
        if (valorPositivo.equals(valor)) return condicao;
        if (valorNegativo != null && valorNegativo.equals(valor)) return !condicao;
        return true;
    }

    private static boolean filtrarRisco(Snapshot snapshot, String filtro) {
        String valor = normalizarToken(filtro, "risco");
        if (valor == null) return true;
        return risco(snapshot).equals(valor);
    }

    private static String risco(Snapshot snapshot) {
        JsonNode riscos = snapshot.payload() == null ? null : snapshot.payload().path("tese").path("riscos");
        String maior = "BAIXO";
        if (riscos != null && riscos.isArray()) {
            for (JsonNode risco : riscos) {
                String severidade = risco.path("severidade").asText("");
                if ("ALTA".equals(severidade)) { maior = "ALTO"; break; }
                if ("MEDIA".equals(severidade)) maior = "MEDIO";
            }
        }
        return maior;
    }

    private EventoTimeline evento(TimelineLinha e) {
        return new EventoTimeline(e.eventId(), e.simbolo(), e.dataEvento(), e.versaoRegra(), e.tipo(),
                e.severidade(), e.titulo(), e.payload(), e.criadoEm());
    }

    private static List<OpcaoFiltro> opcoes(List<Snapshot> snapshots, Function<Snapshot, String> extrator) {
        return snapshots.stream().map(extrator).filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new OpcaoFiltro(e.getKey(), e.getValue())).toList();
    }

    private static List<OpcaoFiltro> opcoesDerivadas(List<Snapshot> snapshots, Function<Snapshot, String> extrator) {
        return opcoes(snapshots, extrator);
    }

    private static String operacaoAtual(Snapshot snapshot) {
        if ("ABERTA".equals(snapshot.posicaoStatus()) || "PENDENTE".equals(snapshot.posicaoStatus())) return "ABERTA";
        return "FECHADA".equals(snapshot.posicaoStatus()) ? "FECHADA" : "SEM_OPERACAO";
    }

    private static boolean igual(String valor, String filtro) {
        return filtro == null || (valor != null && valor.equalsIgnoreCase(filtro));
    }

    private static int pesoSeveridade(String severidade) {
        return switch (severidade == null ? "" : severidade) {
            case "CRITICO" -> 3;
            case "ATENCAO" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
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
