package br.com.miranda.gestor.ativos.brutos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.Getter;

@Getter
@Component
public class ConfigProperties {

    @Value("${server.port:9090}")
    private int serverPort;

    @Value("${aws.sqs.endpoint.base:http://localstack:4566}")
    private String awsSqsEndpointBase;

    @Value("${aws.sqs.queue.url:http://localstack:4566/000000000000/tratar-ativos}")
    private String awsSqsQueueUrl;

    @Value("${aws.sqs.historical-series.queue.url:http://localstack:4566/000000000000/sqs-registrar-series-historicas}")
    private String awsSqsHistoricalSeriesQueueUrl;

    @Value("${aws.region:${AWS_REGION:sa-east-1}}")
    private String awsRegion;

    @Value("${aws.accessKeyId:${AWS_ACCESS_KEY_ID:}}")
    private String awsAccessKeyId;

    @Value("${aws.secretAccessKey:${AWS_SECRET_ACCESS_KEY:}}")
    private String awsSecretAccessKey;

    @Value("${brapi.api.key:}")
    private String brapiApiKey;

    @Value("${spring.profiles.active:dev}")
    private String springProfilesActive;


}
