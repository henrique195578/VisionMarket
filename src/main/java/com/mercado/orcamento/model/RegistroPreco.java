package com.mercado.orcamento.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class RegistroPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Enumerated(EnumType.STRING)
    private Mercado mercado;

    private String regiao; // Ex: "Centro", "Zona Norte" (Pode ser inferido do Mercado futuramente)

    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    private TipoPreco tipoPreco;

    private LocalDateTime dataRegistro;

    public RegistroPreco(Produto produto, Mercado mercado, BigDecimal valor, TipoPreco tipoPreco) {
        this.produto = produto;
        this.mercado = mercado;
        this.valor = valor;
        this.tipoPreco = tipoPreco;
        this.dataRegistro = LocalDateTime.now();
        this.regiao = "Padrao"; // Default por enquanto
    }
}
