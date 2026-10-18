package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CarteiraAtivoRequest(LocalDate dataEntrada, BigDecimal capitalAlocado) {
}
