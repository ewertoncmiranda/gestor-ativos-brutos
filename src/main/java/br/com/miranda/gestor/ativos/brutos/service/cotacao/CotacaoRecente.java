package br.com.miranda.gestor.ativos.brutos.service.cotacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Preco mais recente conhecido de um ativo e de onde ele veio.
 *
 * @param referencia instante a que o preco se refere (fechamento do pregao, ou a cotacao intradiaria)
 * @param fonte      B3_COTAHIST (oficial, dia anterior) ou BRAPI (intradiaria, so favoritos)
 */
public record CotacaoRecente(BigDecimal preco, BigDecimal variacaoPercent, LocalDateTime referencia, String fonte) {
}
