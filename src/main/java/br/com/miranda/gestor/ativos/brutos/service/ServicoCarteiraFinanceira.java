package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraFinanceiraDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraOperacaoRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraProventoRequest;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCarteiraFinanceira;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCarteiras;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static java.math.BigDecimal.ZERO;

@Service
public class ServicoCarteiraFinanceira {
    private static final String VERSAO_PADRAO = "CARTDB-1";
    private static final Pattern SIMBOLO = Pattern.compile("^[A-Z0-9]{4,12}$");
    private static final Set<String> TIPOS_OPERACAO = Set.of("COMPRA", "VENDA", "APORTE", "RETIRADA", "TAXA", "IMPOSTO");
    private static final Set<String> TIPOS_PROVENTO = Set.of("DIVIDENDO", "JCP", "RENDIMENTO", "AMORTIZACAO", "OUTRO");
    private final RepositorioCarteiraFinanceira repositorio;
    private final RepositorioCarteiras repositorioCarteiras;

    public ServicoCarteiraFinanceira(RepositorioCarteiraFinanceira repositorio,
                                     RepositorioCarteiras repositorioCarteiras) {
        this.repositorio = repositorio;
        this.repositorioCarteiras = repositorioCarteiras;
    }

    public List<CarteiraFinanceiraDTO.Operacao> listarOperacoes(String carteiraId) {
        exigirCarteira(carteiraId);
        exigirV43();
        return repositorio.operacoes(carteiraId);
    }

    @Transactional
    public CarteiraFinanceiraDTO.Operacao registrarOperacao(String carteiraId, CarteiraOperacaoRequest request) {
        exigirCarteiraAtiva(carteiraId);
        exigirV43();
        CarteiraFinanceiraDTO.Operacao nova = montarOperacao(request);
        Optional<CarteiraFinanceiraDTO.Operacao> existente = repositorio.operacaoPorChave(carteiraId, nova.chaveIdempotencia());
        if (existente.isPresent()) return idempotenteOuConflito(existente.get(), nova);
        if ("VENDA".equals(nova.tipo()) && nova.quantidade().compareTo(quantidadeAtual(carteiraId, nova.simbolo(), nova.executadaEm())) > 0) {
            throw badRequest("A quantidade vendida excede a posição registrada no ledger");
        }
        try {
            repositorio.inserirOperacao(carteiraId, nova);
        } catch (DuplicateKeyException e) {
            return repositorio.operacaoPorChave(carteiraId, nova.chaveIdempotencia())
                    .map(o -> idempotenteOuConflito(o, nova)).orElseThrow(() -> e);
        }
        return nova;
    }

    @Transactional
    public CarteiraFinanceiraDTO.Operacao estornarOperacao(String carteiraId, String operacaoId, String chave) {
        exigirCarteiraAtiva(carteiraId);
        exigirV43();
        validarChave(chave);
        var porChave = repositorio.operacaoPorChave(carteiraId, chave);
        if (porChave.isPresent()) {
            if ("ESTORNO".equals(porChave.get().tipo()) && Objects.equals(operacaoId, porChave.get().operacaoReferenciadaId())) return porChave.get();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chave de idempotência já usada por outra operação");
        }
        var original = repositorio.operacao(carteiraId, operacaoId)
                .orElseThrow(() -> notFound("Operação não encontrada"));
        if ("ESTORNO".equals(original.tipo())) throw badRequest("Não é permitido estornar outro estorno");
        if ("COMPRA".equals(original.tipo()) && original.quantidade().compareTo(
                quantidadeAtual(carteiraId, original.simbolo(), LocalDateTime.now())) > 0) {
            throw badRequest("Não é possível estornar a compra: parte da quantidade já foi consumida por vendas");
        }
        var estorno = new CarteiraFinanceiraDTO.Operacao(UUID.randomUUID().toString(), "ESTORNO", original.id(),
                original.simbolo(), LocalDateTime.now(), original.quantidade(), original.precoUnitario(),
                original.valorBruto(), original.custos(), original.impostos(), original.valorLiquido().negate(),
                original.moeda(), "MANUAL", chave.trim());
        try {
            repositorio.inserirOperacao(carteiraId, estorno);
        } catch (DuplicateKeyException e) {
            var repetido = repositorio.operacaoPorChave(carteiraId, chave.trim());
            if (repetido.isPresent() && Objects.equals(operacaoId, repetido.get().operacaoReferenciadaId())) return repetido.get();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operação já possui estorno ou chave em conflito");
        }
        return estorno;
    }

