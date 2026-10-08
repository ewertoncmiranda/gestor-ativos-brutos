package br.com.miranda.gestor.ativos.brutos.config;

import lombok.extern.slf4j.Slf4j;
import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONFIG_SQS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;

@Slf4j
@Configuration
public class ConfigSqs {

    private final ConfigProperties configProperties;

    @Autowired
    public ConfigSqs(ConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    @Bean
    public SqsClient config() {
        Region regiao = Region.of(configProperties.getAwsRegion());
        String endpoint = configProperties.getAwsSqsEndpointBase();
        log.info("{}-Inicializando SqsClient com endpoint : {} e região : {}", CONFIG_SQS, endpoint, regiao);

        SqsClientBuilder builder = SqsClient.builder()
                .region(regiao)
                .credentialsProvider(provedorCredenciais(
                        configProperties.getAwsAccessKeyId(),
                        configProperties.getAwsSecretAccessKey()));

        if (temTexto(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }

        SqsClient sqsClient = builder.build();

        log.info("{}-SqsClient criado com sucesso", CONFIG_SQS);
        return sqsClient;
    }

    static AwsCredentialsProvider provedorCredenciais(String accessKeyId, String secretAccessKey) {
        if (temTexto(accessKeyId) && temTexto(secretAccessKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        return DefaultCredentialsProvider.create();
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
