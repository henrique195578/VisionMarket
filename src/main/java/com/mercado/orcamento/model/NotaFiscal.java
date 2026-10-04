package com.mercado.orcamento.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class NotaFiscal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String hashArquivo;
    @Column(unique = true, length = 64)
    private String hashQr;
    private String nomeArquivoImagem;
    @Enumerated(EnumType.STRING)
    private Mercado mercado;
    private String unidade;
    private LocalDateTime dataCompra;
    private LocalDateTime dataImportacao;
    private Integer quantidadeItens;
}
