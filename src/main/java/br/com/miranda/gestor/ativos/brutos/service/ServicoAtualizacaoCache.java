package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.Ativo;
import br.com.miranda.gestor.ativos.brutos.external.CandleDiarioEntity;
import br.com.miranda.gestor.ativos.brutos.external.CotacaoAtualEntity;
import br.com.miranda.gestor.ativos.brutos.external.PerfilEmpresaCacheEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.ConsultaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.PerfilEmpresaBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.ResultadoCotacaoBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaCotacaoEmLoteBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaPerfilBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBrApi;
import br.com.miranda.gestor.ativos.brutos.port.PortaFilaMensagens;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAtivoMonitorado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCandleDiario;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCotacaoAtual;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPerfilEmpresaCache;
import br.com.miranda.gestor.ativos.brutos.tools.ConversorJson;
import br.com.miranda.gestor.ativos.brutos.tools.GeradorChaveDeduplicacaoAtivo;
import br.com.miranda.gestor.ativos.brutos.tools.SelecionadorAtivosDevidos;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.SERVICO;

/**
 * Escritor unico do cache de mercado: fala com a BRAPI (em lote, respeitando
 * o limite do plano) e grava em cotacao_atual / candle_diario /
 * perfil_empresa_cache. Os controllers que servem o frontend so leem dessas
 * tabelas - nunca mais chamam a BRAPI na hora do clique do usuario.
 *
 * Continua publicando nas mesmas filas SQS que o fluxo antigo publicava
 * (tratar-ativos e sqs-registrar-series-historicas), pra nao quebrar o
 * pipeline do gerar-insights que depende desses eventos pra calcular
 * valuation e sinal tecnico.
 */
@Slf4j
@Service
public class ServicoAtualizacaoCache {

    private static final long INTERVALO_HISTORICO_SEGUNDOS = 24L * 60 * 60;
    private static final long INTERVALO_PERFIL_SEGUNDOS = 7L * 24 * 60 * 60;
    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");

    private final ClienteBrApi clienteBrApi;
    private final PortaFilaMensagens filaMensagens;
    private final RepositorioAtivoMonitorado repositorioAtivoMonitorado;
    private final RepositorioCotacaoAtual repositorioCotacaoAtual;
    private final RepositorioCandleDiario repositorioCandleDiario;
    private final RepositorioPerfilEmpresaCache repositorioPerfilEmpresaCache;
    private final ServicoAtivoMonitorado servicoAtivoMonitorado;
    private final ModelMapper mapper = new ModelMapper();

    @Value("${brapi.lote.tamanho:1}")
    private int tamanhoLote;

    @Value("${brapi.historico.range:3mo}")
    private String rangeHistorico;

    @Value("${aws.sqs.historical-series.queue.url}")
    private String filaSeriesHistoricasUrl;

    public ServicoAtualizacaoCache(
            ClienteBrApi clienteBrApi,
            PortaFilaMensagens filaMensagens,
            RepositorioAtivoMonitorado repositorioAtivoMonitorado,
            RepositorioCotacaoAtual repositorioCotacaoAtual,
            RepositorioCandleDiario repositorioCandleDiario,
            RepositorioPerfilEmpresaCache repositorioPerfilEmpresaCache,
            ServicoAtivoMonitorado servicoAtivoMonitorado
    ) {
        this.clienteBrApi = clienteBrApi;
        this.filaMensagens = filaMensagens;
        this.repositorioAtivoMonitorado = repositorioAtivoMonitorado;
        this.repositorioCotacaoAtual = repositorioCotacaoAtual;
        this.repositorioCandleDiario = repositorioCandleDiario;
        this.repositorioPerfilEmpresaCache = repositorioPerfilEmpresaCache;
        this.servicoAtivoMonitorado = servicoAtivoMonitorado;
    }

