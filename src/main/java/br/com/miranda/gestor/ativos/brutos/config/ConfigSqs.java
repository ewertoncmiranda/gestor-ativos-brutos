package br.com.miranda.gestor.ativos.brutos.config;

import lombok.extern.slf4j.Slf4j;
import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.CONFIG_SQS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Slf4j
@Configuration
public class ConfigSqs {

    @Value("${aws.sqs.endpoint.base}")
    private String sqsEndpointBase;

    @Bean
    public SqsClient config() {
        var regiao = Region.SA_EAST_1 ;
        log.info("{}-Inicializando SqsClient com endpoint : {} e região : {}", CONFIG_SQS, sqsEndpointBase,regiao);

        SqsClient sqsClient = SqsClient.builder()
                .endpointOverride(URI.create(sqsEndpointBase))
                .region(regiao)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();

        log.info("{}-SqsClient criado com sucesso", CONFIG_SQS);
        return sqsClient;
    }
}
