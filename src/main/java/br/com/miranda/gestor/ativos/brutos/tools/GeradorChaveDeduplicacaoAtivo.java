package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.external.Ativo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Preenche schemaVersion/dedupKey de um Ativo antes de publicar na fila.
 * Extraido de ServicoAtivo pra ser reaproveitado por ServicoAtualizacaoCache -
 * os dois publicam na mesma fila e precisam gerar a chave do mesmo jeito.
 */
public final class GeradorChaveDeduplicacaoAtivo {

    public static final String VERSAO_PAYLOAD_ATIVO = "1.0";

    private GeradorChaveDeduplicacaoAtivo() {
    }

    public static void preencher(Ativo ativo) {
        ativo.setSchemaVersion(VERSAO_PAYLOAD_ATIVO);
        ativo.setDedupKey(gerar(ativo));
    }

    private static String gerar(Ativo ativo) {
        String identidade = String.join("|",
                valor(ativo.getSymbol()),
                valor(ativo.getRegularMarketTime()),
                valor(ativo.getRegularMarketPrice()),
                valor(ativo.getRegularMarketVolume()),
                valor(ativo.getRegularMarketPreviousClose()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identidade.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    private static String valor(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
