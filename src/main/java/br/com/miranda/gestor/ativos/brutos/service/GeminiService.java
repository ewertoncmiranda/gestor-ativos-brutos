package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.GeminiIntegrationException;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.GenerateContentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.GEMINI_SERVICE;

@Slf4j
@Service
public class GeminiService {

    private static final String DEFAULT_MODEL = "gemini-3-flash-preview";

    private final Client geminiClient;

    public GeminiService(Client geminiClient) {
        this.geminiClient = geminiClient;
    }

    public Mono<String> gerarConteudo(String prompt) {
        return gerarConteudo(prompt, DEFAULT_MODEL);
    }

    public Mono<String> gerarConteudo(String prompt, String model) {
        return Mono.fromCallable(() -> {
                    log.debug("{}-Gerando conteudo com modelo: {}", GEMINI_SERVICE, model);

                    GenerateContentResponse response = geminiClient.models.generateContent(model, prompt, null);
                    String text = response.text();
                    if (!StringUtils.hasText(text)) {
                        throw new GeminiIntegrationException(model, "Resposta vazia retornada pela API", null);
                    }

                    return text;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(error -> toGeminiException(error, model))
                .doOnSuccess(response -> log.info(
                        "{}-Conteudo gerado com sucesso pelo Gemini (modelo: {})",
                        GEMINI_SERVICE,
                        model
                ))
                .doOnError(error -> log.error(
                        "{}-Erro ao gerar conteudo no Gemini. Modelo: {}, Causa raiz: {}",
                        GEMINI_SERVICE,
                        model,
                        getRootCauseMessage(error),
                        error
                ));
    }

    private RuntimeException toGeminiException(Throwable error, String model) {
        if (error instanceof GeminiIntegrationException exception) {
            return exception;
        }

        if (error instanceof ApiException apiException) {
            String detail = String.format(
                    "HTTP %s %s - %s",
                    apiException.code(),
                    apiException.status(),
                    apiException.message()
            );
            return new GeminiIntegrationException(model, detail, apiException);
        }

        if (error instanceof GenAiIOException) {
            return new GeminiIntegrationException(
                    model,
                    "Falha de comunicacao HTTP com a API do Gemini. Verifique rede, DNS, proxy, TLS e disponibilidade do endpoint",
                    error
            );
        }

        return new GeminiIntegrationException(model, error.getMessage(), error);
    }

    private String getRootCauseMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