    public List<CarteiraFinanceiraDTO.Provento> listarProventos(String carteiraId) {
        exigirCarteira(carteiraId);
        exigirV43();
        return repositorio.proventos(carteiraId);
    }

    @Transactional
    public CarteiraFinanceiraDTO.Provento registrarProvento(String carteiraId, CarteiraProventoRequest request) {
        exigirCarteiraAtiva(carteiraId);
        exigirV43();
        var novo = montarProvento(request);
        var existente = repositorio.proventoPorChave(carteiraId, novo.chaveIdempotencia());
        if (existente.isPresent()) return idempotenteOuConflito(existente.get(), novo);
        try {
            repositorio.inserirProvento(carteiraId, novo);
        } catch (DuplicateKeyException e) {
            return repositorio.proventoPorChave(carteiraId, novo.chaveIdempotencia())
                    .map(p -> idempotenteOuConflito(p, novo)).orElseThrow(() -> e);
        }
        return novo;
    }

    @Transactional
    public CarteiraFinanceiraDTO.Snapshot calcularSnapshot(String carteiraId, LocalDate data, String versao) {
        exigirCarteiraAtiva(carteiraId);
        exigirV43();
        if (data == null || data.isAfter(LocalDate.now())) throw badRequest("dataReferencia inválida ou futura");
        String versaoCalculo = validarVersao(versao);
        List<CarteiraFinanceiraDTO.Operacao> operacoes = repositorio.operacoes(carteiraId).stream()
                .filter(o -> !o.executadaEm().toLocalDate().isAfter(data)).toList();
        List<CarteiraFinanceiraDTO.Provento> proventos = repositorio.proventos(carteiraId).stream()
                .filter(p -> !p.dataPagamento().isAfter(data)).toList();

        var posicoes = calcularPosicoes(operacoes);
        LocalDate pregaoReferencia = repositorio.ultimoPregao(data).orElse(null);
        if (pregaoReferencia == null || !pregaoReferencia.equals(data)) {
            throw badRequest("Snapshot só pode ser gerado para uma data com pregão COTAHIST disponível");
        }
        List<CarteiraFinanceiraDTO.AvaliacaoAtivo> avaliacoes = new ArrayList<>();
        List<String> semCotacao = new ArrayList<>();
        BigDecimal valorMercado = ZERO;
        BigDecimal custoRemanescentePrecificado = ZERO;
        for (var entry : posicoes.entrySet()) {
            Posicao posicao = entry.getValue();
            if (posicao.quantidade().signum() <= 0) continue;
            Optional<RepositorioCarteiraFinanceira.Cotacao> cotacao = repositorio.cotacao(entry.getKey(), data);
            if (cotacao.isEmpty()) {
                semCotacao.add(entry.getKey());
                continue;
            }
            var q = cotacao.get();
            if (pregaoReferencia == null || !q.dataPregao().equals(pregaoReferencia)) {
                semCotacao.add(entry.getKey());
                continue;
            }
            BigDecimal mercado = posicao.quantidade().multiply(q.fechamento()).setScale(2, RoundingMode.HALF_UP);
            var avaliacao = new CarteiraFinanceiraDTO.AvaliacaoAtivo(entry.getKey(), posicao.quantidade(), q.fechamento(),
                    q.dataPregao(), "COTAHIST_B3", mercado, versaoCalculo);
            avaliacoes.add(avaliacao);
            repositorio.salvarAvaliacao(carteiraId, avaliacao, data);
            valorMercado = valorMercado.add(mercado);
            custoRemanescentePrecificado = custoRemanescentePrecificado.add(posicao.custoRemanescente());
        }
        BigDecimal saldoCaixa = operacoes.stream().map(CarteiraFinanceiraDTO.Operacao::valorLiquido).reduce(ZERO, BigDecimal::add)
                .add(proventos.stream().map(CarteiraFinanceiraDTO.Provento::valorLiquido).reduce(ZERO, BigDecimal::add));
        BigDecimal proventosLiquidos = proventos.stream().map(CarteiraFinanceiraDTO.Provento::valorLiquido).reduce(ZERO, BigDecimal::add);
        BigDecimal fluxoExterno = fluxoExterno(operacoes, data, anteriorData(carteiraId, data, versaoCalculo));
        BigDecimal realizado = posicoes.values().stream().map(Posicao::resultadoRealizado).reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal naoRealizado = semCotacao.isEmpty()
                ? valorMercado.subtract(custoRemanescentePrecificado).setScale(2, RoundingMode.HALF_UP) : ZERO.setScale(2);

        boolean possuiHistoricoFinanceiro = !operacoes.isEmpty() || !proventos.isEmpty();
        BigDecimal retornoPeriodo = null;
        BigDecimal retornoAcumulado = null;
        var anterior = repositorio.rentabilidadeAnterior(carteiraId, data, versaoCalculo);
        LocalDate pregaoAnterior = pregaoReferencia == null ? null
                : repositorio.ultimoPregao(pregaoReferencia.minusDays(1)).orElse(null);
        boolean coberturaCompleta = semCotacao.isEmpty() && possuiHistoricoFinanceiro;
        String metodo = coberturaCompleta ? "TWR" : "INDISPONIVEL";
        boolean referenciaContinua = anterior.isPresent() && Objects.equals(anterior.get().dataReferencia(), pregaoAnterior);
        if (coberturaCompleta && referenciaContinua && "TWR".equals(anterior.get().metodo())) {
            var prev = anterior.get();
            BigDecimal patrimonioAnterior = prev.valorMercado().add(prev.saldoCaixa());
            if (patrimonioAnterior.signum() > 0) {
                BigDecimal patrimonioAtual = valorMercado.add(saldoCaixa);
                retornoPeriodo = patrimonioAtual.subtract(patrimonioAnterior).subtract(fluxoExterno)
                        .divide(patrimonioAnterior, 10, RoundingMode.HALF_UP);
                if (retornoPeriodo.compareTo(BigDecimal.ONE.negate()) > 0) {
                    retornoAcumulado = prev.retornoAcumulado() == null ? retornoPeriodo
                            : prev.retornoAcumulado().add(BigDecimal.ONE).multiply(retornoPeriodo.add(BigDecimal.ONE))
                            .subtract(BigDecimal.ONE).setScale(10, RoundingMode.HALF_UP);
                } else {
                    retornoPeriodo = null;
                }
            }
        }
        var rentabilidade = new CarteiraFinanceiraDTO.Rentabilidade(data, "BRL", valorMercado, saldoCaixa,
                fluxoExterno, proventosLiquidos, realizado, naoRealizado, retornoPeriodo, retornoAcumulado,
                metodo, versaoCalculo, LocalDateTime.now());
        repositorio.salvarRentabilidade(carteiraId, rentabilidade);
        String observacao = !semCotacao.isEmpty()
                ? "Retorno indisponível: faltam preços oficiais atuais para parte das posições; valor de mercado e resultado não realizado são parciais. Alocações simuladas V42 são excluídas."
                : !possuiHistoricoFinanceiro
                ? "Retorno indisponível: não há operações nem recebimentos registrados. Alocações simuladas V42 não constituem posição."
                : !referenciaContinua && anterior.isPresent()
                ? "Retorno do período indisponível por lacuna entre snapshots; este snapshot estabelece nova base."
                : "Inclui somente operações e recebimentos registrados; alocações simuladas V42 não são posições. TWR usa fechamento oficial e fluxos externos líquidos por período; o primeiro snapshot estabelece a base sem retorno.";
        return new CarteiraFinanceiraDTO.Snapshot(data, versaoCalculo, List.copyOf(avaliacoes), List.copyOf(semCotacao), rentabilidade, observacao);
    }

