package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

public class ExcecaoIntegracaoGemini extends ExcecaoAplicacao {

    public ExcecaoIntegracaoGemini(String model, String detail, Throwable cause) {
        super(
                "GEMINI_INTEGRATION_ERROR",
                "Falha ao gerar conteudo no Gemini usando o modelo " + model + ": " + detail,
                HttpStatus.BAD_GATEWAY,
                cause
        );
    }
}
