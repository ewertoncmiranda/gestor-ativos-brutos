package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExternalStorageException;
import br.com.miranda.gestor.ativos.brutos.external.dto.S3ObjectDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.SERVICE;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3InsightService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket.name:bucket-salvar-insights}")
    private String bucketName;

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

            PutObjectResponse response = s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromString(jsonContent)
            );

            log.info("{} - Insight salvo com sucesso no S3. ETag: {}", SERVICE, response.eTag());
            return true;
        } catch (Exception e) {
            log.error("{} - Erro ao salvar insight no S3: {}", SERVICE, e.getMessage(), e);
            throw new ExternalStorageException(simbolo, e);
        }
    }

    public List<S3ObjectDTO> listarArquivos(String nome) {
        try {
            String filtro = Optional.ofNullable(nome)
                    .map(String::trim)
                    .filter(valor -> !valor.isBlank())
                    .map(String::toLowerCase)
                    .orElse(null);

            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            return s3Client.listObjectsV2Paginator(request)
                    .contents()
                    .stream()
                    .filter(objeto -> filtro == null || objeto.key().toLowerCase().contains(filtro))
                    .map(this::toDto)
                    .toList();
        } catch (Exception e) {
            log.error("{} - Erro ao listar arquivos no S3. Bucket: {}, Filtro: {}",
                    SERVICE, bucketName, nome, e);
            throw new ExternalStorageException("Falha ao listar arquivos do bucket S3", bucketName, e);
        }
    }

    public ResponseBytes<GetObjectResponse> baixarArquivo(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObjectAsBytes(request);
        } catch (Exception e) {
            log.error("{} - Erro ao baixar arquivo do S3. Bucket: {}, Key: {}",
                    SERVICE, bucketName, key, e);
            throw new ExternalStorageException("Falha ao baixar arquivo do S3", key, e);
        }
    }

    private String construirChave(String simbolo, String timestamp) {
        return String.format("%s/%s/%s.json", simbolo.toLowerCase(), "insights", timestamp);
    }

    private S3ObjectDTO toDto(S3Object objeto) {
        return new S3ObjectDTO(
                bucketName,
                objeto.key(),
                extrairNomeArquivo(objeto.key()),
                objeto.size(),
                objeto.lastModified(),
                objeto.eTag(),
                montarDownloadUrl(objeto.key())
        );
    }

    private String montarDownloadUrl(String key) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/s3/insights/download")
                .queryParam("key", key)
                .toUriString();
    }

    private String extrairNomeArquivo(String key) {
        int ultimoSeparador = key.lastIndexOf('/');
        if (ultimoSeparador < 0) {
            return key;
        }
        return key.substring(ultimoSeparador + 1);
    }
}
