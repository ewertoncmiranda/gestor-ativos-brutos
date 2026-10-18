package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraAtivoRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraFinanceiraDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraOperacaoRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraProventoRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.CriarCarteiraRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.ImportacaoCarteirasDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.ImportacaoCarteirasRequest;
import br.com.miranda.gestor.ativos.brutos.service.ServicoCarteiras;
import br.com.miranda.gestor.ativos.brutos.service.ServicoCarteiraFinanceira;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/carteiras")
public class CarteirasController {
    private final ServicoCarteiras servico;
    private final ServicoCarteiraFinanceira servicoFinanceiro;

    public CarteirasController(ServicoCarteiras servico, ServicoCarteiraFinanceira servicoFinanceiro) {
        this.servico = servico;
        this.servicoFinanceiro = servicoFinanceiro;
    }

    @GetMapping
    public List<CarteiraDTO> listar() { return servico.listar(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarteiraDTO criar(@RequestBody CriarCarteiraRequest request) { return servico.criar(request); }

    @PostMapping("/importacoes")
    public ImportacaoCarteirasDTO importar(@RequestBody ImportacaoCarteirasRequest request) {
        return servico.importar(request);
    }

    @GetMapping("/{id}")
    public CarteiraDTO buscar(@PathVariable String id) { return servico.buscar(id); }

    @PutMapping("/{id}")
    public CarteiraDTO atualizar(@PathVariable String id, @RequestBody CriarCarteiraRequest request) {
        return servico.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arquivar(@PathVariable String id) { servico.arquivar(id); }

    @PutMapping("/{id}/ativos/{simbolo}")
    public CarteiraDTO.AtivoDTO salvarAtivo(@PathVariable String id, @PathVariable String simbolo,
                                            @RequestBody CarteiraAtivoRequest request) {
        return servico.salvarAtivo(id, simbolo, request);
    }

    @DeleteMapping("/{id}/ativos/{simbolo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerAtivo(@PathVariable String id, @PathVariable String simbolo) {
        servico.removerAtivo(id, simbolo);
    }

    @GetMapping("/{id}/operacoes")
    public List<CarteiraFinanceiraDTO.Operacao> listarOperacoes(@PathVariable String id) {
        return servicoFinanceiro.listarOperacoes(id);
    }

    @PostMapping("/{id}/operacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public CarteiraFinanceiraDTO.Operacao registrarOperacao(@PathVariable String id,
                                                            @RequestBody CarteiraOperacaoRequest request) {
        return servicoFinanceiro.registrarOperacao(id, request);
    }

    @PostMapping("/{id}/operacoes/{operacaoId}/estorno")
    public CarteiraFinanceiraDTO.Operacao estornarOperacao(@PathVariable String id,
                                                           @PathVariable String operacaoId,
                                                           @RequestParam String chaveIdempotencia) {
        return servicoFinanceiro.estornarOperacao(id, operacaoId, chaveIdempotencia);
    }

    @GetMapping("/{id}/proventos")
    public List<CarteiraFinanceiraDTO.Provento> listarProventos(@PathVariable String id) {
        return servicoFinanceiro.listarProventos(id);
    }

    @PostMapping("/{id}/proventos")
    @ResponseStatus(HttpStatus.CREATED)
    public CarteiraFinanceiraDTO.Provento registrarProvento(@PathVariable String id,
                                                           @RequestBody CarteiraProventoRequest request) {
        return servicoFinanceiro.registrarProvento(id, request);
    }

    @PostMapping("/{id}/snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    public CarteiraFinanceiraDTO.Snapshot calcularSnapshot(@PathVariable String id,
                                                           @RequestParam LocalDate dataReferencia,
                                                           @RequestParam(required = false) String versaoCalculo) {
        return servicoFinanceiro.calcularSnapshot(id, dataReferencia, versaoCalculo);
    }

    @GetMapping("/{id}/snapshots/{dataReferencia}")
    public CarteiraFinanceiraDTO.Snapshot buscarSnapshot(@PathVariable String id,
                                                         @PathVariable LocalDate dataReferencia,
                                                         @RequestParam(required = false) String versaoCalculo) {
        return servicoFinanceiro.buscarSnapshot(id, dataReferencia, versaoCalculo);
    }

    @GetMapping("/{id}/rentabilidade")
    public List<CarteiraFinanceiraDTO.Rentabilidade> listarRentabilidade(@PathVariable String id,
                                                                         @RequestParam LocalDate inicio,
                                                                         @RequestParam LocalDate fim,
                                                                         @RequestParam(required = false) String versaoCalculo) {
        return servicoFinanceiro.listarSnapshots(id, inicio, fim, versaoCalculo);
    }
}
