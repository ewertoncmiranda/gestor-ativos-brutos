package br.com.miranda.gestor.ativos.brutos.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.UTILS ;
@Slf4j
public class Utils {

    public static String toJson(Object obj) {
        try {
            log.debug("{}-Iniciando conversão para JSON. Tipo: {}", UTILS, obj.getClass().getSimpleName());

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(obj);

            log.debug("{}-JSON gerado com sucesso. Tamanho: {} bytes", UTILS, json.length());

            return json;
        } catch (Exception e) {
            log.error("{}-Erro ao converter objeto para JSON. Tipo: {}, Erro: {}",
                    UTILS, obj.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Erro ao converter objeto para JSON", e);
        }
    }


}
