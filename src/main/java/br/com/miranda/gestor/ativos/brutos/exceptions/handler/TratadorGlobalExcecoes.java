package br.com.miranda.gestor.ativos.brutos.exceptions.handler;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoAplicacao;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;

@Slf4j
@RestControllerAdvice
public class TratadorGlobalExcecoes {

    /**
     * Converte exceções conhecidas da aplicação em resposta HTTP padronizada.
     */
    @ExceptionHandler(ExcecaoAplicacao.class)
    public ResponseEntity<RespostaErroApi> tratarExcecaoAplicacao(
            ExcecaoAplicacao ex,
            HttpServletRequest request
    ) {
        log.error("(TRATADOR-EXCECOES)-Erro tratado. Codigo: {}, Caminho: {}, Mensagem: {}",
                ex.getCode(), request.getRequestURI(), ex.getMessage(), ex);
        return montarResposta(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI());
    }

    /**
     * Trata erros de parâmetros, validação e argumentos inválidos na entrada HTTP.
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<RespostaErroApi> tratarRequisicaoInvalida(Exception ex, HttpServletRequest request) {
        log.warn("(TRATADOR-EXCECOES)-Requisicao invalida. Caminho: {}, Mensagem: {}",
                request.getRequestURI(), ex.getMessage());
        return montarResposta(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Trata falhas inesperadas sem expor detalhes internos ao cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaErroApi> tratarExcecaoInesperada(Exception ex, HttpServletRequest request) {
        log.error("(TRATADOR-EXCECOES)-Erro inesperado. Caminho: {}, Mensagem: {}",
                request.getRequestURI(), ex.getMessage(), ex);
        return montarResposta(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Erro interno inesperado",
                request.getRequestURI()
        );
    }

    private ResponseEntity<RespostaErroApi> montarResposta(
            HttpStatus status,
            String code,
            String message,
            String path
    ) {
        RespostaErroApi response = new RespostaErroApi(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path
        );

        return ResponseEntity.status(status).body(response);
    }
}
