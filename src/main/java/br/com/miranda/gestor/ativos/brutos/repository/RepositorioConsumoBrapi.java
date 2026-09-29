package br.com.miranda.gestor.ativos.brutos.repository;

import br.com.miranda.gestor.ativos.brutos.service.orcamento.RegistroConsumoBrapi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * brapi_consumo (infra V15) por JDBC, sem entidade JPA: com ddl-auto=validate
 * uma entidade para tabela ainda nao criada derrubaria o gestor na subida.
 * Sem a tabela, contar vira no-op com um aviso so - a chamada a BRAPI nao
 * pode falhar por causa do contador.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RepositorioConsumoBrapi implements RegistroConsumoBrapi {

    private final NamedParameterJdbcTemplate jdbc;
    private final AtomicBoolean avisouTabelaAusente = new AtomicBoolean(false);

    @Override
    public void registrar(LocalDate dia, Endpoint endpoint, Origem origem) {
        try {
            jdbc.update("INSERT INTO brapi_consumo (dia, endpoint, quantidade) VALUES (:dia, :endpoint, 1) "
                            + "ON DUPLICATE KEY UPDATE quantidade = quantidade + 1",
                    Map.of("dia", dia, "endpoint", origem.chave(endpoint)));
        } catch (DataAccessException e) {
            avisarUmaVez(e);
        }
    }

    @Override
    public long consumidoNoMes(YearMonth mes) {
        return somar(mes, "");
    }

    @Override
    public long consumidoPelaTelaNoMes(YearMonth mes) {
        return somar(mes, "AND endpoint LIKE '%-tela'");
    }

    private long somar(YearMonth mes, String filtro) {
        try {
            Long total = jdbc.queryForObject("SELECT COALESCE(SUM(quantidade), 0) FROM brapi_consumo "
                            + "WHERE dia BETWEEN :inicio AND :fim " + filtro,
                    Map.of("inicio", mes.atDay(1), "fim", mes.atEndOfMonth()), Long.class);
            return total == null ? 0 : total;
        } catch (DataAccessException e) {
            avisarUmaVez(e);
            return 0;
        }
    }

    private void avisarUmaVez(DataAccessException e) {
        if (avisouTabelaAusente.compareAndSet(false, true)) {
            log.warn("(ORCAMENTO-BRAPI)-brapi_consumo indisponivel (migracao V15 aplicada?); "
                    + "consumo nao sera contado: {}", e.getMostSpecificCause().getMessage());
        }
    }
}
