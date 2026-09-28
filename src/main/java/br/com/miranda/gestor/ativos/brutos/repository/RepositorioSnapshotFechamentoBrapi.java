package br.com.miranda.gestor.ativos.brutos.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * snapshot_fechamento_brapi (infra V15) por JDBC - sem entidade JPA pelo mesmo
 * motivo do brapi_consumo. INSERT IGNORE: a foto e imutavel, a primeira do
 * pregao vale e rodar de novo nao sobrescreve.
 */
@Repository
@RequiredArgsConstructor
public class RepositorioSnapshotFechamentoBrapi {

    public record Foto(String simbolo, LocalDate dataPregao, BigDecimal abertura, BigDecimal maxima,
                       BigDecimal minima, BigDecimal fechamento, Long volume, LocalDateTime horarioDadoBrapi,
                       LocalDateTime capturadoEm, String status) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** Devolve 1 quando gravou, 0 quando o pregao ja tinha foto. */
    public int gravar(Foto f) {
        return jdbc.update("INSERT IGNORE INTO snapshot_fechamento_brapi (simbolo, data_pregao, abertura, maxima, "
                        + "minima, fechamento, volume, horario_dado_brapi, capturado_em, status_captura) VALUES "
                        + "(:simbolo, :data, :abertura, :maxima, :minima, :fechamento, :volume, :horario, :capturado, :status)",
                new MapSqlParameterSource()
                        .addValue("simbolo", f.simbolo())
                        .addValue("data", f.dataPregao())
                        .addValue("abertura", f.abertura())
                        .addValue("maxima", f.maxima())
                        .addValue("minima", f.minima())
                        .addValue("fechamento", f.fechamento())
                        .addValue("volume", f.volume())
                        .addValue("horario", f.horarioDadoBrapi())
                        .addValue("capturado", f.capturadoEm())
                        .addValue("status", f.status()));
    }
}