    /**
     * Atualiza a cotacao de todo ativo monitorado cujo intervalo (o mesmo
     * campo intervaloSegundos usado hoje) ja venceu.
     */
    public void atualizarCotacoes() {
        List<AtivoMonitoradoEntity> ativos = repositorioAtivoMonitorado.findByAtivoTrue();
        if (ativos.isEmpty()) {
            return;
        }

        Map<String, LocalDateTime> ultimaAtualizacao = repositorioCotacaoAtual.findAll().stream()
                .collect(Collectors.toMap(CotacaoAtualEntity::getSimbolo, CotacaoAtualEntity::getAtualizadoEm));

        Map<String, AtivoMonitoradoEntity> porSimbolo = ativos.stream()
                .collect(Collectors.toMap(AtivoMonitoradoEntity::getSimbolo, a -> a));

        List<String> devidos = ativos.stream()
                .filter(a -> SelecionadorAtivosDevidos.estaDevido(ultimaAtualizacao.get(a.getSimbolo()), a.getIntervaloSegundos()))
                .map(AtivoMonitoradoEntity::getSimbolo)
                .toList();

        if (devidos.isEmpty()) {
            return;
        }

        log.info("{}-Atualizando cotacao em cache para {} ativo(s): {}", SERVICO, devidos.size(), devidos);

        for (List<String> lote : particionar(devidos, tamanhoLote)) {
            try {
                if (lote.size() == 1) {
                    // O endpoint em lote (/v2/stocks/quote) nao devolve priceEarnings/
                    // earningsPerShare (confirmado ao vivo) - e o gerar-insights depende
                    // desses dois campos pra calcular o valuation de Graham
                    // (equity_snapshot.py le "priceEarnings"/"earningsPerShare" direto do
                    // payload). Com lote de 1 (plano Gratuito, o caso de hoje), usa o
                    // endpoint legado, que tem os dois campos - sem perda de precisao.
                    processarResultadoCotacao(lote.getFirst(), clienteBrApi.consultarCotacao(lote.getFirst()), porSimbolo);
                } else {
                    RespostaCotacaoEmLoteBrapiDTO resposta = clienteBrApi.consultarCotacaoEmLote(lote);
                    if (resposta == null || resposta.getResults() == null) {
                        continue;
                    }
                    for (ResultadoCotacaoBrapiDTO resultado : resposta.getResults()) {
                        if (resultado.getData() == null) {
                            continue;
                        }
                        processarResultadoCotacao(resultado.getSymbol(), resultado.getData(), porSimbolo);
                    }
                }
            } catch (Exception e) {
                log.error("{}-Erro ao atualizar cotacao do lote {}: {}", SERVICO, lote, e.getMessage(), e);
            }
        }
    }

    private void processarResultadoCotacao(String simbolo, RespostaBrapiDTO respostaSingular, Map<String, AtivoMonitoradoEntity> porSimbolo) {
        if (respostaSingular == null || respostaSingular.getResults() == null || respostaSingular.getResults().isEmpty()) {
            return;
        }
        processarResultadoCotacao(simbolo, respostaSingular.getResults().getFirst(), porSimbolo);
    }

    private void processarResultadoCotacao(String simbolo, AtivoBrapiDTO dados, Map<String, AtivoMonitoradoEntity> porSimbolo) {
        if (dados == null) {
            return;
        }
        persistirCotacao(simbolo, dados);
        publicarCotacaoNaFila(dados);
        // So mantem viva a coluna "Ultima atualizacao" que a tela de Monitorados ja
        // mostrava - nao influencia mais a decisao de "esta devido", que agora olha o
        // atualizadoEm de cotacao_atual, nao o de ativo_monitorado.
        AtivoMonitoradoEntity entidade = porSimbolo.get(simbolo);
        if (entidade != null) {
            servicoAtivoMonitorado.marcarProcessado(entidade);
        }
    }

