package com.mercado.orcamento.service;

import com.mercado.orcamento.model.*;
import com.mercado.orcamento.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LeituraService {
    private final ProdutoRepository produtos;
    private final RegistroPrecoRepository precos;

    public LeituraService(ProdutoRepository produtos, RegistroPrecoRepository precos) {
        this.produtos = produtos;
        this.precos = precos;
    }

    @Transactional
    public void registrar(Long id, Mercado mercado, String unidade, BigDecimal varejo, BigDecimal atacado,
                          BigDecimal cartao, Integer minimo, String condicoes, String arquivo, String codigo) {
        if (mercado == null || unidade == null || unidade.isBlank())
            throw new IllegalArgumentException("Informe o mercado e a filial/endereço.");
        if (varejo == null && atacado == null && cartao == null)
            throw new IllegalArgumentException("Confira pelo menos um preço da etiqueta.");
        if (atacado != null && (minimo == null || minimo < 1))
            throw new IllegalArgumentException("Informe a quantidade mínima do atacado.");
        if (unidade.length() > 150 || (condicoes != null && condicoes.length() > 250))
            throw new IllegalArgumentException("Filial ou condições muito longas.");
        BigDecimal[] valores = {varejo, atacado, cartao};
        for (BigDecimal valor : valores) {
            if (valor != null && (valor.signum() <= 0 || valor.scale() > 2 || valor.compareTo(new BigDecimal("999999.99")) > 0))
                throw new IllegalArgumentException("Os preços devem ser positivos e ter até duas casas decimais.");
        }
        Produto produto = produtos.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Selecione o produto da lista."));
        if (codigo != null && !codigo.isBlank()) {
            codigo = codigo.trim();
            if (codigo.length() > 100) throw new IllegalArgumentException("Código de barras inválido.");
            if (produto.getCodigoBarras() != null && !codigo.equals(produto.getCodigoBarras()))
                throw new IllegalArgumentException("Código diferente: selecione ou cadastre a variante correta.");
            Optional<Produto> outro = produtos.findByCodigoBarras(codigo);
            if (outro.isPresent() && !Objects.equals(outro.get().getId(), produto.getId()))
                throw new IllegalArgumentException("Código já cadastrado em outro item. Selecione esse produto.");
            produto.setCodigoBarras(codigo);
            produtos.save(produto);
        }
        TipoPreco[] tipos = {TipoPreco.VAREJO, TipoPreco.ATACADO, TipoPreco.CARTAO};
        LocalDateTime agora = LocalDateTime.now();
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] == null) continue;
            RegistroPreco registro = new RegistroPreco(produto, mercado, valores[i], tipos[i]);
            registro.setDataRegistro(agora);
            registro.setUnidade(unidade.trim().toUpperCase(Locale.ROOT));
            registro.setNomeArquivoImagem(arquivo == null || arquivo.isBlank() ? null : arquivo);
            registro.setQuantidadeMinima(tipos[i] == TipoPreco.ATACADO ? minimo : 1);
            registro.setCondicoes(condicoes == null ? null : condicoes.trim());
            precos.save(registro);
        }
    }

    public List<RegistroPreco> historico(Long id) {
        Produto produto = produtos.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
        return precos.findByProduto(produto).stream()
                .sorted(Comparator.comparing(RegistroPreco::getDataRegistro,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RegistroPreco::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
