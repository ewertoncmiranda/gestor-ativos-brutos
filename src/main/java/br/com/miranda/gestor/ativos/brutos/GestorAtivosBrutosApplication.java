package br.com.miranda.gestor.ativos.brutos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.MAIN;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class GestorAtivosBrutosApplication {


    public static void main(String[] args) {
        log.info("{} ========================================", MAIN);
        log.info("{} Iniciando aplicação: GestorAtivosBrutosApplication", MAIN);
        log.info("{} ========================================", MAIN);

        SpringApplication.run(GestorAtivosBrutosApplication.class, args);

        log.info("{} ========================================", MAIN);
        log.info("{} Aplicação iniciada com sucesso!", MAIN);
        log.info("{} ========================================", MAIN);
    }

}
