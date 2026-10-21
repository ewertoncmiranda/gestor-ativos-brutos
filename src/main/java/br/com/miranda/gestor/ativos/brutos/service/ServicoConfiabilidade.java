package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.ConfiabilidadeDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.ConfiabilidadeDTO.Nivel;
import br.com.miranda.gestor.ativos.brutos.external.dto.ConfiabilidadeDTO.Resumo;
import br.com.miranda.gestor.ativos.brutos.external.dto.ConfiabilidadeDTO.Selo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.Auditor;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.Cadastro;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.CompetenciaFundos;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.Divida;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.MovimentoFundos;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioConfiabilidade.Proventos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Junta as fontes da V45 em selos de confiabilidade por ativo (FONTES-GES-4). */
@Service
public class ServicoConfiabilidade {

    /** Troca de auditor dentro desta janela vira ALERTA. */
    static final int MESES_TROCA_AUDITOR = 24;
    /** Percentual da divida de valores mobiliarios vencendo em 12 meses acima do qual o selo e ALERTA. */
    static final BigDecimal LIMIAR_DIVIDA_12M_PCT = new BigDecimal("50");
    /** Payout (proventos / lucro liquido) acima deste percentual vira ALERTA. */
    static final BigDecimal LIMIAR_PAYOUT_PCT = new BigDecimal("100");

    private final RepositorioConfiabilidade repo;
    private final Clock relogio;

    @Autowired
    public ServicoConfiabilidade(RepositorioConfiabilidade repo) {
        this(repo, Clock.systemDefaultZone());
    }

    ServicoConfiabilidade(RepositorioConfiabilidade repo, Clock relogio) {
        this.repo = repo;
        this.relogio = relogio;
    }

