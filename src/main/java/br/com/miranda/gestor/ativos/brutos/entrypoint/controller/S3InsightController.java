package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.S3ObjectDTO;
import br.com.miranda.gestor.ativos.brutos.service.S3InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/s3/insights")
@RequiredArgsConstructor
public class S3InsightController {

    private final S3InsightService service;

    @GetMapping
    public ResponseEntity<List<S3ObjectDTO>> listarArquivos(
            @RequestParam(value = "nome", required = false) String nome
    ) {
        log.info("(S3-CONTROLLER)-Listando arquivos do bucket. Filtro nome: {}", nome);
        return ResponseEntity.ok(service.listarArquivos(nome));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> baixarArquivo(@RequestParam("key") String key) {
        log.info("(S3-CONTROLLER)-Baixando arquivo do bucket. Key: {}", key);

        ResponseBytes<GetObjectResponse> arquivo = service.baixarArquivo(key);
        GetObjectResponse response = arquivo.response();
        String fileName = extrairNomeArquivo(key);
        MediaType mediaType = Optional.ofNullable(response.contentType())
                .map(MediaType::parseMediaType)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(response.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(arquivo.asByteArray());
    }

    private String extrairNomeArquivo(String key) {
        int ultimoSeparador = key.lastIndexOf('/');
        if (ultimoSeparador < 0) {
            return key;
        }
        return key.substring(ultimoSeparador + 1);
    }
}
