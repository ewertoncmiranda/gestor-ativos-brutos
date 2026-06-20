package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.ObjetoS3DTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoArmazenamentoAnalise;
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
@RequestMapping("/s3/analises")
public class ControladorArquivoAnalise {

    private final ServicoArmazenamentoAnalise servicoArmazenamentoAnalise;

    public ControladorArquivoAnalise(ServicoArmazenamentoAnalise servicoArmazenamentoAnalise) {
        this.servicoArmazenamentoAnalise = servicoArmazenamentoAnalise;
    }

    /**
     * Lista os arquivos de análise disponíveis no bucket S3.
     */
    @GetMapping
    public ResponseEntity<List<ObjetoS3DTO>> listarArquivos(
            @RequestParam(value = "nome", required = false) String nome
    ) {
        log.info("(S3-CONTROLADOR)-Listando arquivos do bucket. Filtro nome: {}", nome);
        return ResponseEntity.ok(servicoArmazenamentoAnalise.listarArquivos(nome));
    }

    /**
     * Baixa um arquivo de análise a partir da chave armazenada no S3.
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> baixarArquivo(@RequestParam("key") String key) {
        log.info("(S3-CONTROLADOR)-Baixando arquivo do bucket. Key: {}", key);

        ResponseBytes<GetObjectResponse> arquivo = servicoArmazenamentoAnalise.baixarArquivo(key);
        GetObjectResponse response = arquivo.response();
        String nomeArquivo = extrairNomeArquivo(key);
        MediaType mediaType = Optional.ofNullable(response.contentType())
                .map(MediaType::parseMediaType)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(response.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(nomeArquivo, StandardCharsets.UTF_8)
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
