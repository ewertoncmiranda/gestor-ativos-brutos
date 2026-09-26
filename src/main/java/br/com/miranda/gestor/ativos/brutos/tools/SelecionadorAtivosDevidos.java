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
        if (atualizadoEm == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(atualizadoEm.plusSeconds(intervaloSegundos));
    }
}
