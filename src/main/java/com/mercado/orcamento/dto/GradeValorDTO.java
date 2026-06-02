package com.mercado.orcamento.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class GradeValorDTO {
    private Long idProduto;
    private String nomeProduto;
    private String marca;
    private String peso;
    private String mercadoCodigo;
    private String nomeMercado;
    private String dataHoraCadastro;
    private BigDecimal precoVarejo;
    private BigDecimal precoAtacado;
    private BigDecimal precoCartao;
}
