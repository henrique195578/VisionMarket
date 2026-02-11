package com.mercado.orcamento.dto;

import lombok.Data;

@Data
public class DadosExtraidos {
    private String textoBruto;
    private String precoEncontrado;
    private String pesoEncontrado;
    private String nomePossivel;
    private String codigoBarras;
}
