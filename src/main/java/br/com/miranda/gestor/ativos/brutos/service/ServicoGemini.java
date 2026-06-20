package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoIntegracaoGemini;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.GEMINI_SERVICE;

@Slf4j
@Service
public class ServicoGemini {

    private static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";
    private static final List<String> FREE_TIER_FALLBACK_MODELS = List.of(
            DEFAULT_MODEL,
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite"
    );
    private static final String SYSTEM_INSTRUCTION = """
            Analista quantitativo de acoes brasileiras. Responda em JSON estrito, conciso e baseado somente nos dados fornecidos.
            Campos textuais devem ser curtos. O campo resumo deve ter no maximo 200 caracteres.
            """;
    private static final int MAX_OUTPUT_TOKENS = 600;
    private static final long QUOTA_FALLBACK_DELAY_MILLIS = 4_000L;
    private static final GenerateContentConfig GENERATION_CONFIG = GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
            .responseMimeType("application/json")
            .responseSchema(montarRespostaSchema())
            .temperature(0.2F)
            .maxOutputTokens(MAX_OUTPUT_TOKENS)
            .build();

    private final Client geminiClient;

    public ServicoGemini(Client geminiClient) {
        this.geminiClient = geminiClient;
    }

    /**
     * Gera uma análise estruturada usando o modelo padrão configurado.
     */
    public String gerarConteudo(String prompt) {
        return gerarConteudo(prompt, DEFAULT_MODEL);
    }

    /**
     * Gera uma análise estruturada usando fallback entre modelos quando houver limite de cota.
     */
    public String gerarConteudo(String prompt, String model) {
        List<String> modelos = montarOrdemDeModelos(model);
        RuntimeException ultimaFalha = null;

        for (String modeloAtual : modelos) {
            try {
                String conteudo = gerarConteudoComModelo(prompt, modeloAtual);
                log.info(
                        "{}-Conteudo gerado com sucesso pelo Gemini (modelo: {})",
                        GEMINI_SERVICE,
                        modeloAtual
                );
                return conteudo;
            } catch (RuntimeException error) {
                RuntimeException geminiException = toGeminiException(error, modeloAtual);
                ultimaFalha = geminiException;

                if (!isQuotaExceeded(error)) {
                    log.error(
                            "{}-Erro ao gerar conteudo no Gemini. Modelo: {}, Causa raiz: {}",
                            GEMINI_SERVICE,
                            modeloAtual,
                            getRootCauseMessage(geminiException),
                            geminiException
                    );
                    throw geminiException;
                }

                log.warn(
                        "{}-Cota excedida no Gemini. Alternando modelo de {} para proximo fallback. Causa raiz: {}",
                        GEMINI_SERVICE,
                        modeloAtual,
                        getRootCauseMessage(error)
                );
                aguardarAntesDoFallback();
            }
        }

        throw ultimaFalha;
    }

    private String gerarConteudoComModelo(String prompt, String model) {
        log.debug("{}-Gerando conteudo com modelo: {}", GEMINI_SERVICE, model);

        GenerateContentResponse response = geminiClient.models.generateContent(model, prompt, GENERATION_CONFIG);
        String text = response.text();
        if (!StringUtils.hasText(text)) {
            throw new ExcecaoIntegracaoGemini(model, "Resposta vazia retornada pela API", null);
        }

        return text;
    }

    private static Schema montarRespostaSchema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("ativo", stringSchema(20L));
        properties.put("sentimento", stringSchema(30L));
        properties.put("forca_sinal", stringSchema(30L));
        properties.put("risco", stringSchema(20L));
        properties.put("confianca_analise", Schema.builder()
                .type(Type.Known.NUMBER)
                .minimum(0.0)
                .maximum(100.0)
                .build());
        properties.put("resumo", stringSchema(200L));
        properties.put("analise_tecnica", stringSchema(280L));
        properties.put("analise_fundamentalista", stringSchema(280L));
        properties.put("possivel_cenario", stringSchema(240L));
        properties.put("recomendacao", stringSchema(120L));

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(properties)
                .required(
                        "ativo",
                        "sentimento",
                        "forca_sinal",
                        "risco",
                        "confianca_analise",
                        "resumo",
                        "analise_tecnica",
                        "analise_fundamentalista",
                        "possivel_cenario",
                        "recomendacao"
                )
                .propertyOrdering(
                        "ativo",
                        "sentimento",
                        "forca_sinal",
                        "risco",
                        "confianca_analise",
                        "resumo",
                        "analise_tecnica",
                        "analise_fundamentalista",
                        "possivel_cenario",
                        "recomendacao"
                )
                .build();
    }

    private static Schema stringSchema(Long maxLength) {
        return Schema.builder()
                .type(Type.Known.STRING)
                .maxLength(maxLength)
                .build();
    }

    private List<String> montarOrdemDeModelos(String model) {
        List<String> modelos = new ArrayList<>();
        if (StringUtils.hasText(model)) {
            modelos.add(model);
        }

        for (String fallbackModel : FREE_TIER_FALLBACK_MODELS) {
            if (!modelos.contains(fallbackModel)) {
                modelos.add(fallbackModel);
            }
        }

        return modelos;
    }

    private RuntimeException toGeminiException(Throwable error, String model) {
        if (error instanceof ExcecaoIntegracaoGemini exception) {
            return exception;
        }

        if (error instanceof ApiException apiException) {
            String detail = String.format(
                    "HTTP %s %s - %s",
                    apiException.code(),
                    apiException.status(),
                    apiException.message()
            );
            return new ExcecaoIntegracaoGemini(model, detail, apiException);
        }

        if (error instanceof GenAiIOException) {
            return new ExcecaoIntegracaoGemini(
                    model,
                    "Falha de comunicacao HTTP com a API do Gemini. Verifique rede, DNS, proxy, TLS e disponibilidade do endpoint",
                    error
            );
        }

        return new ExcecaoIntegracaoGemini(model, error.getMessage(), error);
    }

    private boolean isQuotaExceeded(Throwable error) {
        Throwable root = error;
        while (root != null) {
            if (root instanceof ApiException apiException && String.valueOf(apiException.code()).equals("429")) {
                return true;
            }

            String message = root.getMessage();
            if (StringUtils.hasText(message)) {
                String normalizedMessage = message.toLowerCase();
                if (normalizedMessage.contains("resource_exhausted")
                        || normalizedMessage.contains("quota")
                        || normalizedMessage.contains("rate limit")
                        || normalizedMessage.contains("too many requests")) {
                    return true;
                }
            }

            root = root.getCause();
        }

        return false;
    }

    private void aguardarAntesDoFallback() {
        try {
            Thread.sleep(QUOTA_FALLBACK_DELAY_MILLIS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new ExcecaoIntegracaoGemini(
                    DEFAULT_MODEL,
                    "Thread interrompida ao aguardar fallback de rate limit",
                    interruptedException
            );
        }
    }

    private String getRootCauseMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