    public List<CarteiraFinanceiraDTO.Rentabilidade> listarSnapshots(String carteiraId, LocalDate inicio,
                                                                      LocalDate fim, String versao) {
        exigirCarteira(carteiraId);
        exigirV43();
        if (inicio == null || fim == null || inicio.isAfter(fim)) throw badRequest("Informe período válido");
        return repositorio.rentabilidades(carteiraId, inicio, fim, validarVersao(versao));
    }

    public CarteiraFinanceiraDTO.Snapshot buscarSnapshot(String carteiraId, LocalDate data, String versao) {
        exigirCarteira(carteiraId);
        exigirV43();
        if (data == null) throw badRequest("dataReferencia é obrigatória");
        String calculo = validarVersao(versao);
        var rentabilidade = repositorio.rentabilidade(carteiraId, data, calculo)
                .orElseThrow(() -> notFound("Snapshot não encontrado"));
        var avaliacoes = repositorio.avaliacoes(carteiraId, data, calculo);
        var quantidades = calcularPosicoes(repositorio.operacoes(carteiraId).stream()
                .filter(o -> !o.executadaEm().toLocalDate().isAfter(data)).toList());
        Set<String> avaliados = new HashSet<>();
        avaliacoes.forEach(a -> avaliados.add(a.simbolo()));
        List<String> semCotacao = quantidades.entrySet().stream()
                .filter(e -> e.getValue().quantidade().signum() > 0 && !avaliados.contains(e.getKey()))
                .map(Map.Entry::getKey).toList();
        String observacao = semCotacao.isEmpty()
                ? "Inclui somente operações e recebimentos registrados; alocações simuladas V42 não são posições."
                : "Snapshot parcial: preços indisponíveis para " + String.join(", ", semCotacao) + "; retorno indisponível. Alocações V42 estão excluídas.";
        return new CarteiraFinanceiraDTO.Snapshot(data, calculo, avaliacoes, semCotacao, rentabilidade, observacao);
    }

