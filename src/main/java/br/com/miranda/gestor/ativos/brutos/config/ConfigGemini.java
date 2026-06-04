package br.com.miranda.gestor.ativos.brutos.config;

import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ConfigGemini {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Bean
    public Client geminiClient() {
        log.info("(CONFIG-GEMINI)-Inicializando cliente Google GenAI (Gemini)");

        return Client.builder()
                .apiKey(geminiApiKey)
                .build();
    }
}
