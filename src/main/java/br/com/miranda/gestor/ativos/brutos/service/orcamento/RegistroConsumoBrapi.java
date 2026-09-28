package br.com.miranda.gestor.ativos.brutos.service.orcamento;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Porta do contador de requisicoes a BRAPI (tabela brapi_consumo, infra V15).
 * O ClienteBrApi registra cada chamada; o orcamento le o total do mes.
 */
public interface RegistroConsumoBrapi {

    /** Endpoints contados separadamente, como no contrato da V15. */
    enum Endpoint {
        QUOTE("quote"), QUOTE_LOTE("quote-lote"), HISTORICAL("historical"), PROFILE("profile");

        private final String nome;

        Endpoint(String nome) {
            this.nome = nome;
        }

        public String nome() {
            return nome;
        }
    }

    void registrar(LocalDate dia, Endpoint endpoint);

    long consumidoNoMes(YearMonth mes);
}