    private CarteiraFinanceiraDTO.Operacao montarOperacao(CarteiraOperacaoRequest r) {
        if (r == null || r.executadaEm() == null) throw badRequest("executadaEm é obrigatório");
        if (r.executadaEm().isAfter(LocalDateTime.now())) throw badRequest("executadaEm não pode estar no futuro");
        String tipo = token(r.tipo());
        if (!TIPOS_OPERACAO.contains(tipo)) throw badRequest("tipo deve ser COMPRA, VENDA, APORTE, RETIRADA, TAXA ou IMPOSTO");
        validarChave(r.chaveIdempotencia());
        String moeda = moeda(r.moeda());
        BigDecimal custos = r.custos() == null ? new BigDecimal("0.00") : dinheiro(r.custos(), "custos", true);
        BigDecimal impostos = r.impostos() == null ? new BigDecimal("0.00") : dinheiro(r.impostos(), "impostos", true);
        String simbolo = null;
        BigDecimal quantidade = null, preco = null, bruto;
        if (tipo.equals("COMPRA") || tipo.equals("VENDA")) {
            simbolo = simbolo(r.simbolo());
            quantidade = positivo(r.quantidade(), "quantidade", 8, 24);
            preco = positivo(r.precoUnitario(), "precoUnitario", 8, 20);
            bruto = quantidade.multiply(preco).setScale(2, RoundingMode.HALF_UP);
        } else {
            if (r.simbolo() != null || r.quantidade() != null || r.precoUnitario() != null) throw badRequest("Fluxos de caixa não recebem símbolo, quantidade ou preço unitário");
            if (custos.signum() != 0 || impostos.signum() != 0) throw badRequest("Para fluxos sem ativo, custos/impostos devem compor o valorBruto informado");
            bruto = dinheiro(r.valorBruto(), "valorBruto", false);
        }
        BigDecimal liquido = switch (tipo) {
            case "COMPRA" -> bruto.add(custos).add(impostos).negate();
            case "VENDA" -> bruto.subtract(custos).subtract(impostos);
            case "APORTE" -> bruto;
            case "RETIRADA", "TAXA", "IMPOSTO" -> bruto.add(custos).add(impostos).negate();
            default -> throw badRequest("Tipo de operação inválido");
        };
        if (tipo.equals("VENDA") && liquido.signum() < 0) throw badRequest("Custos e impostos não podem exceder o valor bruto da venda");
        if (bruto.precision() > 20 || liquido.precision() > 20) throw badRequest("Valores financeiros excedem a precisão suportada");
        return new CarteiraFinanceiraDTO.Operacao(UUID.randomUUID().toString(), tipo, null, simbolo,
                r.executadaEm(), quantidade, preco, bruto, custos, impostos, liquido, moeda, "MANUAL", r.chaveIdempotencia().trim());
    }

