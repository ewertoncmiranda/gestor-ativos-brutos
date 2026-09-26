package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.LinhaDoTempoComunicadosDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.NewsletterComunicadosDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoComunicados;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONTROLADOR;

/**
 * Comunicados oficiais das companhias (base IPE dos dados abertos da CVM),
 * carregados pelo {@code etl-fundamentos-cvm --comunicados}. Contrato
 * {@code infra#CTR-10}.
 *
 * <p>Parâmetro inválido (categoria desconhecida, semana malformada, período
 * invertido) responde 400 pelo tratador global.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ComunicadoController {

    private final ServicoComunicados servicoComunicados;

    /**
     * Linha do tempo de um ticker, mais recente primeiro.
     *
     * <p>Exemplo: {@code /empresas/PETR4/comunicados?categorias=FATO_RELEVANTE,PROVENTOS&desde=2026-01-01}
     */
    @GetMapping("/empresas/{simbolo}/comunicados")
    public ResponseEntity<LinhaDoTempoComunicadosDTO> buscarPorSimbolo(
            @PathVariable String simbolo,
            @RequestParam(required = false) List<String> categorias,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        log.info("{}-Comunicados de {} | categorias={} desde={} ate={} pagina={}",
                CONTROLADOR, simbolo, categorias, desde, ate, pagina);
        return ResponseEntity.ok(
                servicoComunicados.buscarPorSimbolo(simbolo, categorias, desde, ate, pagina, tamanho));
    }

    /**
     * Edição da newsletter da carteira monitorada, agrupada por ticker.
     *
     * <p>Exemplos: {@code /comunicados/newsletter} (semana do documento mais
     * recente), {@code ?semana=2026-W38}, {@code ?desde=2026-09-01&ate=2026-09-30}.
     */
    @GetMapping("/comunicados/newsletter")
    public ResponseEntity<NewsletterComunicadosDTO> newsletter(
            @RequestParam(required = false) String semana,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) List<String> categorias) {
        log.info("{}-Newsletter de comunicados | semana={} desde={} ate={} categorias={}",
                CONTROLADOR, semana, desde, ate, categorias);
        return ResponseEntity.ok(servicoComunicados.newsletter(semana, desde, ate, categorias));
    }
}
