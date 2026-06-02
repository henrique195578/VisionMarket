package com.mercado.orcamento.service;

import com.mercado.orcamento.model.Mercado;
import com.mercado.orcamento.model.Produto;
import com.mercado.orcamento.model.RegistroPreco;
import com.mercado.orcamento.model.TipoPreco;
import com.mercado.orcamento.repository.ProdutoRepository;
import com.mercado.orcamento.repository.RegistroPrecoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrcamentoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private RegistroPrecoRepository registroPrecoRepository;

    private OrcamentoService service;

    @BeforeEach
    void setUp() {
        service = new OrcamentoService(produtoRepository, registroPrecoRepository);
        lenient().when(produtoRepository.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void deveCriarNovoItemNaMissaoComCamposNormalizados() {
        when(produtoRepository.findAll()).thenReturn(Collections.emptyList());

        Produto produto = service.adicionarItemNaLista("feijao carioca", " 1kg ", "kicaldo", 2);

        assertEquals("FEIJAO CARIOCA", produto.getNome());
        assertEquals("1KG", produto.getPeso());
        assertEquals("KICALDO", produto.getMarca());
        assertEquals(2, produto.getQuantidadeDesejada());
        assertTrue(produto.isNaListaDeCompras());
    }

    @Test
    void deveSomarQuantidadeQuandoProdutoJaEstiverNaLista() {
        Produto existente = new Produto("ARROZ", null);
        existente.setNaListaDeCompras(true);
        existente.setQuantidadeDesejada(2);
        existente.setPeso("5KG");
        existente.setMarca("TIO JOAO");

        when(produtoRepository.findAll()).thenReturn(List.of(existente));

        Produto produto = service.adicionarItemNaLista("arroz", "5kg", "tio joao", 3);

        assertEquals(5, produto.getQuantidadeDesejada());
        assertEquals("5KG", produto.getPeso());
        assertEquals("TIO JOAO", produto.getMarca());
        assertTrue(produto.isNaListaDeCompras());
    }

    @Test
    void deveAtualizarPreferenciasDoItemDaMissao() {
        Produto existente = new Produto("CAFE", null);
        existente.setNaListaDeCompras(true);
        existente.setPeso("500G");
        existente.setMarca("ANTIGA");
        existente.setQuantidadeDesejada(1);

        when(produtoRepository.findById(10L)).thenReturn(Optional.of(existente));

        Produto produto = service.atualizarPreferenciasItemDaLista(10L, "1kg", "", 4);

        assertEquals("1KG", produto.getPeso());
        assertEquals(null, produto.getMarca());
        assertEquals(4, produto.getQuantidadeDesejada());
        assertTrue(produto.isNaListaDeCompras());
    }

    @Test
    void deveCadastrarProdutoComPrecoNoMesmoFluxo() {
        Produto produtoSalvo = new Produto("ARROZ", null);
        produtoSalvo.setId(20L);

        when(produtoRepository.findAll()).thenReturn(Collections.emptyList());
        when(produtoRepository.findById(20L)).thenReturn(Optional.of(produtoSalvo));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(invocation -> {
            Produto produto = invocation.getArgument(0);
            produto.setId(20L);
            return produto;
        });

        Produto produto = service.cadastrarProdutoComPrecos("arroz", null, "prato fino", "5kg", Mercado.ATACADAO,
                new java.math.BigDecimal("29.90"), null, null);

        assertEquals(20L, produto.getId());
        verify(registroPrecoRepository).save(any());
    }

    @Test
    void deveSalvarCodigoBarrasVazioComoNulo() {
        when(produtoRepository.findAll()).thenReturn(Collections.emptyList());

        Produto produto = service.adicionarItem("feijao", "   ", "marca boa", "1kg");

        assertEquals(null, produto.getCodigoBarras());
        assertEquals("FEIJAO", produto.getNome());
        assertEquals("MARCA BOA", produto.getMarca());
        assertEquals("1KG", produto.getPeso());
    }

    @Test
    void deveCadastrarNovoProdutoQuandoPesoForDiferente() {
        Produto existente = new Produto("PAPEL HIGIENICO", null);
        existente.setMarca("PERSONAL");
        existente.setPeso("4 ROLOS");

        when(produtoRepository.findAll()).thenReturn(List.of(existente));

        Produto produto = service.adicionarItem("papel higienico", null, "personal", "20 rolos", Mercado.ATACADAO);

        assertEquals("20 ROLOS", produto.getPeso());
    }

    @Test
    void deveCadastrarNovoProdutoQuandoMercadoForDiferente() {
        Produto existente = new Produto("PAPEL HIGIENICO", null);
        existente.setId(5L);
        existente.setMarca("PERSONAL");
        existente.setPeso("4 ROLOS");
        existente.getHistoricoPrecos().add(new RegistroPreco(existente, Mercado.ATACADAO, new java.math.BigDecimal("12.90"), com.mercado.orcamento.model.TipoPreco.VAREJO));

        when(produtoRepository.findAll()).thenReturn(List.of(existente));

        Produto produto = service.adicionarItem("papel higienico", null, "personal", "4 rolos", Mercado.PANTOJA);

        assertEquals("PAPEL HIGIENICO", produto.getNome());
        assertEquals("4 ROLOS", produto.getPeso());
        assertEquals("PERSONAL", produto.getMarca());
    }

    @Test
    void deveEditarDescricaoEManterRegistroMaisRecenteDoMercado() {
        Produto produto = new Produto("AMACIANTE", null);
        produto.setId(30L);
        produto.setMarca("DOWNY");
        produto.setPeso("1L");

        RegistroPreco precoVarejo = new RegistroPreco(produto, Mercado.PANTOJA, new java.math.BigDecimal("23.99"), TipoPreco.VAREJO);

        when(produtoRepository.findById(30L)).thenReturn(Optional.of(produto));
        when(registroPrecoRepository.findTopByProdutoIdAndMercadoAndTipoPrecoOrderByDataRegistroDesc(30L, Mercado.PANTOJA, TipoPreco.VAREJO))
                .thenReturn(Optional.of(precoVarejo));
        when(registroPrecoRepository.findTopByProdutoIdAndMercadoAndTipoPrecoOrderByDataRegistroDesc(30L, Mercado.PANTOJA, TipoPreco.ATACADO))
                .thenReturn(Optional.empty());
        when(registroPrecoRepository.findTopByProdutoIdAndMercadoAndTipoPrecoOrderByDataRegistroDesc(30L, Mercado.PANTOJA, TipoPreco.CARTAO))
                .thenReturn(Optional.empty());

        Produto produtoAtualizado = service.atualizarRegistroDaGrade(30L, Mercado.PANTOJA, "amaciante suavizante", "downy", "1l",
                new java.math.BigDecimal("19.99"), null, null);

        assertEquals("AMACIANTE SUAVIZANTE", produtoAtualizado.getNome());
        assertEquals("DOWNY", produtoAtualizado.getMarca());
        assertEquals("1L", produtoAtualizado.getPeso());
        assertEquals(new java.math.BigDecimal("19.99"), precoVarejo.getValor());
        verify(registroPrecoRepository).save(precoVarejo);
    }
}