    private CarteiraFinanceiraDTO.Provento montarProvento(CarteiraProventoRequest r) {
        if (r == null || r.dataPagamento() == null) throw badRequest("dataPagamento é obrigatório");
        if (r.dataPagamento().isAfter(LocalDate.now())) throw badRequest("Provento recebido não pode ter data de pagamento futura");
        String simbolo = simbolo(r.simbolo());
        String tipo = token(r.tipo());
        if (!TIPOS_PROVENTO.contains(tipo)) throw badRequest("tipo de provento inválido");
        validarChave(r.chaveIdempotencia());
        if (r.dataEx() != null && r.dataEx().isAfter(r.dataPagamento())) throw badRequest("dataEx não pode ser posterior à dataPagamento");
        BigDecimal elegivel = r.quantidadeElegivel() == null ? null : positivo(r.quantidadeElegivel(), "quantidadeElegivel", 8, 24);
        BigDecimal valorPorAtivo = r.valorPorAtivo() == null ? null : dinheiro(r.valorPorAtivo(), "valorPorAtivo", true);
        BigDecimal bruto = dinheiro(r.valorBruto(), "valorBruto", true);
        BigDecimal imposto = r.impostoRetido() == null ? new BigDecimal("0.00") : dinheiro(r.impostoRetido(), "impostoRetido", true);
        if (imposto.compareTo(bruto) > 0) throw badRequest("impostoRetido não pode exceder valorBruto");
        BigDecimal liquido = bruto.subtract(imposto);
        return new CarteiraFinanceiraDTO.Provento(UUID.randomUUID().toString(), simbolo, tipo, r.dataEx(),
                r.dataPagamento(), elegivel, valorPorAtivo, bruto, imposto, liquido, moeda(r.moeda()),
                textoOpcional(r.referenciaEvento(), 160), r.chaveIdempotencia().trim());
    }

    private CarteiraFinanceiraDTO.Operacao idempotenteOuConflito(CarteiraFinanceiraDTO.Operacao existente,
                                                                  CarteiraFinanceiraDTO.Operacao proposta) {
        boolean igual = existente.tipo().equals(proposta.tipo()) && Objects.equals(existente.simbolo(), proposta.simbolo())
                && existente.executadaEm().equals(proposta.executadaEm()) && decimalIgual(existente.quantidade(), proposta.quantidade())
                && decimalIgual(existente.precoUnitario(), proposta.precoUnitario()) && decimalIgual(existente.valorBruto(), proposta.valorBruto())
                && decimalIgual(existente.custos(), proposta.custos()) && decimalIgual(existente.impostos(), proposta.impostos())
                && decimalIgual(existente.valorLiquido(), proposta.valorLiquido()) && existente.moeda().equals(proposta.moeda());
        if (!igual) throw new ResponseStatusException(HttpStatus.CONFLICT, "Chave de idempotência já usada com conteúdo diferente");
        return existente;
    }

