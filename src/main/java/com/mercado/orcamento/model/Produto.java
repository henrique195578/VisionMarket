package com.mercado.orcamento.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String codigoBarras; // EAN

    private String nome;
    private String marca;
    private String peso;
    private String imagemUrl;
    
    // Grupo de equivalência para produtos substitutos (Ex: "Arroz 5kg")
    private Long grupoEquivalenciaId; 

    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RegistroPreco> historicoPrecos = new ArrayList<>();
    
    private boolean naListaDeCompras;

    public Produto(String nome, String codigoBarras) {
        this.nome = nome;
        this.codigoBarras = codigoBarras;
    }

    // Helper para compatibilidade com a View (Thymeleaf e JS)
    // Removido JsonIgnore para permitir que o JS acesse os preços
    public java.util.Map<Mercado, java.util.Map<TipoPreco, java.math.BigDecimal>> getPrecos() {
        java.util.Map<Mercado, java.util.Map<TipoPreco, java.math.BigDecimal>> mapa = new java.util.HashMap<>();
        
        for (RegistroPreco rp : historicoPrecos) {
            if (rp.getMercado() == null || rp.getTipoPreco() == null || rp.getValor() == null) continue;
            
            // Se tiver múltiplos preços pro mesmo mercado/tipo, pega o mais recente (assumindo que a lista pode ter histórico antigo)
            // Aqui simplificamos pegando o último (ou sobrescrevendo)
            mapa.computeIfAbsent(rp.getMercado(), k -> new java.util.HashMap<>())
                .put(rp.getTipoPreco(), rp.getValor());
        }
        return mapa;
    }
}