    /**
     * Atualiza o candle de hoje dos ativos com tipoColeta COTACAO_E_HISTORICO,
     * uma vez por dia (candles de dias passados sao imutaveis, nunca regravam).
     */
    public void atualizarHistoricoDiario() {
        List<AtivoMonitoradoEntity> ativos = repositorioAtivoMonitorado.findByAtivoTrue().stream()
                .filter(a -> a.getTipoColeta() == TipoColeta.COTACAO_E_HISTORICO)
                .toList();
        if (ativos.isEmpty()) {
            return;
        }

        LocalDate hoje = LocalDate.now(ZONA_BRASIL);
        Map<String, LocalDateTime> ultimaAtualizacaoHoje = new HashMap<>();
        for (AtivoMonitoradoEntity ativo : ativos) {
            repositorioCandleDiario.findBySimboloAndData(ativo.getSimbolo(), hoje)
                    .ifPresent(candle -> ultimaAtualizacaoHoje.put(ativo.getSimbolo(), candle.getAtualizadoEm()));
        }

        List<String> devidos = ativos.stream()
                .map(AtivoMonitoradoEntity::getSimbolo)
                .filter(simbolo -> SelecionadorAtivosDevidos.estaDevido(ultimaAtualizacaoHoje.get(simbolo), INTERVALO_HISTORICO_SEGUNDOS))
                .toList();

        if (devidos.isEmpty()) {
            return;
        }

        log.info("{}-Atualizando historico diario em cache para {} ativo(s): {}", SERVICO, devidos.size(), devidos);

        for (List<String> lote : particionar(devidos, tamanhoLote)) {
            try {
                RespostaHistoricoAcoesDTO resposta = clienteBrApi.consultarHistorico(
                        new ConsultaHistoricoAcoesDTO(String.join(",", lote), rangeHistorico, "1d", null, null, "asc"));
                if (resposta == null || resposta.results() == null) {
                    continue;
                }
                for (var resultado : resposta.results()) {
                    if (resultado.data() == null || resultado.data().historicalDataPrice() == null) {
                        continue;
                    }
                    for (var preco : resultado.data().historicalDataPrice()) {
                        persistirCandle(resultado.symbol(), preco);
                    }
                }
                // Publica a resposta do lote inteira, igual o fluxo antigo publicava por
                // simbolo - com tamanhoLote=1 (default hoje) o comportamento e identico ao
                // de antes. Se o lote crescer (plano pago), o consumidor Python precisa
                // aceitar mais de um simbolo por mensagem; hoje ele so processa um.
                publicarHistoricoNaFila(resposta);
            } catch (Exception e) {
                log.error("{}-Erro ao atualizar historico do lote {}: {}", SERVICO, lote, e.getMessage(), e);
            }
        }
    }

    /**
     * Atualiza o perfil (setor, industria, resumo) de todo ativo monitorado
     * uma vez por semana - e o dado que menos muda de todo o ecossistema.
     * Feito um simbolo por vez: no cadencia semanal o ganho de lote e
     * irrelevante, e assim reaproveita consultarPerfilEmpresa sem alterar seu
     * contrato (usado tambem pelo fallback de primeira visita).
     */
    public void atualizarPerfilEmpresa() {
        List<AtivoMonitoradoEntity> ativos = repositorioAtivoMonitorado.findByAtivoTrue();
        if (ativos.isEmpty()) {
            return;
        }

        Map<String, LocalDateTime> ultimaAtualizacao = repositorioPerfilEmpresaCache.findAll().stream()
                .collect(Collectors.toMap(PerfilEmpresaCacheEntity::getSimbolo, PerfilEmpresaCacheEntity::getAtualizadoEm));

        List<String> devidos = ativos.stream()
                .map(AtivoMonitoradoEntity::getSimbolo)
                .filter(simbolo -> SelecionadorAtivosDevidos.estaDevido(ultimaAtualizacao.get(simbolo), INTERVALO_PERFIL_SEGUNDOS))
                .toList();

        if (devidos.isEmpty()) {
            return;
        }

        log.info("{}-Atualizando perfil de empresa em cache para {} ativo(s): {}", SERVICO, devidos.size(), devidos);

        for (String simbolo : devidos) {
            try {
                RespostaPerfilBrapiDTO resposta = clienteBrApi.consultarPerfilEmpresa(simbolo);
                if (resposta == null || resposta.getResults() == null || resposta.getResults().isEmpty()) {
                    continue;
                }
                PerfilEmpresaBrapiDTO dados = resposta.getResults().getFirst().getData();
                if (dados == null) {
                    continue;
                }
                persistirPerfil(simbolo, dados);
            } catch (Exception e) {
                log.warn("{}-Falha ao atualizar perfil de {}: {}", SERVICO, simbolo, e.getMessage());
            }
        }
    }

