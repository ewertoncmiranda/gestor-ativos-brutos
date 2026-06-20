package br.com.miranda.gestor.ativos.brutos.tools;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoConversaoJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.CONVERSOR_JSON;

@Slf4j
public final class ConversorJson {

    private ConversorJson() {
    }

    /**
     * Serializa um objeto para JSON, padronizando o erro de conversão da aplicação.
     */
    public static String paraJson(Object obj) {
        try {
            log.debug("{}-Iniciando conversao para JSON. Tipo: {}", CONVERSOR_JSON, obj.getClass().getSimpleName());

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(obj);

            log.debug("{}-JSON gerado com sucesso. Tamanho: {} bytes", CONVERSOR_JSON, json.length());
            return json;
        } catch (Exception e) {
            log.error("{}-Erro ao converter objeto para JSON. Tipo: {}, Erro: {}",
                    CONVERSOR_JSON, obj.getClass().getSimpleName(), e.getMessage(), e);
            throw new ExcecaoConversaoJson("objeto " + obj.getClass().getSimpleName(), e);
        }
    }
}
