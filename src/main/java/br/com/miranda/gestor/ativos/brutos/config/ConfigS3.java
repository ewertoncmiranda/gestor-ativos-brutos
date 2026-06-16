package br.com.miranda.gestor.ativos.brutos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.CONFIG_S3;

@Slf4j
@Configuration
public class ConfigS3 {

    @Value("${aws.s3.endpoint.base}")
    private String s3EndpointBase;

    @Value("${aws.accessKeyId}")
    private String accessKeyId;

    @Value("${aws.secretAccessKey}")
    private String secretAccessKey;

    @Bean
    public S3Client s3Client() {
        var regiao = Region.SA_EAST_1;
        log.info("{} - Inicializando S3Client com endpoint: {} e região: {}", CONFIG_S3, s3EndpointBase, regiao);

        S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3EndpointBase))
                .region(regiao)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .forcePathStyle(true)
                .build();

        log.info("{} - S3Client criado com sucesso", CONFIG_S3);
        return s3Client;
    }
}