package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.AtivoListagemDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioListagemAtivos;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioListagemAtivos.Filtro;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioListagemAtivos.Linha;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Tabela unica de ativos do painel (TASK-UX-5): monta cada linha a partir da
 * pagina e da serie curta de fechamentos. Somente leitura - nao chama a
 * BRAPI, nao grava, nao publica.
 */
@Service
@RequiredArgsConstructor
public class ServicoListagemAtivos {

    public static final int TAMANHO_MAXIMO = 100;
    public static final int PONTOS_SPARKLINE = 20;

    private final RepositorioListagemAtivos repositorio;

    public Page<AtivoListagemDTO> listar(Filtro filtro, int pagina, int tamanho) {
        int paginaValida = Math.max(pagina, 0);
        int tamanhoValido = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO);
        PageRequest pedido = PageRequest.of(paginaValida, tamanhoValido);

        long total = repositorio.contar(filtro);
        if (total == 0 || pedido.getOffset() >= total) {
            return new PageImpl<>(List.of(), pedido, total);
        }
        List<Linha> linhas = repositorio.pagina(filtro, (int) pedido.getOffset(), tamanhoValido);
        Map<String, List<BigDecimal>> series = repositorio.fechamentosRecentes(
                linhas.stream().map(Linha::simbolo).toList(), PONTOS_SPARKLINE);
        List<AtivoListagemDTO> conteudo = linhas.stream()
                .map(l -> paraDto(l, series.getOrDefault(l.simbolo(), List.of())))
                .toList();
        return new PageImpl<>(conteudo, pedido, total);
    }

    static AtivoListagemDTO paraDto(Linha l, List<BigDecimal> serie) {
        AtivoListagemDTO.Sinal sinal = l.recomendacao() == null ? null
                : new AtivoListagemDTO.Sinal(l.recomendacao(), l.versaoRegra(), l.dataAnalise());
        AtivoListagemDTO.Comunicado comunicado = l.dataEntregaComunicado() == null ? null
                : new AtivoListagemDTO.Comunicado(l.categoriaComunicado(), l.assuntoComunicado(),
                        l.dataEntregaComunicado(), l.linkComunicado());
        boolean defasado = l.dataPregao() == null
                || (l.ultimoPregaoMercado() != null && l.dataPregao().isBefore(l.ultimoPregaoMercado()));
        AtivoListagemDTO.Selos selos = new AtivoListagemDTO.Selos(
                l.favorito(), l.monitorado(), l.temFundamento(), defasado);
        return new AtivoListagemDTO(l.simbolo(), l.nome(), l.setor(), l.situacaoRegistro(),
                l.fechamento(), l.dataPregao(),
                variacaoPercentual(l.fechamento(), l.fechamentoAnterior()), serie, sinal, comunicado, selos);
    }

    /** Variacao contra o pregao anterior do proprio ativo; {@code null} sem os dois precos. */
    static BigDecimal variacaoPercentual(BigDecimal atual, BigDecimal anterior) {
        if (atual == null || anterior == null || anterior.signum() == 0) return null;
        return atual.subtract(anterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(anterior, 2, RoundingMode.HALF_UP);
    }
}
