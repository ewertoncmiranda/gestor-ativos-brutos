package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoArmazenamentoExterno;
import br.com.miranda.gestor.ativos.brutos.external.dto.ObjetoS3DTO;
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

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoArmazenamentoAnalise {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket.name:bucket-salvar-insights}")
    private String bucketName;

    /**
     * Persiste a análise gerada em JSON no bucket S3 configurado para o ativo.
     */
    public boolean salvarAnaliseNoS3(Object data, String simbolo) {
        try {
            String jsonContent = objectMapper.writeValueAsString(data);
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            String chave = construirChave(simbolo, timestamp);

            log.info("{} - Salvando insight no S3 com chave: {}", SERVICO, chave);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(chave)
                    .contentType("application/json")
                    .build();

            PutObjectResponse response = s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromString(jsonContent)
            );

            log.info("{} - Analise salva com sucesso no S3. ETag: {}", SERVICO, response.eTag());
            return true;
        } catch (Exception e) {
            log.error("{} - Erro ao salvar insight no S3: {}", SERVICO, e.getMessage(), e);
            throw new ExcecaoArmazenamentoExterno(simbolo, e);
        }
    }

    /**
     * Lista arquivos de análise armazenados no S3, com filtro opcional por nome.
     */
    public List<ObjetoS3DTO> listarArquivos(String nome) {
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
                    SERVICO, bucketName, nome, e);
            throw new ExcecaoArmazenamentoExterno("Falha ao listar arquivos do bucket S3", bucketName, e);
        }
    }

    /**
     * Baixa o conteúdo bruto de um arquivo de análise salvo no S3.
     */
    public ResponseBytes<GetObjectResponse> baixarArquivo(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObjectAsBytes(request);
        } catch (Exception e) {
            log.error("{} - Erro ao baixar arquivo do S3. Bucket: {}, Key: {}",
                    SERVICO, bucketName, key, e);
            throw new ExcecaoArmazenamentoExterno("Falha ao baixar arquivo do S3", key, e);
        }
    }

    private String construirChave(String simbolo, String timestamp) {
        return String.format("%s/%s/%s.json", simbolo.toLowerCase(), "analises", timestamp);
    }

    private ObjetoS3DTO toDto(S3Object objeto) {
        return new ObjetoS3DTO(
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
                .path("/s3/analises/download")
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
