package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.InsidersDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsidersDTO.Agregado;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsidersDTO.Janela;
import br.com.miranda.gestor.ativos.brutos.external.dto.RecomprasDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioInsiders;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioInsiders.Negociacao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioInsiders.SaldoTesouraria;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Sinal de insiders (VLMO) e recompras (tesouraria do BPP) - FONTES-GES-3. */
@Service
public class ServicoInsiders {

    public static final List<Integer> JANELAS_MESES = List.of(3, 6, 12);
    /** Janela (meses) que decide o sinal. */
    static final int JANELA_SINAL_MESES = 6;
    /** |compra liquida| / (compras + vendas), em quantidade; abaixo disso o sinal e NEUTRO. */
    static final BigDecimal LIMIAR_SINAL = new BigDecimal("0.10");
    public static final List<String> CARGOS = List.of("Diretor", "Conselho de Administração", "Controlador",
            "Conselho Fiscal", "Órgão Estatutário");
    static final int ANOS_TESOURARIA = 5;

    private final RepositorioInsiders repositorio;
    private final RepositorioIdentidadeAtivo identidade;

    public ServicoInsiders(RepositorioInsiders repositorio, RepositorioIdentidadeAtivo identidade) {
        this.repositorio = repositorio;
        this.identidade = identidade;
    }

    public InsidersDTO insiders(String simbolo) {
        return insiders(simbolo, LocalDate.now());
    }

    InsidersDTO insiders(String simbolo, LocalDate hoje) {
        if (!repositorio.schemaDisponivel()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O banco ainda não tem as negociações de administradores (migration V45)");
        }
        String s = normalizarSimbolo(simbolo);
        String cnpj = repositorio.cnpj(identidade.canonico(s)).orElse(null);
        if (cnpj == null) {
            return vazio(s, null, "Ticker sem CNPJ em cvm_ticker");
        }
        List<Negociacao> validas = repositorio.negociacoes(cnpj, hoje.minusMonths(12)).stream()
                .filter(n -> n.dataMovimentacao() != null && !n.dataMovimentacao().isAfter(hoje))
                .filter(n -> tipoMovimento(n.movimentacao()) != 0 && ativoValido(n.ativo()))
                .toList();
        if (validas.isEmpty()) {
            return vazio(s, cnpj, "Sem negociações de administradores nos últimos 12 meses");
        }
        List<Janela> janelas = new ArrayList<>();
        for (int meses : JANELAS_MESES) {
            LocalDate desde = hoje.minusMonths(meses);
            List<Negociacao> da = validas.stream().filter(n -> !n.dataMovimentacao().isBefore(desde)).toList();
            Map<String, Agregado> porCargo = new LinkedHashMap<>();
            for (String cargo : CARGOS) {
                porCargo.put(cargo, agregar(da.stream().filter(n -> cargo.equals(cargo(n.cargo()))).toList()));
            }
            janelas.add(new Janela(meses, agregar(da), porCargo));
        }
        Agregado base = janelas.stream().filter(j -> j.meses() == JANELA_SINAL_MESES).findFirst().orElseThrow().total();
        String sinal = sinal(base);
        LocalDate ultima = validas.stream().map(Negociacao::dataMovimentacao).max(LocalDate::compareTo).orElse(null);
        LocalDate entrega = validas.stream().map(Negociacao::dataEntrega).filter(d -> d != null)
                .max(LocalDate::compareTo).orElse(null);
        return new InsidersDTO(s, cnpj, true, null, sinal, leitura(sinal, base), ultima, entrega, janelas);
    }

    public RecomprasDTO recompras(String simbolo) {
        return recompras(simbolo, LocalDate.now());
    }

    RecomprasDTO recompras(String simbolo, LocalDate hoje) {
        String s = normalizarSimbolo(simbolo);
        String cnpj = repositorio.cnpj(identidade.canonico(s)).orElse(null);
        if (cnpj == null) {
            return indisponivel(s, null, "Ticker sem CNPJ em cvm_ticker");
        }
        List<SaldoTesouraria> saldos;
        try {
            saldos = repositorio.saldosTesouraria(cnpj, hoje.minusYears(ANOS_TESOURARIA));
        } catch (org.springframework.dao.DataAccessException e) {
            saldos = List.of();
        }
        // uma linha por data-base: consolidado (con) prevalece sobre individual
        TreeMap<LocalDate, SaldoTesouraria> porData = new TreeMap<>();
        for (SaldoTesouraria x : saldos) {
            SaldoTesouraria atual = porData.get(x.dataBase());
            if (atual == null || ("con".equals(x.grupo()) && !"con".equals(atual.grupo()))) {
                porData.put(x.dataBase(), x);
            }
        }
        if (porData.isEmpty()) {
            return indisponivel(s, cnpj, "contas de tesouraria não persistidas; depende de FONTES-ETL-8");
        }
        List<RecomprasDTO.Ponto> serie = new ArrayList<>();
        BigDecimal anterior = null;
        BigDecimal acumulado12m = BigDecimal.ZERO;
        LocalDate corte = hoje.minusMonths(12);
        for (SaldoTesouraria x : porData.values()) {
            BigDecimal variacao = anterior == null ? null : x.saldo().subtract(anterior);
            BigDecimal recompra = variacao == null ? null : variacao.negate();
            serie.add(new RecomprasDTO.Ponto(x.dataBase(), x.tipoDoc(), x.saldo(), variacao, recompra));
            if (recompra != null && x.dataBase().isAfter(corte)) {
                acumulado12m = acumulado12m.add(recompra);
            }
            anterior = x.saldo();
        }
        return new RecomprasDTO(s, cnpj, true, null, acumulado12m, serie);
    }

