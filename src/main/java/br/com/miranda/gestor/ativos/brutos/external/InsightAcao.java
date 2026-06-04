
package br.com.miranda.gestor.ativos.brutos.external;

import br.com.miranda.gestor.ativos.brutos.tools.JsonNodeConverter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "insight_acao",
       indexes = {
           @Index(name = "idx_simbolo_insight", columnList = "simbolo"),
           @Index(name = "idx_data_analise", columnList = "data_analise")
       }
)
public class InsightAcao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10, nullable = false)
    private String simbolo;

    @Column(name = "data_analise", nullable = false)
    private LocalDateTime dataAnalise;

    @Column(name = "preco_justo_graham", precision = 12, scale = 4)
    private BigDecimal precoJustoGraham;

    @Column(name = "margem_seguranca_percent", precision = 10, scale = 4)
    private BigDecimal margemSegurancaPercent;

    @Column(length = 20)
    private String recomendacao;

    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "detalhes_json", columnDefinition = "json")
    private JsonNode detalhesJson;

}

