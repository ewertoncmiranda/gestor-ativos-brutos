package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.BancoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.BancoDTO.Selo;
import br.com.miranda.gestor.ativos.brutos.external.dto.BancoDTO.Trimestre;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioBancoIndicadores;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Indicadores de banco (FONTES-GES-2, DEC-FONTES-1): bancos sao avaliados por
 * P/VP x ROE e Basileia, nao por Graham. Selos deterministicos, sem IA.
 */
@Service
public class ServicoBanco {

    /** Minimo regulatorio de Basileia incluindo adicional de conservacao (10,5%). */
    static final BigDecimal BASILEIA_MINIMA = new BigDecimal("10.5");
    /** Alta de inadimplencia (p.p.) em 4 trimestres que dispara alerta. Escolha nossa; revisar. */
    static final BigDecimal INADIMPLENCIA_ALTA_PP = new BigDecimal("0.5");
    static final int TRIMESTRES_COMPARACAO = 4;
    static final int TRIMESTRES_SERIE = 8;

    private final RepositorioBancoIndicadores repositorio;
    private final ServicoFundamentosCvm fundamentos;

    public ServicoBanco(RepositorioBancoIndicadores repositorio, ServicoFundamentosCvm fundamentos) {
        this.repositorio = repositorio;
        this.fundamentos = fundamentos;
    }

    public BancoDTO buscar(String simboloBruto) {
        String simbolo = simboloBruto == null ? "" : simboloBruto.trim().toUpperCase(Locale.ROOT);
        if (!repositorio.schemaDisponivel()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O banco ainda não tem as tabelas de fontes públicas (migration V45)");
        }
        Optional<String> cnpj = repositorio.cnpjDoSimbolo(simbolo);
        if (cnpj.isEmpty()) {
            return BancoDTO.naoBanco(simbolo, null, "Símbolo sem CNPJ conhecido (cvm_ticker)");
        }
        Optional<String> setor = repositorio.setorCvm(cnpj.get());
        if (setor.isEmpty() || !setorBancario(setor.get())) {
            return BancoDTO.naoBanco(simbolo, setor.orElse(null), "Não é instituição bancária pelo setor da CVM");
        }
        List<Trimestre> recentes = repositorio.ultimosTrimestres(cnpj.get(), TRIMESTRES_SERIE);
        BigDecimal pvp = pvp(simbolo);
        String pvpMotivo = pvp == null ? "P/VP indisponível (sem cotação ou VPA nos fundamentos CVM)" : null;
        if (recentes.isEmpty()) {
            return new BancoDTO(simbolo, true, setor.get(), "Sem dados do IF.data para este banco",
                    null, List.of(), pvp, pvpMotivo, null, null, null);
        }
        Trimestre ultimo = recentes.get(0);
        List<Trimestre> serie = new ArrayList<>(recentes);
        Collections.reverse(serie);
        BigDecimal roeSobrePvp = ultimo.roe() != null && pvp != null && pvp.signum() > 0
                ? ultimo.roe().divide(pvp, 4, RoundingMode.HALF_UP) : null;
        List<Selo> selos = List.of(seloBasileia(ultimo), seloInadimplencia(recentes),
                seloRoe(ultimo, repositorio.selicMeta().orElse(null)));
        return new BancoDTO(simbolo, true, setor.get(), null, ultimo, serie, pvp, pvpMotivo,
                roeSobrePvp, selos, leituraGeral(selos, roeSobrePvp));
    }

    /** SETOR_ATIV da CVM: "Bancos" e "Emp. Adm. Part. - Bancos" (holdings, ex. Itau). */
    static boolean setorBancario(String setor) {
        String s = Normalizer.normalize(setor, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return s.contains("banco");
    }

    private BigDecimal pvp(String simbolo) {
        try {
            return fundamentos.buscarPorSimbolo(simbolo).getPrecoValorPatrimonial();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Selo seloBasileia(Trimestre t) {
        if (t.basileia() == null) {
            return new Selo("BASILEIA", "SEM_DADO", null, BASILEIA_MINIMA, "Basileia indisponível no trimestre.");
        }
        boolean alerta = t.basileia().compareTo(BASILEIA_MINIMA) < 0;
        return new Selo("BASILEIA", alerta ? "ALERTA" : "OK", t.basileia(), BASILEIA_MINIMA, alerta
                ? "Basileia de " + t.basileia() + "% abaixo do mínimo regulatório de " + BASILEIA_MINIMA + "%."
                : "Basileia de " + t.basileia() + "% acima do mínimo regulatório de " + BASILEIA_MINIMA + "%.");
    }

    static Selo seloInadimplencia(List<Trimestre> recentesPrimeiro) {
        Trimestre atual = recentesPrimeiro.get(0);
        if (recentesPrimeiro.size() <= TRIMESTRES_COMPARACAO || atual.inadimplencia() == null
                || recentesPrimeiro.get(TRIMESTRES_COMPARACAO).inadimplencia() == null) {
            return new Selo("INADIMPLENCIA", "SEM_DADO", atual.inadimplencia(), null,
                    "Histórico insuficiente para comparar a inadimplência em 4 trimestres.");
        }
        BigDecimal delta = atual.inadimplencia().subtract(recentesPrimeiro.get(TRIMESTRES_COMPARACAO).inadimplencia());
        boolean alerta = delta.compareTo(INADIMPLENCIA_ALTA_PP) > 0;
        return new Selo("INADIMPLENCIA", alerta ? "ALERTA" : "OK", delta, INADIMPLENCIA_ALTA_PP, alerta
                ? "Inadimplência subiu " + delta + " p.p. em 4 trimestres (limite " + INADIMPLENCIA_ALTA_PP + " p.p.)."
                : "Inadimplência variou " + delta + " p.p. em 4 trimestres, dentro do limite de "
                + INADIMPLENCIA_ALTA_PP + " p.p.");
    }

    static Selo seloRoe(Trimestre t, BigDecimal selic) {
        if (t.roe() == null || selic == null) {
            return new Selo("ROE", "SEM_DADO", t.roe(), selic,
                    "ROE ou Selic meta indisponível para comparar o custo de capital.");
        }
        boolean acima = t.roe().compareTo(selic) >= 0;
        return new Selo("ROE", acima ? "ACIMA" : "ABAIXO", t.roe(), selic, acima
                ? "ROE de " + t.roe() + "% acima do custo de capital de referência (Selic meta " + selic + "%)."
                : "ROE de " + t.roe() + "% abaixo do custo de capital de referência (Selic meta " + selic + "%).");
    }

    static String leituraGeral(List<Selo> selos, BigDecimal roeSobrePvp) {
        long alertas = selos.stream().filter(s -> "ALERTA".equals(s.status()) || "ABAIXO".equals(s.status())).count();
        String base = alertas == 0 ? "Nenhum selo em alerta."
                : alertas + (alertas == 1 ? " selo exige atenção." : " selos exigem atenção.");
        String rel = roeSobrePvp == null ? " ROE/P/VP indisponível."
                : " ROE/P/VP de " + roeSobrePvp + " (maior indica mais retorno por unidade de preço pago sobre o patrimônio).";
        return base + rel;
    }
}
