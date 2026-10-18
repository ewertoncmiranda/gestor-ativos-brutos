package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.CargaDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteEtl;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCargas;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cargas do ETL para o painel (Plano CARGA): le o que o ETL gravou
 * (CARGA-GES-1) e repassa pedidos de carga ao servico do ETL (CARGA-GES-2).
 * Valida o pedido antes de repassar; nao executa nem altera carga.
 */
@Service
public class ServicoCargas {

    static final int LIMITE_PADRAO = 20;
    static final int LIMITE_MAXIMO = 200;
    static final int PRIMEIRO_ANO = 2016;
    static final int MAXIMO_SIMBOLOS = 50;

    static final Set<String> TIPOS = Set.of("ROTINA", "COMPLETA", "PARCIAL");
    static final Set<String> STATUS = Set.of("PENDENTE", "EXECUTANDO", "SUCESSO", "SUCESSO_COM_ALERTA",
            "SUCESSO_PARCIAL", "FALHA", "INTERROMPIDA");
    /** Catalogo de etapas do contrato (infra V44 recusa qualquer outra). */
    static final List<String> ETAPAS = List.of("COTAHIST", "LIQUIDEZ", "PRONTIDAO", "QUALIDADE_OPCOES",
            "CONCILIACAO", "DFP", "TTM", "IPE", "DVA", "EVENTOS", "CHECAGENS");
    private static final Pattern SIMBOLO = Pattern.compile("^[A-Z0-9]{4,12}$");

    private final RepositorioCargas repositorio;
    private final ClienteEtl etl;

    public ServicoCargas(RepositorioCargas repositorio, ClienteEtl etl) {
        this.repositorio = repositorio;
        this.etl = etl;
    }

    // --- CARGA-GES-1: leitura ----------------------------------------------------------

    public List<CargaDTO.Execucao> listar(Integer limite, String status, LocalDateTime desde) {
        exigirSchema();
        int n = limite == null ? LIMITE_PADRAO : limite;
        if (n < 1 || n > LIMITE_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limite deve ficar entre 1 e " + LIMITE_MAXIMO);
        }
        String filtro = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (filtro != null && !STATUS.contains(filtro)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status desconhecido: " + status);
        }
        return repositorio.listar(n, filtro, desde);
    }

    public CargaDTO.Execucao buscar(String id) {
        exigirSchema();
        if (id == null || id.isBlank() || id.length() > 36) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id inválido");
        }
        return repositorio.buscar(id.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carga não encontrada"));
    }

    public List<CargaDTO.Ultima> ultimas() {
        exigirSchema();
        return repositorio.ultimas();
    }

    private void exigirSchema() {
        if (!repositorio.schemaDisponivel()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O banco ainda não tem o controle de cargas (migration V44)");
        }
    }

    // --- CARGA-GES-2: disparo ----------------------------------------------------------

    /** Resposta ao painel: status do ETL e corpo em camelCase. */
    public record RespostaDisparo(int status, Map<String, Object> corpo) {
    }

    public RespostaDisparo disparar(CargaDTO.Disparo pedido) {
        Map<String, Object> corpo = validar(pedido);
        ClienteEtl.Resposta resposta;
        try {
            resposta = etl.disparar(corpo);
        } catch (ClienteEtl.EtlIndisponivel e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O serviço de cargas (ETL) está fora do ar; tente de novo em instantes", e);
        }
        return new RespostaDisparo(resposta.status(), paraPainel(resposta.corpo()));
    }

    /** Corpo para o ETL (snake_case, sem campos vazios) a partir do pedido do painel. */
    Map<String, Object> validar(CargaDTO.Disparo pedido) {
        if (pedido == null || pedido.tipo() == null) {
            throw invalido("tipo é obrigatório (ROTINA, COMPLETA ou PARCIAL)");
        }
        String tipo = pedido.tipo().trim().toUpperCase(Locale.ROOT);
        if (!TIPOS.contains(tipo)) {
            throw invalido("tipo deve ser ROTINA, COMPLETA ou PARCIAL");
        }
        int anoAtual = Year.now().getValue();
        List<Integer> anos = pedido.anos() == null ? List.of() : pedido.anos().stream().distinct().sorted().toList();
        for (Integer ano : anos) {
            if (ano == null || ano < PRIMEIRO_ANO || ano > anoAtual) {
                throw invalido("anos devem ficar entre " + PRIMEIRO_ANO + " e " + anoAtual);
            }
        }
        List<String> simbolos = pedido.simbolos() == null ? List.of() : pedido.simbolos().stream()
                .map(s -> s == null ? "" : s.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        if (simbolos.size() > MAXIMO_SIMBOLOS) {
            throw invalido("no máximo " + MAXIMO_SIMBOLOS + " símbolos por carga");
        }
        for (String s : simbolos) {
            if (!SIMBOLO.matcher(s).matches()) {
                throw invalido("símbolo inválido: " + s);
            }
        }
        List<String> etapas = pedido.etapas() == null ? List.of() : pedido.etapas().stream()
                .map(e -> e == null ? "" : e.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        for (String e : etapas) {
            if (!ETAPAS.contains(e)) {
                throw invalido("etapa desconhecida: " + e);
            }
        }
        if ("PARCIAL".equals(tipo) && etapas.isEmpty() && anos.isEmpty() && simbolos.isEmpty()) {
            throw invalido("carga PARCIAL precisa de etapas, anos ou símbolos");
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("tipo", tipo);
        if (!anos.isEmpty()) corpo.put("anos", anos);
        if (!simbolos.isEmpty()) corpo.put("simbolos", simbolos);
        if (!etapas.isEmpty()) corpo.put("etapas", etapas);
        if (Boolean.TRUE.equals(pedido.forcar())) corpo.put("forcar", true);
        corpo.put("solicitada_por", "PAINEL");
        return corpo;
    }

    /** {id, status} no 202; {execucaoEmAndamento} no 409; outros erros com a mensagem do ETL. */
    static Map<String, Object> paraPainel(JsonNode corpo) {
        Map<String, Object> saida = new LinkedHashMap<>();
        if (corpo == null || !corpo.isObject()) {
            return saida;
        }
        corpo.fields().forEachRemaining(campo -> saida.put(camelCase(campo.getKey()), valor(campo.getValue())));
        return saida;
    }

    private static Object valor(JsonNode no) {
        if (no.isNull()) return null;
        if (no.isTextual()) return no.asText();
        if (no.isIntegralNumber()) return no.asLong();
        if (no.isNumber()) return no.decimalValue();
        if (no.isBoolean()) return no.asBoolean();
        return no;
    }

    static String camelCase(String snake) {
        StringBuilder saida = new StringBuilder();
        boolean maiuscula = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                maiuscula = true;
            } else {
                saida.append(maiuscula ? Character.toUpperCase(c) : c);
                maiuscula = false;
            }
        }
        return saida.toString();
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
