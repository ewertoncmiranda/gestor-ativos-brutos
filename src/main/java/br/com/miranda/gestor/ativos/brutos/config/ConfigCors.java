package br.com.miranda.gestor.ativos.brutos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONFIG_CORS;

@Slf4j
@Configuration
public class ConfigCors implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origens = allowedOrigins.split(",");
        log.info("{} - CORS habilitado para as origens: {}", CONFIG_CORS, allowedOrigins);

        registry.addMapping("/**")
                .allowedOrigins(origens)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