    private CarteiraFinanceiraDTO.Provento idempotenteOuConflito(CarteiraFinanceiraDTO.Provento a, CarteiraFinanceiraDTO.Provento b) {
        boolean igual = a.simbolo().equals(b.simbolo()) && a.tipo().equals(b.tipo()) && Objects.equals(a.dataEx(), b.dataEx())
                && a.dataPagamento().equals(b.dataPagamento()) && decimalIgual(a.quantidadeElegivel(), b.quantidadeElegivel())
                && decimalIgual(a.valorPorAtivo(), b.valorPorAtivo()) && decimalIgual(a.valorBruto(), b.valorBruto())
                && decimalIgual(a.impostoRetido(), b.impostoRetido()) && decimalIgual(a.valorLiquido(), b.valorLiquido()) && a.moeda().equals(b.moeda())
                && Objects.equals(a.referenciaEvento(), b.referenciaEvento());
        if (!igual) throw new ResponseStatusException(HttpStatus.CONFLICT, "Chave de idempotência já usada com conteúdo diferente");
        return a;
    }

    private Map<String, Posicao> calcularPosicoes(List<CarteiraFinanceiraDTO.Operacao> operacoes) {
        Map<String, Posicao> posicoes = new TreeMap<>();
        Set<String> estornadas = new HashSet<>();
        operacoes.stream().filter(o -> "ESTORNO".equals(o.tipo()))
                .map(CarteiraFinanceiraDTO.Operacao::operacaoReferenciadaId).filter(Objects::nonNull)
                .forEach(estornadas::add);
        for (var o : operacoes) {
            // Replays the effective ledger: an estorno cancels the exact original
            // event instead of approximating it as an opposite trade at cash value.
            if ("ESTORNO".equals(o.tipo()) || estornadas.contains(o.id())) continue;
            if (o.simbolo() == null || o.quantidade() == null) continue;
            String tipo = o.tipo();
            if (!tipo.equals("COMPRA") && !tipo.equals("VENDA")) continue;
            Posicao atual = posicoes.getOrDefault(o.simbolo(), new Posicao(ZERO, ZERO, ZERO));
            BigDecimal custo = o.tipo().equals("COMPRA")
                    ? o.valorBruto().add(o.custos()).add(o.impostos()) : o.valorLiquido();
            posicoes.put(o.simbolo(), atual.aplicar(tipo, o.quantidade(), custo));
        }
        return posicoes;
    }

    private BigDecimal quantidadeAtual(String carteiraId, String ticker, LocalDateTime ate) {
        return calcularPosicoes(repositorio.operacoes(carteiraId).stream().filter(o -> !o.executadaEm().isAfter(ate)).toList())
                .getOrDefault(ticker, new Posicao(ZERO, ZERO, ZERO)).quantidade();
    }

    private BigDecimal fluxoExterno(List<CarteiraFinanceiraDTO.Operacao> operacoes, LocalDate data, LocalDate inicioExclusivo) {
        BigDecimal total = ZERO;
        Map<String, CarteiraFinanceiraDTO.Operacao> porId = new HashMap<>();
        for (var o : operacoes) {
            porId.put(o.id(), o);
            LocalDate dataOperacao = o.executadaEm().toLocalDate();
            if (dataOperacao.isAfter(inicioExclusivo == null ? data.minusDays(1) : inicioExclusivo) && !dataOperacao.isAfter(data)) {
                if (o.tipo().equals("APORTE") || o.tipo().equals("RETIRADA")) total = total.add(o.valorLiquido());
                if (o.tipo().equals("ESTORNO")) {
                    var original = porId.get(o.operacaoReferenciadaId());
                    if (original != null && (original.tipo().equals("APORTE") || original.tipo().equals("RETIRADA"))) total = total.add(o.valorLiquido());
                }
            }
        }
        return total;
    }