    public ConfiabilidadeDTO confiabilidade(String simboloBruto) {
        String simbolo = simboloBruto == null ? "" : simboloBruto.trim().toUpperCase(Locale.ROOT);
        Set<String> existentes = repo.tabelasV45Existentes();
        if (existentes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O banco ainda não tem as fontes públicas (migration V45)");
        }
        String cnpj = repo.cnpjPorSimbolo(simbolo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo não encontrado: " + simbolo));
        LocalDate hoje = LocalDate.now(relogio);
        List<Selo> selos = new ArrayList<>();
        selos.add(tabela(existentes, "cvm_cadastro", "CADASTRO", "Cadastro na CVM", () -> cadastro(cnpj)));
        selos.add(tabela(existentes, "cvm_auditor", "AUDITOR", "Auditor", () -> auditor(cnpj, hoje)));
        selos.add(tabela(existentes, "cvm_divida_valor_mobiliario", "DIVIDA", "Vencimento da dívida",
                () -> divida(cnpj, hoje)));
        selos.add(tabela(existentes, "fundo_posicao_acao", "FUNDOS", "Fundos com o ativo",
                () -> fundos(simbolo, hoje)));
        selos.add(dividendo(simbolo));
        return new ConfiabilidadeDTO(simbolo, cnpj, resumo(selos), List.copyOf(selos));
    }

    private Selo tabela(Set<String> existentes, String tabela, String id, String titulo, Supplier<Selo> montar) {
        if (!existentes.contains(tabela)) {
            return new Selo(id, Nivel.SEM_DADO, titulo, "Tabela " + tabela + " ausente no banco");
        }
        try {
            return montar.get();
        } catch (DataAccessException e) {
            return new Selo(id, Nivel.SEM_DADO, titulo, "Fonte indisponível no momento");
        }
    }

    Selo cadastro(String cnpj) {
        String titulo = "Cadastro na CVM";
        var opt = repo.cadastro(cnpj);
        if (opt.isEmpty() || opt.get().situacao() == null || opt.get().situacao().isBlank()) {
            return new Selo("CADASTRO", Nivel.SEM_DADO, titulo, "Empresa sem registro em cvm_cadastro");
        }
        Cadastro c = opt.get();
        String sit = c.situacao().trim();
        String norm = sit.toUpperCase(Locale.ROOT);
        StringBuilder d = new StringBuilder("Situação: ").append(sit);
        if (c.dataSituacao() != null) d.append(" desde ").append(c.dataSituacao());
        if (c.dataCancelamento() != null) d.append("; cancelada em ").append(c.dataCancelamento());
        if (c.motivoCancelamento() != null && !c.motivoCancelamento().isBlank()) {
            d.append("; motivo: ").append(c.motivoCancelamento().trim());
        }
        Nivel nivel;
        if (norm.equals("ATIVO")) nivel = Nivel.OK;
        else if (norm.startsWith("SUSPENS") || norm.startsWith("CANCELAD")) nivel = Nivel.CRITICO;
        else nivel = Nivel.ALERTA;
        return new Selo("CADASTRO", nivel, titulo, d.toString());
    }

    Selo auditor(String cnpj, LocalDate hoje) {
        String titulo = "Auditor";
        List<Auditor> lista = repo.auditores(cnpj);
        if (lista.isEmpty()) {
            return new Selo("AUDITOR", Nivel.SEM_DADO, titulo, "Sem auditor informado em cvm_auditor");
        }
        Auditor atual = lista.get(0);
        Auditor anterior = lista.stream()
                .filter(a -> !Objects.equals(a.cnpjAuditor(), atual.cnpjAuditor())).findFirst().orElse(null);
        boolean troca = anterior != null && atual.dataInicio() != null
                && !atual.dataInicio().isBefore(hoje.minusMonths(MESES_TROCA_AUDITOR));
        if (!troca) {
            return new Selo("AUDITOR", Nivel.OK, titulo, "Auditor atual: " + nome(atual)
                    + " (desde " + atual.dataInicio() + "); sem troca nos últimos " + MESES_TROCA_AUDITOR + " meses");
        }
        StringBuilder d = new StringBuilder("Troca de auditor em ").append(atual.dataInicio())
                .append(": atual ").append(nome(atual)).append(", anterior ").append(nome(anterior));
        if (atual.justificativa() != null && !atual.justificativa().isBlank()) {
            d.append("; justificativa: ").append(atual.justificativa().trim());
        }
        return new Selo("AUDITOR", Nivel.ALERTA, titulo, d.toString());
    }

    private static String nome(Auditor a) {
        return a.nome() == null || a.nome().isBlank() ? a.cnpjAuditor() : a.nome().trim();
    }

    Selo divida(String cnpj, LocalDate hoje) {
        String titulo = "Vencimento da dívida";
        var opt = repo.divida(cnpj, hoje);
        if (opt.isEmpty() || opt.get().saldoTotal() == null || opt.get().saldoTotal().signum() <= 0) {
            return new Selo("DIVIDA", Nivel.SEM_DADO, titulo, "Sem dívida de valores mobiliários com saldo informado");
        }
        Divida v = opt.get();
        BigDecimal ate12 = v.saldoAte12Meses() == null ? BigDecimal.ZERO : v.saldoAte12Meses();
        BigDecimal pct = ate12.multiply(BigDecimal.valueOf(100)).divide(v.saldoTotal(), 1, RoundingMode.HALF_UP);
        Nivel nivel = pct.compareTo(LIMIAR_DIVIDA_12M_PCT) > 0 ? Nivel.ALERTA : Nivel.OK;
        return new Selo("DIVIDA", nivel, titulo, pct.toPlainString() + "% do saldo devedor ("
                + ate12.setScale(2, RoundingMode.HALF_UP).toPlainString() + " de "
                + v.saldoTotal().setScale(2, RoundingMode.HALF_UP).toPlainString() + ") vence em até 12 meses; "
                + v.titulos() + " título(s), referência " + v.dataReferencia());
    }

    Selo fundos(String simbolo, LocalDate hoje) {
        String titulo = "Fundos com o ativo";
        List<CompetenciaFundos> comps = repo.competenciasFundos(simbolo);
        if (comps.isEmpty()) {
            return new Selo("FUNDOS", Nivel.SEM_DADO, titulo, "Nenhum fundo (CDA) com o ativo em carteira");
        }
        CompetenciaFundos atual = comps.get(0);
        long defasagem = ChronoUnit.MONTHS.between(atual.competencia().withDayOfMonth(1), hoje.withDayOfMonth(1));
        StringBuilder d = new StringBuilder(String.valueOf(atual.fundos())).append(" fundo(s) em ")
                .append(atual.competencia()).append(" (defasagem de ").append(defasagem).append(" mês(es)); quantidade ")
                .append(atual.quantidade().stripTrailingZeros().toPlainString());
        if (comps.size() > 1) {
            CompetenciaFundos ant = comps.get(1);
            MovimentoFundos mov = repo.movimentoFundos(simbolo, atual.competencia(), ant.competencia());
            d.append("; contra ").append(ant.competencia()).append(": ").append(mov.entraram()).append(" entraram, ")
                    .append(mov.sairam()).append(" saíram, fundos ")
                    .append(sinal(BigDecimal.valueOf(atual.fundos() - ant.fundos())))
                    .append(", quantidade ").append(sinal(atual.quantidade().subtract(ant.quantidade())));
        } else {
            d.append("; sem competência anterior para comparar");
        }
        return new Selo("FUNDOS", Nivel.OK, titulo, d.toString());
    }

    private static String sinal(BigDecimal v) {
        String txt = v.stripTrailingZeros().toPlainString();
        return v.signum() > 0 ? "+" + txt : txt;
    }

    Selo dividendo(String simbolo) {
        String titulo = "Sustentabilidade do dividendo";
        var opt = repo.proventosEPayout(simbolo);
        if (opt.isEmpty() || opt.get().total() == null) {
            return new Selo("DIVIDENDO", Nivel.SEM_DADO, titulo, "Sem proventos contábeis (DFP) para o ativo");
        }
        Proventos p = opt.get();
        if (p.lucroLiquido() == null) {
            return new Selo("DIVIDENDO", Nivel.SEM_DADO, titulo,
                    "Sem lucro líquido anual de " + p.periodo() + " para calcular o payout");
        }
        if (p.lucroLiquido().signum() <= 0) {
            if (p.total().signum() > 0) {
                return new Selo("DIVIDENDO", Nivel.ALERTA, titulo,
                        "Proventos pagos em " + p.periodo() + " com lucro líquido não positivo");
            }
            return new Selo("DIVIDENDO", Nivel.SEM_DADO, titulo,
                    "Lucro líquido não positivo em " + p.periodo() + " e sem proventos");
        }
        BigDecimal payout = p.total().multiply(BigDecimal.valueOf(100))
                .divide(p.lucroLiquido(), 1, RoundingMode.HALF_UP);
        Nivel nivel = payout.compareTo(LIMIAR_PAYOUT_PCT) > 0 ? Nivel.ALERTA : Nivel.OK;
        return new Selo("DIVIDENDO", nivel, titulo,
                "Payout de " + payout.toPlainString() + "% do lucro líquido no exercício " + p.periodo());
    }

    /** Pior nivel: CRITICO > ALERTA > OK; SEM_DADO so quando nenhum selo tem dado. */
    Resumo resumo(List<Selo> selos) {
        List<Selo> criticos = selos.stream().filter(s -> s.nivel() == Nivel.CRITICO).toList();
        List<Selo> alertas = selos.stream().filter(s -> s.nivel() == Nivel.ALERTA).toList();
        long semDado = selos.stream().filter(s -> s.nivel() == Nivel.SEM_DADO).count();
        if (semDado == selos.size()) {
            return new Resumo(Nivel.SEM_DADO, "Sem dados nas fontes públicas para avaliar a confiabilidade.");
        }
        String complemento = semDado > 0 ? " " + semDado + " selo(s) sem dado." : "";
        if (!criticos.isEmpty()) {
            return new Resumo(Nivel.CRITICO, "Atenção crítica: " + ids(criticos)
                    + (alertas.isEmpty() ? "" : "; alertas em " + ids(alertas)) + "." + complemento);
        }
        if (!alertas.isEmpty()) {
            return new Resumo(Nivel.ALERTA, "Pontos de atenção em " + ids(alertas) + "." + complemento);
        }
        return new Resumo(Nivel.OK, "Nenhum ponto de atenção nas fontes consultadas." + complemento);
    }

    private static String ids(List<Selo> selos) {
        return String.join(", ", selos.stream().map(Selo::id).toList());
    }
}
