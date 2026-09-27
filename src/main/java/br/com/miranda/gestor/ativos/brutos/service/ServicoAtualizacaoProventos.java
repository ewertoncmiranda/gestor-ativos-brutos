package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.ProventoDistribuidoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.ProventoB3DTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteB3Proventos;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAtivoMonitorado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioProventoDistribuido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Escritor unico do cache de proventos (provento_distribuido) - fala com a
 * B3 (ClienteB3Proventos), nunca na hora do clique do usuario.
 *
 * So os ultimos 12 meses vem em cada consulta (limite da propria B3): rodar
 * isso 1x/dia, dia apos dia, e o que constroi historico de verdade a partir
 * de agora - nao resolve sozinho o backfill de anos anteriores (ISS de
 * "retorno com proventos" no backtest do gerar-insights, que precisa de uma
 * fonte historica separada).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoAtualizacaoProventos {

    private static final DateTimeFormatter FORMATO_B3 = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClienteB3Proventos clienteB3Proventos;
    private final RepositorioAtivoMonitorado repositorioAtivoMonitorado;
    private final RepositorioProventoDistribuido repositorioProventoDistribuido;

    public void atualizarProventos() {
        var ativos = repositorioAtivoMonitorado.findByAtivoTrue();
        // Varios ativos monitorados podem ser a mesma emissora (ON e PN, ex.
        // PETR3/PETR4): consultar 1x por codigo de emissor, nao por ticker.
        var codigosEmissores = ativos.stream()
                .map(a -> codigoEmissor(a.getSimbolo()))
                .distinct()
                .toList();

        for (String codigoEmissor : codigosEmissores) {
            try {
                var proventos = clienteB3Proventos.consultarProventos(codigoEmissor);
                proventos.forEach(p -> persistir(codigoEmissor, p));
            } catch (Exception e) {
                log.error("(SERVICO) - Erro ao atualizar proventos de {}: {}", codigoEmissor, e.getMessage(), e);
            }
        }
    }

    /**
     * A B3 identifica o emissor pelas 4 letras do ticker, sem o digito da
     * especie de acao (PETR4 -> PETR, VALE3 -> VALE).
     */
    private String codigoEmissor(String simbolo) {
        return simbolo.replaceAll("\\d+$", "");
    }

    private void persistir(String simbolo, ProventoB3DTO p) {
        LocalDate dataPagamento;
        BigDecimal valor;
        try {
            dataPagamento = LocalDate.parse(p.dataPagamento(), FORMATO_B3);
            valor = new BigDecimal(p.valorPorAcao().replace(",", "."));
        } catch (Exception e) {
            log.warn("(SERVICO) - Provento ignorado de {} (formato inesperado): {}", simbolo, e.getMessage());
            return;
        }

        ProventoDistribuidoEntity entidade = repositorioProventoDistribuido
                .findBySimboloAndIsinAndTipoAndDataPagamento(simbolo, p.isinCode(), p.tipo(), dataPagamento)
                .orElseGet(ProventoDistribuidoEntity::new);
        entidade.setSimbolo(simbolo);
        entidade.setIsin(p.isinCode());
        entidade.setTipo(p.tipo());
        entidade.setValorPorAcao(valor);
        entidade.setPeriodoReferencia(p.periodoReferencia());
        entidade.setAprovadoEm(parseDataOpcional(p.aprovadoEm()));
        entidade.setUltimaDataComDireito(parseDataOpcional(p.ultimaDataComDireito()));
        entidade.setDataPagamento(dataPagamento);
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorioProventoDistribuido.save(entidade);
    }

    private LocalDate parseDataOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor, FORMATO_B3);
        } catch (Exception e) {
            return null;
        }
    }
}