    private String simbolo(String v) {
        String s = token(v);
        if (!SIMBOLO.matcher(s).matches()) throw badRequest("Símbolo inválido");
        return s;
    }
    private String token(String v) { return v == null ? "" : v.trim().toUpperCase(Locale.ROOT); }
    private String moeda(String v) {
        String s = v == null || v.isBlank() ? "BRL" : token(v);
        if (!s.matches("[A-Z]{3}")) throw badRequest("moeda deve ter três letras");
        return s;
    }
    private String validarVersao(String v) {
        String s = v == null || v.isBlank() ? VERSAO_PADRAO : v.trim();
        if (s.length() > 40 || !s.matches("[A-Za-z0-9._-]+")) throw badRequest("versaoCalculo inválida");
        return s;
    }
    private String textoOpcional(String v, int limite) {
        if (v == null || v.isBlank()) return null;
        if (v.trim().length() > limite) throw badRequest("referenciaEvento excede o limite");
        return v.trim();
    }
    private void validarChave(String v) {
        if (v == null || v.isBlank() || v.trim().length() > 100) throw badRequest("chaveIdempotencia é obrigatória (até 100 caracteres)");
    }
    private BigDecimal dinheiro(BigDecimal v, String nome, boolean zeroPermitido) {
        if (v == null || v.scale() > 2 || v.precision() > 20 || v.signum() < 0 || (!zeroPermitido && v.signum() == 0))
            throw badRequest(nome + " deve ser não negativo, com até 2 casas decimais");
        return v.setScale(2, RoundingMode.UNNECESSARY);
    }
    private BigDecimal positivo(BigDecimal v, String nome, int scale, int precision) {
        if (v == null || v.signum() <= 0 || v.scale() > scale || v.precision() > precision) throw badRequest(nome + " deve ser positivo e respeitar a precisão suportada");
        return v;
    }
    private boolean decimalIgual(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }
    private void exigirCarteira(String id) {
        if (id == null || id.isBlank() || id.length() > 64) throw badRequest("id de carteira inválido");
        if (!repositorioCarteiras.schemaDisponivel()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Schema V42 de Carteiras não está disponível");
        if (repositorioCarteiras.buscar(id).isEmpty()) throw notFound("Carteira não encontrada");
    }
    private void exigirCarteiraAtiva(String id) {
        exigirCarteira(id);
        if (!repositorio.schemaFinanceiroDisponivel()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Schema financeiro V43 não está disponível");
        if (!repositorio.carteiraAtiva(id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Carteira arquivada não aceita novas alterações");
    }
    private LocalDate anteriorData(String carteiraId, LocalDate data, String versao) {
        return repositorio.rentabilidadeAnterior(carteiraId, data, versao).map(CarteiraFinanceiraDTO.Rentabilidade::dataReferencia).orElse(null);
    }
    private void exigirV43() {
        if (!repositorio.schemaFinanceiroDisponivel()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Schema financeiro V43 não está disponível");
    }
    private ResponseStatusException badRequest(String s) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, s); }
    private ResponseStatusException notFound(String s) { return new ResponseStatusException(HttpStatus.NOT_FOUND, s); }

    private record Posicao(BigDecimal quantidade, BigDecimal custoRemanescente, BigDecimal resultadoRealizado) {
        Posicao aplicar(String tipo, BigDecimal q, BigDecimal valor) {
            if (tipo.equals("COMPRA")) return new Posicao(quantidade.add(q), custoRemanescente.add(valor), resultadoRealizado);
            if (q.compareTo(quantidade) > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ledger contém venda superior à posição; corrija os registros antes do snapshot");
            BigDecimal custoVendido = quantidade.signum() == 0 ? ZERO
                    : custoRemanescente.multiply(q).divide(quantidade, 8, RoundingMode.HALF_UP);
            BigDecimal realizado = resultadoRealizado.add(valor.subtract(custoVendido));
            return new Posicao(quantidade.subtract(q), custoRemanescente.subtract(custoVendido), realizado);
        }
    }
}
