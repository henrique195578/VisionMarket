package com.mercado.orcamento.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RegistroPrecoDTO {
    private String nomeMercado;
    private String nomeProduto;
    private BigDecimal valor;
    private String tipoPreco; // Ex: "Atacado", "Varejo"
    private boolean melhorPreco;
}
