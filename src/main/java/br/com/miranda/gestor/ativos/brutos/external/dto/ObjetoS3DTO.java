package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.time.Instant;

public record ObjetoS3DTO(
        String bucket,
        String key,
        String fileName,
        Long size,
        Instant lastModified,
        String eTag,
        String downloadUrl
) {
}
