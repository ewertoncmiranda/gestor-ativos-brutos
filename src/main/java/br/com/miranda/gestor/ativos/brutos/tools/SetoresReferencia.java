package br.com.miranda.gestor.ativos.brutos.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Universo de referencia por setor, curado manualmente (nao vem de nenhuma
 * API) - os 3 papeis mais liquidos/representativos de cada setor na B3,
 * pra dar contexto de comparacao setorial sem depender de uma fonte externa
 * nova. E uma fotografia, nao um ranking vivo: precisa ser revisitada de
 * tempos em tempos, nao se atualiza sozinha.
 *
 * Reduzido de ~93 pra 30 (3 por setor, 10 setores) depois de esgotar a cota
 * mensal da BRAPI (15.000 req/mes, plano Gratuito) registrando o universo
 * inteiro de uma vez no primeiro ciclo. 3 por setor ainda permite comparar
 * (nao e so 1 ativo isolado), sem repetir o estouro de cota.
 */
public final class SetoresReferencia {

    private SetoresReferencia() {
    }

    public static final Map<String, List<String>> TICKERS_POR_SETOR = criarMapa();

    private static Map<String, List<String>> criarMapa() {
        Map<String, List<String>> mapa = new LinkedHashMap<>();

        mapa.put("Bancos e Servicos Financeiros", List.of("ITUB4", "BBDC4", "BBAS3"));
        mapa.put("Energia (Petroleo, Gas e Biocombustiveis)", List.of("PETR4", "PRIO3", "VBBR3"));
        mapa.put("Mineracao e Siderurgia", List.of("VALE3", "CSNA3", "GGBR4"));
        mapa.put("Varejo e Consumo", List.of("MGLU3", "LREN3", "RENT3"));
        mapa.put("Bens Industriais e Transporte", List.of("WEGE3", "EMBR3", "RAIL3"));
        mapa.put("Utilidades (Energia Eletrica e Saneamento)", List.of("ELET3", "CPFE3", "EQTL3"));
        mapa.put("Saude", List.of("RDOR3", "HAPV3", "FLRY3"));
        mapa.put("Tecnologia e Comunicacoes", List.of("TOTS3", "VIVT3", "TIMS3"));
        mapa.put("Imobiliario e Construcao", List.of("CYRE3", "MRVE3", "EZTC3"));
        mapa.put("Consumo Nao Ciclico e Agro", List.of("JBSS3", "BRFS3", "MRFG3"));

        // Collections.unmodifiableMap, nao Map.copyOf: preserva a ordem de
        // insercao do LinkedHashMap (Map.copyOf nao garante ordem nenhuma),
        // que e a ordem em que os setores aparecem na tela.
        return Collections.unmodifiableMap(mapa);
    }

    public static String setorDoTicker(String simbolo) {
        String alvo = simbolo.trim().toUpperCase();
        return TICKERS_POR_SETOR.entrySet().stream()
                .filter(entrada -> entrada.getValue().contains(alvo))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
