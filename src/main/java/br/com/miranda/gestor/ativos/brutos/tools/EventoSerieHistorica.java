package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaHistoricoAcoesDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EventoSerieHistorica {
    private EventoSerieHistorica() {}

    public static String serializar(RespostaHistoricoAcoesDTO serie) {
        if (serie == null || serie.results() == null || serie.results().isEmpty()) {
            throw new IllegalArgumentException("Serie historica sem resultados");
        }
        try {
            // requestedAt/took não mudam a identidade do conteúdo.
            String identidade = ConversorJson.paraJson(serie.results());
            String chave = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identidade.getBytes(StandardCharsets.UTF_8)));
            Map<String, Object> evento = new LinkedHashMap<>();
            evento.put("schemaVersion", "1.0");
            evento.put("dedupKey", chave);
            evento.put("results", serie.results());
            evento.put("requestedAt", serie.requestedAt());
            evento.put("took", serie.took());
            return ConversorJson.paraJson(evento);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
