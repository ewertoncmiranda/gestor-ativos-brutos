package br.com.miranda.gestor.ativos.brutos.service.orcamento;

import br.com.miranda.gestor.ativos.brutos.repository.RepositorioExecucaoEtl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orcamento mensal de requisicoes a BRAPI (plano gratuito: 15.000/mes; o teto
 * aqui deixa 10% de reserva). Antes de cada ciclo intradiario, projeta o
 * consumo ate o fim do mes e escolhe a cadencia que ainda cabe: 30 min, 60 min
 * ou desligado. Um 429 vira linha BRAPI_ORCAMENTO=ERRO em etl_execucao, uma
 * vez por dia, para a saude dos dados e o alerta enxergarem.
 */
@Slf4j
@Service
public class OrcamentoBrapi {

    static final String FONTE = "BRAPI_ORCAMENTO";

    private final RegistroConsumoBrapi consumo;
    private final RepositorioExecucaoEtl execucoes;
    private final long orcamentoMensal;
    private final long orcamentoTela;
    private final AtomicReference<LocalDate> ultimoErroRegistrado = new AtomicReference<>();

    /** Teto padrao das chamadas da tela: cabe nos 13.500 junto do intradiario de 35 favoritos. */
    static final long ORCAMENTO_TELA_PADRAO = 1500;

    public OrcamentoBrapi(RegistroConsumoBrapi consumo, RepositorioExecucaoEtl execucoes, long orcamentoMensal) {
        this(consumo, execucoes, orcamentoMensal, ORCAMENTO_TELA_PADRAO);
    }

    @Autowired
    public OrcamentoBrapi(RegistroConsumoBrapi consumo, RepositorioExecucaoEtl execucoes,
                          @Value("${brapi.orcamento.mensal:13500}") long orcamentoMensal,
                          @Value("${brapi.orcamento.tela.mensal:1500}") long orcamentoTela) {
        this.consumo = consumo;
        this.execucoes = execucoes;
        this.orcamentoMensal = orcamentoMensal;
        this.orcamentoTela = orcamentoTela;
    }

    /** Cadencia permitida para o ciclo de agora, com `favoritos` requisicoes por ciclo. */
    public ModoColeta modoPara(ZonedDateTime agora, int favoritos) {
        long consumido = consumo.consumidoNoMes(YearMonth.from(agora));
        ModoColeta modo = ProjecaoOrcamentoBrapi.modo(consumido, favoritos, orcamentoMensal, agora);
        if (modo != ModoColeta.A_CADA_30_MIN) {
            log.warn("(ORCAMENTO-BRAPI)-Consumo no mes {} de {}; {} favorito(s): coleta {}",
                    consumido, orcamentoMensal, favoritos, modo);
        }
        return modo;
    }

    /** Se ainda ha cota para uma chamada avulsa (perfil, preenchimento ao favoritar). */
    public boolean cabeChamadaAvulsa(ZonedDateTime agora) {
        return consumo.consumidoNoMes(YearMonth.from(agora)) < orcamentoMensal;
    }

    /**
     * Chamada disparada pela tela (requisicao HTTP): precisa caber no total do
     * mes E no teto proprio da tela. Sem o teto, navegar pelo painel consumia a
     * cota que o ciclo intradiario projeta para si (visto em 2026-09-29: 55
     * requisicoes antes das 10h, quase todas de cliques).
     */
    public boolean cabeChamadaDaTela(ZonedDateTime agora) {
        YearMonth mes = YearMonth.from(agora);
        return consumo.consumidoNoMes(mes) < orcamentoMensal
                && consumo.consumidoPelaTelaNoMes(mes) < orcamentoTela;
    }

    /** 429 ou cota estourada: registra ERRO em etl_execucao (no maximo uma linha por dia). */
    public void registrarCotaEsgotada(ZonedDateTime agora, String detalhe) {
        LocalDate hoje = agora.toLocalDate();
        if (hoje.equals(ultimoErroRegistrado.getAndSet(hoje))) {
            return;
        }
        long consumido = consumo.consumidoNoMes(YearMonth.from(agora));
        String mensagem = "Cota da BRAPI: " + detalhe + ". Consumo no mes: " + consumido + " de " + orcamentoMensal + ".";
        log.error("(ORCAMENTO-BRAPI)-{}", mensagem);
        try {
            execucoes.registrarErro(FONTE, YearMonth.from(agora).toString(), "brapi", mensagem);
        } catch (DataAccessException e) {
            log.warn("(ORCAMENTO-BRAPI)-Nao foi possivel registrar em etl_execucao: {}", e.getMessage());
        }
    }
}
