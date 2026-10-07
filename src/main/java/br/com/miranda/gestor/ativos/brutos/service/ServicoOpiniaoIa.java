package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.OpiniaoAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOpiniaoIa;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioOpiniaoIa.Linha;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Opiniao por horizonte (TASK-OPI-1): do pregao mais recente do ativo, um
 * item por horizonte - a leitura do modelo local quando existe para aquele
 * (ativo, pregao, horizonte), senao a baseline da regra. Somente leitura.
 */
@Service
@RequiredArgsConstructor
public class ServicoOpiniaoIa {

    /** Valor de {@code opiniao_ia.modelo} das linhas de baseline por regra. */
    static final String MODELO_REGRA = "regra";

    private final RepositorioOpiniaoIa repositorio;

    public OpiniaoAtivoDTO opiniao(String simbolo) {
        String s = simbolo.toUpperCase();
        List<OpiniaoAtivoDTO.Horizonte> horizontes = escolherPorHorizonte(repositorio.doUltimoPregao(s));
        return new OpiniaoAtivoDTO(s, horizontes.isEmpty() ? null : horizontes.get(0).dataPregao(),
                OpiniaoAtivoDTO.AVISO, horizontes);
    }

    /** Modelo antes de regra; entre linhas do mesmo tipo, a mais recente. Horizontes em ordem crescente. */
    static List<OpiniaoAtivoDTO.Horizonte> escolherPorHorizonte(List<Linha> linhas) {
        Comparator<Linha> preferencia = Comparator
                .comparing((Linha l) -> MODELO_REGRA.equalsIgnoreCase(l.modelo()))
                .thenComparing(Linha::criadoEm, Comparator.nullsLast(Comparator.reverseOrder()));
        Map<Integer, Linha> escolhida = new LinkedHashMap<>();
        linhas.stream()
                .sorted(Comparator.comparingInt(Linha::horizontePregoes).thenComparing(preferencia))
                .forEach(l -> escolhida.putIfAbsent(l.horizontePregoes(), l));
        return escolhida.values().stream().map(ServicoOpiniaoIa::paraHorizonte).toList();
    }

    private static OpiniaoAtivoDTO.Horizonte paraHorizonte(Linha l) {
        return new OpiniaoAtivoDTO.Horizonte(l.dataPregao(), l.horizontePregoes(), l.opiniao(), l.risco(),
                lista(l.justificativa(), n -> new OpiniaoAtivoDTO.Justificativa(texto(n, "evidencia_id"), texto(n, "leitura"))),
                lista(l.invalida(), ServicoOpiniaoIa::textoDoNo),
                lista(l.dadosAusentes(), ServicoOpiniaoIa::textoDoNo),
                lista(l.evidencias(), n -> new OpiniaoAtivoDTO.Evidencia(texto(n, "id"), texto(n, "rotulo"),
                        texto(n, "valor"), n.hasNonNull("direcao") ? n.get("direcao").asInt() : null)),
                l.modelo(), l.origem());
    }

    private static <T> List<T> lista(JsonNode no, Function<JsonNode, T> conversor) {
        List<T> saida = new ArrayList<>();
        if (no != null && no.isArray()) no.forEach(item -> saida.add(conversor.apply(item)));
        return saida;
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static String textoDoNo(JsonNode no) {
        return no.isNull() ? null : no.asText();
    }
}
