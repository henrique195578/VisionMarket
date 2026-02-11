package com.mercado.orcamento.model;

public enum TipoPreco {
    VAREJO("Varejo (Unidade)"),
    ATACADO("Atacado (Quantidade)"),
    CARTAO("Clube/Cartão");

    private final String descricao;

    TipoPreco(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
