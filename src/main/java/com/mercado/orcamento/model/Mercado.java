package com.mercado.orcamento.model;

public enum Mercado {
    COOVABRA("Mercado Coovabra"),
    ATACADAO("Mercado Atacadão"),
    PANTOJA("Mercado Pantoja"),
    EXAMINE("Mercado Examine"),
    BANANAS("Mercado Bananas");

    private final String nomeExibicao;

    Mercado(String nomeExibicao) {
        this.nomeExibicao = nomeExibicao;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }
}
