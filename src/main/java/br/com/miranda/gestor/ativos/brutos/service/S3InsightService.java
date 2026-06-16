package br.com.miranda.gestor.ativos.brutos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.SERVICE;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3InsightService {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket.name:bucket-salvar-insights}")
    private String bucketName;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**     * Salva um objeto JSON no bucket S3 com nome baseado em timestamp (HH:mm:ss)     *     * @param data objeto a ser serializado em JSON     * @param simbolo símbolo do ativo (usado como prefixo da pasta)     * @return true se salvo com sucesso, false caso contrário     */
    public boolean salvarInsightNoS3(Object data, String simbolo) {
        try {
            String jsonContent = objectMapper.writeValueAsString(data);
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            String chave = construirChave(simbolo, timestamp);

            log.info("{} - Salvando insight no S3 com chave: {}", SERVICE, chave);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(chave)
                    .contentType("application/json")
                    .build();

            PutObjectResponse response = s3Client.putObject(putObjectRequest,
                    RequestBody.fromString(jsonContent));

            log.info("{} - Insight salvo com sucesso no S3. ETag: {}", SERVICE, response.eTag());
            return true;

        } catch (Exception e) {
            log.error("{} - Erro ao salvar insight no S3: {}", SERVICE, e.getMessage(), e);
            throw new  RuntimeException(e);
        }
    }

    /**     * Constrói a chave do objeto S3 no formato: {simbolo}/{ativo}/{timestamp}.json     */
    private String construirChave(String simbolo, String timestamp) {
        return String.format("%s/%s/%s.json", simbolo.toLowerCase(), "insights", timestamp);
    }
}