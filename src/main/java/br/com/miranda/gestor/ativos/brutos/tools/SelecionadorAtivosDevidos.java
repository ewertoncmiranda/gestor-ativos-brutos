package br.com.miranda.gestor.ativos.brutos.tools;

import java.time.LocalDateTime;

/**
 * Generaliza a mesma regra que {@code AgendadorAtivos.estaDevido()} ja usava
 * so pra cotacao: compara atualizadoEm + intervalo contra agora. Reaproveitado
 * pelos 3 tipos de dado cacheados (cotacao, historico diario, perfil da
 * empresa), cada um com seu proprio intervalo.
 */
public final class SelecionadorAtivosDevidos {

    private SelecionadorAtivosDevidos() {
    }

    public static boolean estaDevido(LocalDateTime atualizadoEm, long intervaloSegundos) {
        return estaDevido(atualizadoEm, intervaloSegundos, LocalDateTime.now());
    }

    /** Mesma regra com o "agora" explicito - para quem decide com um relogio injetado. */
    public static boolean estaDevido(LocalDateTime atualizadoEm, long intervaloSegundos, LocalDateTime agora) {
        if (atualizadoEm == null) {
            return true;
        }
        return agora.isAfter(atualizadoEm.plusSeconds(intervaloSegundos));
    }
}
