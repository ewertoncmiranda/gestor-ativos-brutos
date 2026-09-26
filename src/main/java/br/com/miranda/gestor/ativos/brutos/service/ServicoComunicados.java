package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.CategoriaComunicado;
import br.com.miranda.gestor.ativos.brutos.external.ComunicadoCvmEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.ComunicadoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.LinhaDoTempoComunicadosDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.NewsletterComunicadosDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.NewsletterComunicadosDTO.EmpresaNaEdicao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioComunicadoCvm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comunicados oficiais da CVM (base IPE) para o painel: a linha do tempo de um
 * ticker e a edição semanal da newsletter da carteira (contrato {@code infra#CTR-10}).
 *
 * <p>Só lê. A carga é do {@code etl-fundamentos-cvm --comunicados}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ServicoComunicados {

    public static final String FONTE = "CVM - Dados Abertos (IPE)";
    public static final String AVISO =
            "Informativo. Metadados públicos da CVM; o conteúdo está no documento oficial. "
                    + "Não é recomendação de investimento.";

    static final int TAMANHO_PADRAO = 20;
    static final int TAMANHO_MAXIMO = 100;
    private static final LocalDate INICIO_ABERTO = LocalDate.of(2000, 1, 1);
    private static final LocalDate FIM_ABERTO = LocalDate.of(9999, 12, 31);

    private final RepositorioComunicadoCvm repositorio;

    public LinhaDoTempoComunicadosDTO buscarPorSimbolo(
            String simbolo,
            List<String> categorias,
            LocalDate desde,
            LocalDate ate,
            Integer pagina,
            Integer tamanho) {

        String normalizado = normalizarSimbolo(simbolo);
        List<CategoriaComunicado> filtro = resolverCategorias(categorias);
        LocalDate inicio = desde != null ? desde : INICIO_ABERTO;
        LocalDate fim = ate != null ? ate : FIM_ABERTO;
        validarPeriodo(inicio, fim);
        int numeroPagina = pagina != null ? pagina : 0;
        int tamanhoPagina = tamanho != null ? tamanho : TAMANHO_PADRAO;
        if (numeroPagina < 0) {
            throw new IllegalArgumentException("pagina deve ser >= 0");
        }
        if (tamanhoPagina < 1 || tamanhoPagina > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("tamanho deve estar entre 1 e " + TAMANHO_MAXIMO);
        }

        Page<ComunicadoCvmEntity> encontrados = repositorio.buscarPorSimbolo(
                normalizado, nomes(filtro), inicio, fim, PageRequest.of(numeroPagina, tamanhoPagina));

        return LinhaDoTempoComunicadosDTO.builder()
                .simbolo(normalizado)
                .categorias(nomes(filtro))
                .desde(desde)
                .ate(ate)
                .pagina(numeroPagina)
                .tamanho(tamanhoPagina)
                .total(encontrados.getTotalElements())
                .totalPaginas(encontrados.getTotalPages())
                .dadosAte(repositorio.dataEntregaMaisRecente().orElse(null))
                .fonte(FONTE)
                .aviso(AVISO)
                .comunicados(encontrados.getContent().stream().map(ComunicadoDTO::de).toList())
                .build();
    }

    /**
     * Edição da newsletter. Período: {@code desde}/{@code ate} se vierem; senão
     * a semana ISO pedida; senão a semana do documento mais recente - e não a
     * semana corrente, que costuma estar vazia porque a CVM republica a base
     * com cerca de uma semana de atraso.
     */
    public NewsletterComunicadosDTO newsletter(
            String semana, LocalDate desde, LocalDate ate, List<String> categorias) {

        List<CategoriaComunicado> filtro = resolverCategorias(categorias);
        Optional<LocalDate> dadosAte = repositorio.dataEntregaMaisRecente();

        LocalDate inicio;
        LocalDate fim;
        String semanaIso = null;
        if (desde != null || ate != null) {
            if (desde == null || ate == null) {
                throw new IllegalArgumentException("Informe desde e ate juntos, ou use semana");
            }
            inicio = desde;
            fim = ate;
            validarPeriodo(inicio, fim);
        } else {
            LocalDate referencia = semana != null && !semana.isBlank()
                    ? segundaDaSemana(semana)
                    : dadosAte.orElse(LocalDate.now());
            inicio = referencia.with(DayOfWeek.MONDAY);
            fim = inicio.plusDays(6);
            semanaIso = formatarSemana(inicio);
        }

        Map<String, List<String>> simbolosPorCnpj = new LinkedHashMap<>();
        for (Object[] linha : repositorio.tickersMonitorados()) {
            simbolosPorCnpj.computeIfAbsent(String.valueOf(linha[1]), c -> new ArrayList<>())
                    .add(String.valueOf(linha[0]));
        }

        List<ComunicadoCvmEntity> documentos = simbolosPorCnpj.isEmpty()
                ? List.of()
                : repositorio.findByCnpjInAndCategoriaInAndDataEntregaBetween(
                        simbolosPorCnpj.keySet(), nomes(filtro), inicio, fim);

        List<EmpresaNaEdicao> empresas = agruparPorTicker(documentos, simbolosPorCnpj);

        return NewsletterComunicadosDTO.builder()
                .semana(semanaIso)
                .semanaAnterior(semanaIso != null ? formatarSemana(inicio.minusWeeks(1)) : null)
                .semanaSeguinte(semanaIso != null ? formatarSemana(inicio.plusWeeks(1)) : null)
                .desde(inicio)
                .ate(fim)
                .categorias(nomes(filtro))
                .totalDocumentos(documentos.size())
                .dadosAte(dadosAte.orElse(null))
                .fonte(FONTE)
                .aviso(AVISO)
                .empresas(empresas)
                .build();
    }

    /**
     * Um bloco por ticker. Documento de companhia com dois tickers monitorados
     * aparece nos dois blocos: quem lê a newsletter pensa em ticker.
     */
    static List<EmpresaNaEdicao> agruparPorTicker(
            List<ComunicadoCvmEntity> documentos, Map<String, List<String>> simbolosPorCnpj) {

        Map<String, List<ComunicadoCvmEntity>> porSimbolo = new TreeMap<>();
        for (ComunicadoCvmEntity documento : documentos) {
            for (String simbolo : simbolosPorCnpj.getOrDefault(documento.getCnpj(), List.of())) {
                porSimbolo.computeIfAbsent(simbolo, s -> new ArrayList<>()).add(documento);
            }
        }

        Comparator<ComunicadoCvmEntity> relevancia = Comparator
                .comparingInt((ComunicadoCvmEntity c) -> prioridade(c.getCategoria()))
                .thenComparing(ComunicadoCvmEntity::getDataEntrega, Comparator.reverseOrder())
                .thenComparing(ComunicadoCvmEntity::getProtocoloCvm, Comparator.reverseOrder());

        return porSimbolo.entrySet().stream()
                .map(entrada -> {
                    List<ComunicadoCvmEntity> ordenados = entrada.getValue().stream()
                            .sorted(relevancia)
                            .toList();
                    Map<String, Long> porCategoria = ordenados.stream().collect(Collectors.groupingBy(
                            ComunicadoCvmEntity::getCategoria, LinkedHashMap::new, Collectors.counting()));
                    return EmpresaNaEdicao.builder()
                            .simbolo(entrada.getKey())
                            .total(ordenados.size())
                            .porCategoria(porCategoria)
                            .comunicados(ordenados.stream().map(ComunicadoDTO::de).toList())
                            .build();
                })
                .sorted(Comparator
                        .comparingInt((EmpresaNaEdicao e) -> prioridade(e.getComunicados().get(0).getCategoria()))
                        .thenComparing(EmpresaNaEdicao::getTotal, Comparator.reverseOrder())
                        .thenComparing(EmpresaNaEdicao::getSimbolo))
                .toList();
    }

    static LocalDate segundaDaSemana(String semana) {
        try {
            return LocalDate.parse(semana.trim().toUpperCase(Locale.ROOT) + "-1",
                    DateTimeFormatter.ISO_WEEK_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Semana inválida: '" + semana + "'. Use o formato ISO, ex.: 2026-W39");
        }
    }

    static String formatarSemana(LocalDate data) {
        return String.format("%d-W%02d",
                data.get(IsoFields.WEEK_BASED_YEAR), data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private static List<CategoriaComunicado> resolverCategorias(List<String> categorias) {
        if (categorias == null || categorias.stream().allMatch(c -> c == null || c.isBlank())) {
            return CategoriaComunicado.padrao();
        }
        return categorias.stream()
                .filter(c -> c != null && !c.isBlank())
                .flatMap(c -> Arrays.stream(c.split(",")))
                .map(CategoriaComunicado::deParametro)
                .distinct()
                .sorted(Comparator.comparingInt(CategoriaComunicado::getPrioridade))
                .toList();
    }

    private static List<String> nomes(List<CategoriaComunicado> categorias) {
        return categorias.stream().map(Enum::name).toList();
    }

    private static int prioridade(String categoria) {
        CategoriaComunicado conhecida = CategoriaComunicado.doBanco(categoria);
        return conhecida != null ? conhecida.getPrioridade() : Integer.MAX_VALUE;
    }

    private static void validarPeriodo(LocalDate desde, LocalDate ate) {
        if (desde.isAfter(ate)) {
            throw new IllegalArgumentException("desde (" + desde + ") é posterior a ate (" + ate + ")");
        }
    }

    private static String normalizarSimbolo(String simbolo) {
        String normalizado = simbolo == null ? "" : simbolo.trim().toUpperCase(Locale.ROOT);
        if (normalizado.isEmpty()) {
            throw new IllegalArgumentException("simbolo é obrigatório");
        }
        return normalizado;
    }
}
