package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO.Resumo;
import br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO.Salto;
import br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO.Vela;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPregoes;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPregoes.Pregao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Velas de qualquer periodo desde 2016 (CTR-15), por dia, semana ou mes.
 *
 * <p>Regras: um pregao por data (o codigo canonico ganha do antigo na mesma
 * data; o COTAHIST ganha da BRAPI); semana e mes agrupam abertura do
 * primeiro pregao, fechamento do ultimo, maior maxima e menor minima. Salto
 * e medido no diario, antes de agrupar, porque e la que ele acontece.
 */
@Service
@RequiredArgsConstructor
public class ServicoPregoes {

    public static final Set<String> INTERVALOS = Set.of("dia", "semana", "mes");
    public static final LocalDate INICIO_DOS_DADOS = LocalDate.of(2016, 1, 1);
    static final BigDecimal LIMIAR_SALTO = new BigDecimal("0.40");
    public static final String AVISO =
            "Preço bruto do pregão (COTAHIST da B3; os dias mais recentes, da BRAPI até o COTAHIST chegar). "
                    + "Proventos não estão descontados e desdobramentos aparecem como saltos, marcados à parte.";

    private final RepositorioPregoes repositorio;
    private final RepositorioIdentidadeAtivo repositorioIdentidade;

    public PregoesDTO montar(String simboloPedido, LocalDate de, LocalDate ate, String intervalo) {
        String simbolo = repositorioIdentidade.canonico(simboloPedido.trim().toUpperCase());
        LocalDate fim = ate != null ? ate : LocalDate.now();
        LocalDate inicio = de != null ? de : fim.minusYears(1);
        String tipo = intervalo == null ? "dia" : intervalo.toLowerCase();
        if (!INTERVALOS.contains(tipo)) {
            throw new IllegalArgumentException("intervalo deve ser dia, semana ou mes");
        }
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("de deve ser anterior ou igual a ate");
        }
        if (inicio.isBefore(INICIO_DOS_DADOS)) {
            inicio = INICIO_DOS_DADOS;
        }

        List<String> codigos = repositorio.codigosDoPapel(simbolo);
        List<Pregao> diarios = unificar(simbolo, repositorio.oficiais(codigos, inicio, fim));
        LocalDate ultimoOficial = diarios.isEmpty() ? inicio.minusDays(1) : diarios.get(diarios.size() - 1).data();
        diarios.addAll(repositorio.recentesBrapi(simbolo, ultimoOficial, fim));

        List<Salto> saltos = saltos(diarios);
        return new PregoesDTO(simbolo, codigos, tipo, inicio, fim, agrupar(diarios, tipo), resumo(diarios, saltos),
                saltos, AVISO);
    }

    /** Um pregao por data; na data em que dois codigos negociaram, o canonico vence. */
    static List<Pregao> unificar(String canonico, List<Pregao> pregoes) {
        Map<LocalDate, Pregao> porData = new TreeMap<>();
        for (Pregao p : pregoes) {
            Pregao atual = porData.get(p.data());
            if (atual == null || (!canonico.equals(atual.codigo()) && canonico.equals(p.codigo()))) {
                porData.put(p.data(), p);
            }
        }
        return new ArrayList<>(porData.values());
    }

    static List<Salto> saltos(List<Pregao> diarios) {
        List<Salto> saltos = new ArrayList<>();
        for (int i = 1; i < diarios.size(); i++) {
            BigDecimal anterior = diarios.get(i - 1).fechamento();
            BigDecimal abertura = diarios.get(i).abertura();
            if (anterior == null || abertura == null || anterior.signum() == 0) {
                continue;
            }
            BigDecimal variacao = abertura.divide(anterior, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
            if (variacao.abs().compareTo(LIMIAR_SALTO) >= 0) {
                saltos.add(new Salto(diarios.get(i).data(), anterior, abertura, variacao));
            }
        }
        return saltos;
    }

    static List<Vela> agrupar(List<Pregao> diarios, String intervalo) {
        Function<LocalDate, LocalDate> chave = switch (intervalo) {
            case "semana" -> d -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "mes" -> d -> d.withDayOfMonth(1);
            default -> d -> d;
        };
        Map<LocalDate, List<Pregao>> grupos = new LinkedHashMap<>();
        for (Pregao p : diarios) {
            grupos.computeIfAbsent(chave.apply(p.data()), k -> new ArrayList<>()).add(p);
        }
        List<Vela> velas = new ArrayList<>();
        for (List<Pregao> grupo : grupos.values()) {
            Pregao primeiro = grupo.get(0);
            Pregao ultimo = grupo.get(grupo.size() - 1);
            BigDecimal maxima = grupo.stream().map(Pregao::maxima).filter(java.util.Objects::nonNull)
                    .max(BigDecimal::compareTo).orElse(null);
            BigDecimal minima = grupo.stream().map(Pregao::minima).filter(java.util.Objects::nonNull)
                    .min(BigDecimal::compareTo).orElse(null);
            Long volume = grupo.stream().map(Pregao::volume).filter(java.util.Objects::nonNull)
                    .reduce(Long::sum).orElse(null);
            Long numeroNegocios = grupo.stream().map(Pregao::numeroNegocios).filter(java.util.Objects::nonNull)
                    .mapToLong(Integer::longValue).sum();
            if (grupo.stream().allMatch(p -> p.numeroNegocios() == null)) numeroNegocios = null;
            String fonte = grupo.stream().map(Pregao::fonte).distinct().count() > 1 ? "MISTA" : primeiro.fonte();
            velas.add(new Vela(primeiro.data(), ultimo.data(), ultimo.codigo(), primeiro.abertura(), maxima, minima,
                    ultimo.fechamento(), volume, numeroNegocios, grupo.size(), fonte));
        }
        return velas;
    }

    static Resumo resumo(List<Pregao> diarios, List<Salto> saltos) {
        if (diarios.isEmpty()) {
            return new Resumo(0, null, null, null, null, null, null, null, null, null, false);
        }
        Pregao primeiro = diarios.get(0);
        Pregao ultimo = diarios.get(diarios.size() - 1);
        Pregao comMaxima = diarios.stream().filter(p -> p.maxima() != null)
                .max((a, b) -> a.maxima().compareTo(b.maxima())).orElse(primeiro);
        Pregao comMinima = diarios.stream().filter(p -> p.minima() != null)
                .min((a, b) -> a.minima().compareTo(b.minima())).orElse(primeiro);
        BigDecimal variacao = primeiro.abertura() == null || primeiro.abertura().signum() == 0 ? null
                : ultimo.fechamento().divide(primeiro.abertura(), 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        return new Resumo(diarios.size(), primeiro.data(), ultimo.data(), primeiro.abertura(), ultimo.fechamento(),
                variacao, comMaxima.maxima(), comMaxima.data(), comMinima.minima(), comMinima.data(),
                !saltos.isEmpty());
    }
}
