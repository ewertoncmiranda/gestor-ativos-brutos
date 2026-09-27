package br.com.miranda.gestor.ativos.brutos.service.cotacao;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Composite: pergunta a todas as fontes e fica com o preco mais recente. Para
 * um favorito no pregao vence a BRAPI; para o resto (e fora do pregao), o
 * fechamento oficial. E a implementacao injetada por padrao ({@code @Primary}).
 */
@Primary
@Component
public class CotacaoMaisRecente implements FonteCotacaoRecente {

    private final List<FonteCotacaoRecente> fontes;

    public CotacaoMaisRecente(CotacaoOficialCotahist oficial, CotacaoIntradiariaCache intradiaria) {
        this.fontes = List.of(oficial, intradiaria);
    }

    @Override
    public Optional<CotacaoRecente> cotacao(String simbolo) {
        return fontes.stream()
                .map(fonte -> fonte.cotacao(simbolo))
                .flatMap(Optional::stream)
                .filter(c -> Objects.nonNull(c.referencia()))
                .max(Comparator.comparing(CotacaoRecente::referencia));
    }
}
