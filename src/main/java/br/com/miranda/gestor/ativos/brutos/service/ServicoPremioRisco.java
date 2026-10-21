package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.CurvaRealDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.PremioRiscoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioTitulosPublicos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Curva real (NTN-B) e premio de risco do ativo contra a NTN-B de referencia (FONTES-GES-1). */
@Service
@RequiredArgsConstructor
public class ServicoPremioRisco {

    private final RepositorioTitulosPublicos repositorio;

    public CurvaRealDTO curvaReal() {
        exigirSchema();
        Optional<LocalDate> base = repositorio.dataBaseMaisRecente();
        if (base.isEmpty()) {
            return new CurvaRealDTO(null, null, List.of(), "Sem NTN-B em titulo_publico_taxa");
        }
        List<CurvaRealDTO.Ponto> pontos = repositorio.ntnbPorVencimento(base.get());
        return new CurvaRealDTO(base.get(), referencia(base.get(), pontos).orElse(null), pontos,
                pontos.isEmpty() ? "Sem NTN-B na data-base" : null);
    }

    /** Vencimento mais perto de data-base + 5 anos (DEC-FONTES-3); empate = vencimento menor. */
    static Optional<CurvaRealDTO.Ponto> referencia(LocalDate dataBase, List<CurvaRealDTO.Ponto> pontos) {
        LocalDate alvo = dataBase.plusYears(5);
        return pontos.stream()
                .filter(p -> p.taxaCompra() != null)
                .min(Comparator.<CurvaRealDTO.Ponto>comparingLong(p -> Math.abs(ChronoUnit.DAYS.between(alvo, p.vencimento())))
                        .thenComparing(CurvaRealDTO.Ponto::vencimento));
    }

    public PremioRiscoDTO premioRisco(String simbolo) {
        exigirSchema();
        String s = simbolo.toUpperCase(Locale.ROOT);
        Optional<BigDecimal> ey = repositorio.earningsYield(s);
        Optional<LocalDate> base = repositorio.dataBaseMaisRecente();
        Optional<CurvaRealDTO.Ponto> ref = base.flatMap(b -> referencia(b, repositorio.ntnbPorVencimento(b)));
        BigDecimal selic = repositorio.selicMeta().orElse(null);
        BigDecimal juro = ref.map(CurvaRealDTO.Ponto::taxaCompra).orElse(null);
        LocalDate venc = ref.map(CurvaRealDTO.Ponto::vencimento).orElse(null);
        LocalDate dataBase = base.orElse(null);

        if (ey.isEmpty() || juro == null) {
            String motivo = ey.isEmpty() && juro == null ? "Sem insight do ativo e sem NTN-B na tabela"
                    : ey.isEmpty() ? "Ativo sem insight com earnings yield" : "Sem NTN-B de referencia na tabela";
            return new PremioRiscoDTO(s, ey.orElse(null), juro, venc, dataBase, null, null, null, selic, null, motivo);
        }
        BigDecimal premio = ey.get().subtract(juro);
        String veredito = premio.signum() > 0 ? "ACIMA_DA_RENDA_FIXA"
                : premio.signum() < 0 ? "ABAIXO_DA_RENDA_FIXA" : "IGUAL_A_RENDA_FIXA";
        BigDecimal sobreSelic = selic == null ? null : ey.get().subtract(selic).setScale(2, RoundingMode.HALF_UP);
        String leitura = "Earnings yield de %s%% contra %s%% da NTN-B %d: %s de %s p.p.".formatted(
                fmt(ey.get(), 2), fmt(juro, 2), venc.getYear(),
                premio.signum() >= 0 ? "prêmio" : "prêmio negativo", fmt(premio.abs(), 1));
        return new PremioRiscoDTO(s, ey.get(), juro, venc, dataBase, premio.setScale(2, RoundingMode.HALF_UP),
                veredito, leitura, selic, sobreSelic, null);
    }

    private static String fmt(BigDecimal v, int casas) {
        DecimalFormat f = new DecimalFormat("0." + "#".repeat(casas), DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(v);
    }

    private void exigirSchema() {
        if (!repositorio.schemaDisponivel()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O banco ainda não tem os títulos públicos (migration V45)");
        }
    }
}
