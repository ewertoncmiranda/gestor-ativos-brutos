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

    /**
     * Quem disparou a chamada. TELA e toda chamada feita dentro de uma
     * requisicao HTTP (botao robusto, ficha, historico ao vivo, favoritar):
     * conta com o sufixo "-tela" em brapi_consumo e tem teto proprio, para a
     * navegacao nao consumir a cota do ciclo intradiario.
     */
    enum Origem {
        AGENDADA(""), TELA("-tela");

        private final String sufixo;

        Origem(String sufixo) {
            this.sufixo = sufixo;
        }

        public String chave(Endpoint endpoint) {
            return endpoint.nome() + sufixo;
        }
    }

    default void registrar(LocalDate dia, Endpoint endpoint) {
        registrar(dia, endpoint, Origem.AGENDADA);
    }

    void registrar(LocalDate dia, Endpoint endpoint, Origem origem);

    /** Total do mes, de todas as origens: e o que o plano da BRAPI cobra. */
    long consumidoNoMes(YearMonth mes);

    /** So as chamadas de origem TELA no mes. */
    long consumidoPelaTelaNoMes(YearMonth mes);
}
