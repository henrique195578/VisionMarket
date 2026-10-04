package com.mercado.orcamento.dto;

import lombok.Data;

@Data
public class DadosExtraidos {
    private String textoBruto;
    private String precoVarejo;
    private String precoAtacado;
    private String precoCartao;
    private Integer quantidadeMinima;
    private String nomeArquivoImagem;
    private String descricaoProduto;
    private String precoEncontrado;
    private String pesoEncontrado;
    private String nomePossivel;
    private String codigoBarras;
}