    // --- regras --------------------------------------------------------------------------

    /** +1 compra (a vista ou a termo), -1 venda a vista, 0 ignorar. */
    static int tipoMovimento(String movimentacao) {
        String m = norm(movimentacao);
        if (m.equals("compra a vista") || m.equals("compra a termo")) return 1;
        if (m.equals("venda a vista")) return -1;
        return 0;
    }

    static boolean ativoValido(String ativo) {
        String a = norm(ativo);
        return a.equals("acoes") || a.equals("units") || a.equals("unit");
    }

    static String cargo(String tipoCargo) {
        String c = norm(tipoCargo);
        if (c.startsWith("diretor")) return "Diretor";
        if (c.startsWith("conselho de administracao")) return "Conselho de Administração";
        if (c.startsWith("controlador")) return "Controlador";
        if (c.startsWith("conselho fiscal")) return "Conselho Fiscal";
        if (c.startsWith("orgao estatutario")) return "Órgão Estatutário";
        return null;
    }

    static Agregado agregar(List<Negociacao> linhas) {
        BigDecimal compras = BigDecimal.ZERO;
        BigDecimal vendas = BigDecimal.ZERO;
        BigDecimal volume = null;
        for (Negociacao n : linhas) {
            int sentido = tipoMovimento(n.movimentacao());
            BigDecimal q = n.quantidade() == null ? BigDecimal.ZERO : n.quantidade().abs();
            if (sentido > 0) compras = compras.add(q); else vendas = vendas.add(q);
            if (n.volume() != null && n.volume().signum() > 0) {
                BigDecimal v = sentido > 0 ? n.volume() : n.volume().negate();
                volume = volume == null ? v : volume.add(v);
            }
        }
        return new Agregado(compras, vendas, compras.subtract(vendas), volume, linhas.size());
    }

    static String sinal(Agregado a) {
        BigDecimal bruto = a.quantidadeCompras().add(a.quantidadeVendas());
        if (bruto.signum() == 0) return "NEUTRO";
        BigDecimal razao = a.compraLiquidaQuantidade().divide(bruto, 4, RoundingMode.HALF_UP);
        if (razao.compareTo(LIMIAR_SINAL) >= 0) return "COMPRA_LIQUIDA";
        if (razao.compareTo(LIMIAR_SINAL.negate()) <= 0) return "VENDA_LIQUIDA";
        return "NEUTRO";
    }

    private static String leitura(String sinal, Agregado a) {
        String base = "Nos últimos " + JANELA_SINAL_MESES + " meses, ";
        return switch (sinal) {
            case "COMPRA_LIQUIDA" -> base + "administradores e controladores compraram mais do que venderam (líquido de "
                    + a.compraLiquidaQuantidade().stripTrailingZeros().toPlainString() + " ações).";
            case "VENDA_LIQUIDA" -> base + "administradores e controladores venderam mais do que compraram (líquido de "
                    + a.compraLiquidaQuantidade().negate().stripTrailingZeros().toPlainString() + " ações vendidas).";
            default -> base + "compras e vendas de administradores e controladores ficaram equilibradas (sem sinal claro).";
        };
    }

    private static InsidersDTO vazio(String simbolo, String cnpj, String motivo) {
        List<Janela> janelas = new ArrayList<>();
        for (int meses : JANELAS_MESES) {
            Map<String, Agregado> porCargo = new LinkedHashMap<>();
            CARGOS.forEach(c -> porCargo.put(c, agregar(List.of())));
            janelas.add(new Janela(meses, agregar(List.of()), porCargo));
        }
        return new InsidersDTO(simbolo, cnpj, false, motivo, "NEUTRO", "Sem dados de insiders para leitura.", null,
                null, janelas);
    }

    private static RecomprasDTO indisponivel(String simbolo, String cnpj, String motivo) {
        return new RecomprasDTO(simbolo, cnpj, false, motivo, null, List.of());
    }

    private static String normalizarSimbolo(String simbolo) {
        return simbolo == null ? "" : simbolo.trim().toUpperCase(Locale.ROOT);
    }

    /** Minusculas, sem acentos; repara texto UTF-8 lido como latin-1 (mojibake). */
    static String norm(String texto) {
        if (texto == null) return "";
        String t = texto.trim();
        if (t.contains("Ã") || t.contains("Â")) {
            t = new String(t.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        }
        t = t.replace('�', ' ');
        return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
