package br.com.miranda.gestor.ativos.brutos.external.http;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoCotaBrapiEsgotada;
import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoIntegracaoBrapi;
import br.com.miranda.gestor.ativos.brutos.external.dto.ConsultaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaCotacaoEmLoteBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaPerfilBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.service.orcamento.PoliticaChamadaDaTela;
import br.com.miranda.gestor.ativos.brutos.service.orcamento.RegistroConsumoBrapi;
import br.com.miranda.gestor.ativos.brutos.service.orcamento.RegistroConsumoBrapi.Endpoint;
import br.com.miranda.gestor.ativos.brutos.service.orcamento.RegistroConsumoBrapi.Origem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.BRAPI_SERVICE;

@Slf4j
@Service
public class ClienteBrApi {

    private static final String BRAPI_BASE_URL = "https://brapi.dev";
    private static final String CAMINHO_COTACAO = "/api/quote/{symbol}";
    private static final String CAMINHO_COTACAO_EM_LOTE = "/api/v2/stocks/quote";
    private static final String CAMINHO_HISTORICO_ACOES = "/api/v2/stocks/historical";
    private static final String CAMINHO_PERFIL_EMPRESA = "/api/v2/stocks/profile";
    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");

    // Opcional (2026-09-27): sem chave, nenhuma rotina chama a BRAPI e o
    // sistema segue com o preco oficial do dia anterior (COTAHIST).
    @Value("${brapi.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    // Toda requisicao conta na cota, inclusive a que volta com erro (infra V15).
    private final RegistroConsumoBrapi consumo;
    // Chamadas da tela (dentro de requisicao HTTP): teto proprio e reuso de 30 min.
    private final PoliticaChamadaDaTela politicaTela;

    public ClienteBrApi(ObjectMapper objectMapper, RegistroConsumoBrapi consumo, PoliticaChamadaDaTela politicaTela) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.consumo = consumo;
        this.politicaTela = politicaTela;
    }

    /** Ha chave configurada: sem ela, quem usa a BRAPI se desliga em vez de falhar. */
    public boolean habilitado() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Consulta a cotação atual de um ativo na BRAPI.
     */
    public RespostaBrapiDTO consultarCotacao(String simbolo) {
        log.info("{}-Iniciando consulta para simbolo: {}", BRAPI_SERVICE, simbolo);

        String url = UriComponentsBuilder.fromHttpUrl(BRAPI_BASE_URL)
                .path(CAMINHO_COTACAO)
                .buildAndExpand(simbolo)
                .toUriString();

        RespostaBrapiDTO resposta = executarGet(url, RespostaBrapiDTO.class, "quote/" + simbolo, Endpoint.QUOTE);
        int totalResultados = resposta == null || resposta.getResults() == null ? 0 : resposta.getResults().size();
        log.info("{}-Cotacao parseada com sucesso. Resultados: {}", BRAPI_SERVICE, totalResultados);
        return resposta;
    }

    /**
     * Consulta séries históricas OHLCV de um ou mais ativos na BRAPI.
     */
    public RespostaHistoricoAcoesDTO consultarHistorico(ConsultaHistoricoAcoesDTO filtros) {
        log.info("{}-Iniciando consulta de historico OHLCV BRAPI. Filtros: {}", BRAPI_SERVICE, filtros);

        String url = UriComponentsBuilder.fromHttpUrl(BRAPI_BASE_URL)
                .path(CAMINHO_HISTORICO_ACOES)
                .queryParam("symbols", filtros.symbols())
                .queryParamIfPresent("range", texto(filtros.range()))
                .queryParamIfPresent("interval", texto(filtros.interval()))
                .queryParamIfPresent("startDate", texto(filtros.startDate()))
                .queryParamIfPresent("endDate", texto(filtros.endDate()))
                .queryParamIfPresent("sortOrder", texto(filtros.sortOrder()))
                .toUriString();

        RespostaHistoricoAcoesDTO resposta = executarGet(url, RespostaHistoricoAcoesDTO.class, "stocks/historical", Endpoint.HISTORICAL);
        int totalResultados = resposta == null || resposta.results() == null ? 0 : resposta.results().size();
        log.info("{}-Historico OHLCV parseado com sucesso. Resultados: {}", BRAPI_SERVICE, totalResultados);
        return resposta;
    }

    /**
     * Consulta a cotacao de varios ativos numa unica chamada. Endpoint em lote
     * (diferente do /api/quote/{symbol} legado, que aceita so um simbolo) -
     * necessario porque o plano Gratuito da BRAPI recusa mais de 1 ativo por
     * requisicao no endpoint singular, mas o em lote aceita ate o limite do
     * plano (1 no Gratuito, 10 no Startup, 20 no Pro).
     */
    public RespostaCotacaoEmLoteBrapiDTO consultarCotacaoEmLote(List<String> simbolos) {
        String simbolosCsv = String.join(",", simbolos);
        log.info("{}-Iniciando consulta em lote para simbolos: {}", BRAPI_SERVICE, simbolosCsv);

        String url = UriComponentsBuilder.fromHttpUrl(BRAPI_BASE_URL)
                .path(CAMINHO_COTACAO_EM_LOTE)
                .queryParam("symbols", simbolosCsv)
                .toUriString();

        RespostaCotacaoEmLoteBrapiDTO resposta = executarGet(url, RespostaCotacaoEmLoteBrapiDTO.class, "quote-lote/" + simbolosCsv, Endpoint.QUOTE_LOTE);
        int totalResultados = resposta == null || resposta.getResults() == null ? 0 : resposta.getResults().size();
        log.info("{}-Cotacao em lote parseada com sucesso. Resultados: {}", BRAPI_SERVICE, totalResultados);
        return resposta;
    }

