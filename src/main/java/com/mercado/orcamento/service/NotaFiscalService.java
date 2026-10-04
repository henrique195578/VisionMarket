package com.mercado.orcamento.service;

import com.mercado.orcamento.dto.NotaFiscalDTO.*;
import com.mercado.orcamento.model.*;
import com.mercado.orcamento.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class NotaFiscalService {
    private final ProdutoRepository produtos;
    private final RegistroPrecoRepository precos;
    private final NotaFiscalRepository notas;
    private final FotoService fotos;

    public NotaFiscalService(ProdutoRepository produtos, RegistroPrecoRepository precos, NotaFiscalRepository notas, FotoService fotos) {
        this.produtos = produtos; this.precos = precos; this.notas = notas; this.fotos = fotos;
    }

    @Transactional
    public Resultado importar(Importacao pedido) {
        validar(pedido);
        String hash = hashArquivo(pedido.arquivo());
        String hashQr = pedido.codigoQr() == null || pedido.codigoQr().isBlank() ? null
                : digest(pedido.codigoQr().trim().getBytes(StandardCharsets.UTF_8));
        if (notas.existsByHashArquivo(hash) || (hashQr != null && notas.existsByHashQr(hashQr)))
            throw new IllegalArgumentException("Esta nota já foi importada. Os produtos e preços não foram duplicados.");
        NotaFiscal nota = new NotaFiscal();
        nota.setHashArquivo(hash); nota.setHashQr(hashQr); nota.setNomeArquivoImagem(pedido.arquivo());
        nota.setMercado(pedido.mercado()); nota.setUnidade(normalizar(pedido.unidade()));
        nota.setDataCompra(pedido.dataCompra()); nota.setDataImportacao(LocalDateTime.now());
        nota.setQuantidadeItens(pedido.itens().size());
        notas.saveAndFlush(nota);
        Long primeiro = null;
        for (Item item : pedido.itens()) {
            Produto produto = localizar(item);
            if (produto == null) {
                produto = new Produto(normalizar(item.nome()), vazio(item.codigoBarras()));
                produto.setMarca(vazio(normalizar(item.marca()))); produto.setPeso(vazio(normalizar(item.peso())));
            }
            if (pedido.adicionarNaLista()) {
                produto.setNaListaDeCompras(true);
                produto.setQuantidadeDesejada(Math.max(1, item.quantidade().setScale(0, java.math.RoundingMode.CEILING).intValue()));
            }
            produto = produtos.save(produto);
            if (primeiro == null) primeiro = produto.getId();
            RegistroPreco preco = new RegistroPreco(produto, pedido.mercado(), item.precoUnitario(), TipoPreco.PAGO);
            preco.setDataRegistro(pedido.dataCompra()); preco.setNomeArquivoImagem(pedido.arquivo());
            preco.setUnidade(nota.getUnidade()); preco.setNotaFiscalId(nota.getId());
            preco.setQuantidadeCompra(item.quantidade()); preco.setUnidadeMedida(normalizar(item.unidadeMedida()));
            preco.setCondicoes("Preço pago na nota fiscal; modalidade e condições não identificadas.");
            precos.save(preco);
        }
        return new Resultado(pedido.itens().size(), primeiro);
    }

    private Produto localizar(Item item) {
        String codigo = vazio(item.codigoBarras());
        if (codigo != null) {
            var encontrado = produtos.findByCodigoBarras(codigo);
            if (encontrado.isPresent()) return encontrado.get();
        }
        return produtos.findAll().stream().filter(p -> Objects.equals(normalizar(p.getNome()), normalizar(item.nome())))
                .filter(p -> Objects.equals(normalizar(p.getMarca()), normalizar(item.marca())))
                .filter(p -> Objects.equals(normalizar(p.getPeso()), normalizar(item.peso())))
                .filter(p -> codigo == null ? p.getCodigoBarras() == null : codigo.equals(p.getCodigoBarras()))
                .findFirst().orElse(null);
    }

    private void validar(Importacao pedido) {
        if (pedido == null || pedido.arquivo() == null || !fotos.existe(pedido.arquivo()))
            throw new IllegalArgumentException("Envie um arquivo ou consulte o link da nota antes de importar.");
        if (pedido.mercado() == null || pedido.unidade() == null || pedido.unidade().isBlank() || pedido.unidade().length() > 150)
            throw new IllegalArgumentException("Informe o mercado e a filial da compra.");
        if (pedido.dataCompra() == null || pedido.dataCompra().isAfter(LocalDateTime.now().plusMinutes(5)))
            throw new IllegalArgumentException("Informe a data e hora da compra, sem usar uma data futura.");
        if (pedido.itens() == null || pedido.itens().isEmpty() || pedido.itens().size() > 500)
            throw new IllegalArgumentException("Confira entre 1 e 500 itens da nota.");
        for (Item item : pedido.itens()) {
            if (item == null || item.nome() == null || item.nome().isBlank() || item.nome().length() > 150
                    || (item.marca() != null && item.marca().length() > 100)
                    || (item.peso() != null && item.peso().length() > 100)
                    || (item.codigoBarras() != null && item.codigoBarras().length() > 100)
                    || item.unidadeMedida() == null || item.unidadeMedida().isBlank() || item.unidadeMedida().length() > 20)
                throw new IllegalArgumentException("Confira nome, embalagem e unidade de todos os itens.");
            if (item.quantidade() == null || item.quantidade().signum() <= 0 || item.quantidade().scale() > 4 || item.quantidade().compareTo(new BigDecimal("100000")) > 0
                    || item.precoUnitario() == null || item.precoUnitario().signum() <= 0
                    || item.precoUnitario().scale() > 4 || item.precoUnitario().compareTo(new BigDecimal("999999.99")) > 0)
                throw new IllegalArgumentException("Todos os itens precisam de quantidade e preço unitário positivos.");
            if (item.total() == null || item.total().signum() <= 0 || item.total().scale() > 2
                    || item.precoUnitario().multiply(item.quantidade()).subtract(item.total()).abs().compareTo(new BigDecimal("0.03")) > 0)
                throw new IllegalArgumentException("O total de um item difere de quantidade × preço. Confira o valor efetivamente pago e os descontos.");
        }
    }
    private String normalizar(String texto) { return texto == null ? "" : texto.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT); }
    private String vazio(String texto) { return texto == null || texto.isBlank() ? null : texto.trim(); }
    private String hashArquivo(String arquivo) {
        try { return digest(Files.readAllBytes(fotos.resolver(arquivo))); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Não foi possível ler a foto da nota."); }
    }
    private String digest(byte[] dados) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
