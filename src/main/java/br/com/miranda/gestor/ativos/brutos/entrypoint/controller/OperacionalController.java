package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.OperacionalDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoOperacional;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Leitura do sistema operavel em MODO SIMULADO (plano OPR, OPR-GES-2..4). So GET e sem efeito colateral:
 * nao inicia coleta, analise, operacao nem mensagem; quem grava e o gerar-insights. Parametro invalido
 * responde 400; sem dados, 200 com a estrutura vazia e um aviso.
 */
@RestController
@RequestMapping("/operacional")
@RequiredArgsConstructor
public class OperacionalController {

    private final ServicoOperacional servico;

    @GetMapping("/resumo")
    public OperacionalDTO.Resumo resumo(@RequestParam(required = false) String versaoRegra) {
        return servico.resumo(versaoRegra);
    }

    @GetMapping("/diario")
    public OperacionalDTO.Diario diario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String versaoRegra) {
        return servico.diario(inicio, fim, versaoRegra);
    }

    @GetMapping("/operacoes")
    public OperacionalDTO.Operacoes operacoes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String simbolo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) String versaoRegra) {
        return servico.operacoes(status, simbolo, inicio, fim, motivo, limite, versaoRegra);
    }

    @GetMapping("/bloqueios")
    public OperacionalDTO.Bloqueios bloqueios(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) String simbolo,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) String versaoRegra) {
        return servico.bloqueios(data, simbolo, motivo, limite, versaoRegra);
    }

    @GetMapping("/saude")
    public OperacionalDTO.Saude saude(@RequestParam(required = false) String versaoRegra) {
        return servico.saude(versaoRegra);
    }
}