    /**
     * Consulta o perfil da empresa (setor, industria, resumo do negocio) na BRAPI.
     * Endpoint gratis no plano atual, inclusive pra tickers reais (nao so os de
     * demonstracao) - validado em 2026-09-25 com WEGE3.
     */
    public RespostaPerfilBrapiDTO consultarPerfilEmpresa(String simbolo) {
        log.info("{}-Iniciando consulta de perfil de empresa para simbolo: {}", BRAPI_SERVICE, simbolo);

        String url = UriComponentsBuilder.fromHttpUrl(BRAPI_BASE_URL)
                .path(CAMINHO_PERFIL_EMPRESA)
                .queryParam("symbols", simbolo)
                .toUriString();

        RespostaPerfilBrapiDTO resposta = executarGet(url, RespostaPerfilBrapiDTO.class, "profile/" + simbolo, Endpoint.PROFILE);
        int totalResultados = resposta == null || resposta.getResults() == null ? 0 : resposta.getResults().size();
        log.info("{}-Perfil de empresa parseado com sucesso. Resultados: {}", BRAPI_SERVICE, totalResultados);
        return resposta;
    }

    private <T> T executarGet(String url, Class<T> tipoResposta, String nomeRecurso, Endpoint endpoint) {
        log.debug("{}-URL de requisicao: {}", BRAPI_SERVICE, url);
        // Dentro de uma requisicao HTTP e clique na tela; agendador e fila rodam
        // fora dela. So a tela passa pela politica de reuso e teto proprio.
        boolean daTela = RequestContextHolder.getRequestAttributes() != null;
        if (daTela) {
            Optional<String> recente = politicaTela.respostaRecente(url);
            if (recente.isPresent()) {
                log.info("{}-Reusando resposta de menos de 30 min, sem nova chamada: {}", BRAPI_SERVICE, nomeRecurso);
                return ler(recente.get(), tipoResposta, nomeRecurso);
            }
            politicaTela.exigirOrcamento(nomeRecurso);
        }
        consumo.registrar(LocalDate.now(ZONA_BRASIL), endpoint, daTela ? Origem.TELA : Origem.AGENDADA);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entidadeAutenticada(),
                    String.class
            );

            String corpoResposta = response.getBody();
            log.debug("{}-Status HTTP recebido: {}", BRAPI_SERVICE, response.getStatusCode());
            log.debug("{}-Tamanho da resposta: {} bytes", BRAPI_SERVICE, corpoResposta == null ? 0 : corpoResposta.length());

            if (!StringUtils.hasText(corpoResposta)) {
                throw new ExcecaoIntegracaoBrapi(nomeRecurso, "resposta vazia", null);
            }

            T lida = ler(corpoResposta, tipoResposta, nomeRecurso);
            if (daTela) {
                politicaTela.guardar(url, corpoResposta);
            }
            return lida;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.warn("{}-Recurso nao encontrado na BRAPI: {} (404)", BRAPI_SERVICE, nomeRecurso);
                return null;
            }
            if (ex.getStatusCode().value() == 429) {
                log.error("{}-BRAPI recusou por cota/limite (429). Recurso: {}", BRAPI_SERVICE, nomeRecurso);
                throw new ExcecaoCotaBrapiEsgotada(nomeRecurso, ex);
            }

            log.error("{}-Erro HTTP ao consultar BRAPI. Recurso: {}, Status: {}, Mensagem: {}",
                    BRAPI_SERVICE, nomeRecurso, ex.getStatusCode(), ex.getMessage());
            throw new ExcecaoIntegracaoBrapi(nomeRecurso, "HTTP " + ex.getStatusCode(), ex);
        }
    }

    private <T> T ler(String corpo, Class<T> tipoResposta, String nomeRecurso) {
        try {
            return objectMapper.readValue(corpo, tipoResposta);
        } catch (JsonProcessingException e) {
            log.error("{}-Erro ao processar JSON da resposta BRAPI. Recurso: {}", BRAPI_SERVICE, nomeRecurso, e);
            throw new ExcecaoIntegracaoBrapi(nomeRecurso, "resposta JSON invalida", e);
        }
    }

    private HttpEntity<?> entidadeAutenticada() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    private Optional<String> texto(String valor) {
        return Optional.ofNullable(valor).filter(StringUtils::hasText);
    }
}
