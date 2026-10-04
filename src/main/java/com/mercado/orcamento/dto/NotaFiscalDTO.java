package com.mercado.orcamento.dto;

import com.mercado.orcamento.model.Mercado;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class NotaFiscalDTO {
    public record Item(String nome, String marca, String peso, String codigoBarras,
                       BigDecimal quantidade, String unidadeMedida, BigDecimal precoUnitario,
                       BigDecimal total, String aviso) {}
    public record Leitura(String arquivo, String texto, String codigoQr, List<Item> itens, String aviso,
                          LocalDateTime dataCompra, String emitente, String urlConsulta) {
        public Leitura(String arquivo, String texto, String codigoQr, List<Item> itens, String aviso) {
            this(arquivo,texto,codigoQr,itens,aviso,null,"","");
        }
    }
    public record Importacao(String arquivo, String codigoQr, Mercado mercado, String unidade,
                             LocalDateTime dataCompra, boolean adicionarNaLista, List<Item> itens, String emitente) {
        public Importacao(String arquivo, String codigoQr, Mercado mercado, String unidade,
                          LocalDateTime dataCompra, boolean adicionarNaLista, List<Item> itens) {
            this(arquivo,codigoQr,mercado,unidade,dataCompra,adicionarNaLista,itens,null);
        }
    }
    public record Resultado(int itensSalvos, Long primeiroProdutoId) {}
}