    /**
     * Le o perfil do cache; se o simbolo nunca foi visto (busca livre de um
     * ticker fora da carteira monitorada), busca ao vivo uma unica vez e
     * aquece o cache nesse mesmo golpe, pra proxima pessoa que buscar esse
     * simbolo ja encontrar servido do banco.
     */
    public Optional<PerfilEmpresaBrapiDTO> buscarPerfilComFallback(String simbolo) {
        var emCache = repositorioPerfilEmpresaCache.findBySimbolo(simbolo);
        if (emCache.isPresent()) {
            return emCache.map(PerfilEmpresaBrapiDTO::de);
        }

        try {
            RespostaPerfilBrapiDTO resposta = clienteBrApi.consultarPerfilEmpresa(simbolo);
            if (resposta == null || resposta.getResults() == null || resposta.getResults().isEmpty()) {
                return Optional.empty();
            }
            PerfilEmpresaBrapiDTO dados = resposta.getResults().getFirst().getData();
            if (dados == null) {
                return Optional.empty();
            }
            persistirPerfil(simbolo, dados);
            return Optional.of(dados);
        } catch (Exception e) {
            log.warn("{}-Falha no fallback de perfil para {}: {}", SERVICO, simbolo, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Persiste no cache um Ativo ja obtido ao vivo pelo fallback de primeira
     * visita do AtivoController (que chama ServicoAtivo diretamente, nao o
     * ClienteBrApi, porque tambem precisa publicar na fila).
     */
    public void persistirCotacaoDoFallback(Ativo ativo) {
        if (ativo == null || ativo.getSymbol() == null) {
            return;
        }
        CotacaoAtualEntity entidade = repositorioCotacaoAtual.findBySimbolo(ativo.getSymbol()).orElseGet(CotacaoAtualEntity::new);
        entidade.setSimbolo(ativo.getSymbol());
        entidade.setShortName(ativo.getShortName());
        entidade.setLongName(ativo.getLongName());
        entidade.setMarketCap(ativo.getMarketCap());
        entidade.setRegularMarketChange(ativo.getRegularMarketChange());
        entidade.setRegularMarketChangePercent(ativo.getRegularMarketChangePercent());
        entidade.setRegularMarketTime(ativo.getRegularMarketTime());
        entidade.setRegularMarketPrice(ativo.getRegularMarketPrice());
        entidade.setRegularMarketDayHigh(ativo.getRegularMarketDayHigh());
        entidade.setRegularMarketDayLow(ativo.getRegularMarketDayLow());
        entidade.setRegularMarketVolume(ativo.getRegularMarketVolume());
        entidade.setRegularMarketPreviousClose(ativo.getRegularMarketPreviousClose());
        entidade.setRegularMarketOpen(ativo.getRegularMarketOpen());
        entidade.setFiftyTwoWeekLow(ativo.getFiftyTwoWeekLow());
        entidade.setFiftyTwoWeekHigh(ativo.getFiftyTwoWeekHigh());
        entidade.setPriceEarnings(ativo.getPriceEarnings());
        entidade.setEarningsPerShare(ativo.getEarningsPerShare());
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorioCotacaoAtual.save(entidade);
    }

    /** Persiste os candles de uma resposta ja obtida da BRAPI - usado pelo fallback de histórico do HistoricoAcoesController. */
    public void persistirCandlesDoResultado(RespostaHistoricoAcoesDTO resposta) {
        if (resposta == null || resposta.results() == null) {
            return;
        }
        for (var resultado : resposta.results()) {
            if (resultado.data() == null || resultado.data().historicalDataPrice() == null) {
                continue;
            }
            for (var preco : resultado.data().historicalDataPrice()) {
                persistirCandle(resultado.symbol(), preco);
            }
        }
    }

    private void persistirCotacao(String simbolo, AtivoBrapiDTO dados) {
        CotacaoAtualEntity entidade = repositorioCotacaoAtual.findBySimbolo(simbolo).orElseGet(CotacaoAtualEntity::new);
        entidade.setSimbolo(simbolo);
        entidade.setShortName(dados.getShortName());
        entidade.setLongName(dados.getLongName());
        entidade.setMarketCap(dados.getMarketCap());
        entidade.setRegularMarketChange(dados.getRegularMarketChange());
        entidade.setRegularMarketChangePercent(dados.getRegularMarketChangePercent());
        entidade.setRegularMarketTime(dados.getRegularMarketTime());
        entidade.setRegularMarketPrice(dados.getRegularMarketPrice());
        entidade.setRegularMarketDayHigh(dados.getRegularMarketDayHigh());
        entidade.setRegularMarketDayLow(dados.getRegularMarketDayLow());
        entidade.setRegularMarketVolume(dados.getRegularMarketVolume());
        entidade.setRegularMarketPreviousClose(dados.getRegularMarketPreviousClose());
        entidade.setRegularMarketOpen(dados.getRegularMarketOpen());
        entidade.setFiftyTwoWeekLow(dados.getFiftyTwoWeekLow());
        entidade.setFiftyTwoWeekHigh(dados.getFiftyTwoWeekHigh());
        entidade.setPriceEarnings(dados.getPriceEarnings());
        entidade.setEarningsPerShare(dados.getEarningsPerShare());
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorioCotacaoAtual.save(entidade);
    }

    private void persistirCandle(String simbolo, RespostaHistoricoAcoesDTO.PrecoHistoricoAcaoDTO preco) {
        if (preco.date() == null) {
            return;
        }
        LocalDate data = Instant.ofEpochSecond(preco.date()).atZone(ZONA_BRASIL).toLocalDate();
        CandleDiarioEntity entidade = repositorioCandleDiario.findBySimboloAndData(simbolo, data).orElseGet(CandleDiarioEntity::new);
        entidade.setSimbolo(simbolo);
        entidade.setData(data);
        entidade.setOpen(preco.open());
        entidade.setHigh(preco.high());
        entidade.setLow(preco.low());
        entidade.setClose(preco.close());
        entidade.setVolume(preco.volume());
        entidade.setAdjustedClose(preco.adjustedClose());
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorioCandleDiario.save(entidade);
    }

    private void persistirPerfil(String simbolo, PerfilEmpresaBrapiDTO dados) {
        PerfilEmpresaCacheEntity entidade = repositorioPerfilEmpresaCache.findBySimbolo(simbolo).orElseGet(PerfilEmpresaCacheEntity::new);
        entidade.setSimbolo(simbolo);
        entidade.setSector(dados.getSector());
        entidade.setIndustry(dados.getIndustry());
        entidade.setLongBusinessSummary(dados.getLongBusinessSummary());
        entidade.setWebsite(dados.getWebsite());
        entidade.setCnpj(dados.getCnpj());
        entidade.setFullTimeEmployees(dados.getFullTimeEmployees());
        entidade.setCity(dados.getCity());
        entidade.setState(dados.getState());
        entidade.setAtualizadoEm(LocalDateTime.now());
        repositorioPerfilEmpresaCache.save(entidade);
    }

    private void publicarCotacaoNaFila(AtivoBrapiDTO dados) {
        Ativo ativo = mapper.map(dados, Ativo.class);
        GeradorChaveDeduplicacaoAtivo.preencher(ativo);
        filaMensagens.enviarMensagemParaFila(ConversorJson.paraJson(ativo));
    }

    private void publicarHistoricoNaFila(RespostaHistoricoAcoesDTO resposta) {
        filaMensagens.enviarMensagemParaFila(ConversorJson.paraJson(resposta), filaSeriesHistoricasUrl);
    }

    private static List<List<String>> particionar(List<String> lista, int tamanho) {
        int passo = Math.max(tamanho, 1);
        List<List<String>> lotes = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += passo) {
            lotes.add(lista.subList(i, Math.min(i + passo, lista.size())));
        }
        return lotes;
    }
}
