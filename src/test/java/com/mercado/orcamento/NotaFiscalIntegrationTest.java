package com.mercado.orcamento;

import com.mercado.orcamento.dto.NotaFiscalDTO.*;
import com.mercado.orcamento.model.*;
import com.mercado.orcamento.repository.*;
import com.mercado.orcamento.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:notas;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
    "visionmarket.fotos.path=./target/test-fotos",
    "visionmarket.ocr.tessdata-path=./data/tessdata", "visionmarket.ocr.language=por+eng",
    "logging.file.name=./target/test-app.log"
})
@AutoConfigureMockMvc
@Transactional
class NotaFiscalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired FotoService fotos;
    @Autowired OcrService ocr;
    @Autowired NotaFiscalService notas;
    @Autowired ProdutoRepository produtos;
    @Autowired RegistroPrecoRepository precos;

    private MockMultipartFile foto() throws Exception {
        BufferedImage imagem = new BufferedImage(1600, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,1600,420);
        g.setColor(Color.BLACK); g.setFont(new Font("Monospaced", Font.PLAIN, 40));
        String[] linhas = {"NOTA FISCAL", "001 123456 FEIJAO 2 UN X 8,49 16,98", "002 987654 LEITE 3 UN X 4,99 14,97", "TOTAL 31,95"};
        for (int i=0;i<linhas.length;i++) g.drawString(linhas[i],40,80+i*80);
        g.dispose(); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(imagem,"png",bytes);
        return new MockMultipartFile("file","nota.png","image/png",bytes.toByteArray());
    }
    private Importacao pedido(String arquivo, BigDecimal preco, BigDecimal total) {
        return new Importacao(arquivo, "", Mercado.PANTOJA, "CENTRO", LocalDateTime.now().minusMonths(5), false,
                List.of(new Item("FEIJAO", "MARCA", "1KG", "", new BigDecimal("2"), "UN", preco, total, "")));
    }
    @Test void adicionarItemSemPrecoExibeNaListaAposRedirecionamento() throws Exception {
        var resultado = mvc.perform(post("/minha-lista/adicionar")
                .param("nome", "FEIJAO REGRESSAO LISTA")
                .param("quantidadeDesejada", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?etapa=lista"))
                .andReturn();
        entityManager.flush();
        entityManager.clear();
        String html = mvc.perform(get("/").param("etapa", "lista")
                .flashAttrs(resultado.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var documento = org.jsoup.Jsoup.parse(html);
        assertEquals("FEIJAO REGRESSAO LISTA", documento.selectFirst("#lista .painel-lista strong").text());
        assertEquals("1", documento.selectFirst("#lista input[name=quantidadeDesejada][aria-label]").val());
        assertEquals(1, documento.select("#produtoFoto option").stream()
                .filter(o -> o.text().contains("FEIJAO REGRESSAO LISTA")).count());
    }
    @Test void sessaoInvalidaPodeRetomarListaSemLiberarOperacao() throws Exception {
        var cookie = new jakarta.servlet.http.Cookie("APP_SESSION_TOKEN", "sessao-invalida-regressao");
        mvc.perform(post("/minha-lista/adicionar").cookie(cookie)
                .param("nome", "NAO DEVE SALVAR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/acesso-negado"));
        mvc.perform(get("/acesso-negado")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Voltar à minha lista")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("window.close"))));
        mvc.perform(get("/").param("etapa", "lista").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("APP_SESSION_TOKEN"));
        assertTrue(produtos.findAll().stream().noneMatch(p -> "NAO DEVE SALVAR".equals(p.getNome())));
    }
    @Test void versaoPublicaSemCacheETelaComAtualizacaoManual() throws Exception {
        mvc.perform(get("/api/versao").cookie(new jakarta.servlet.http.Cookie("APP_SESSION_TOKEN", "invalido")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.build").isNotEmpty());
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("vision-build")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("atualizarAplicacao")));
    }
    @Test void chaveManualRejeitaTamanhoEDigitoInvalidos() throws Exception {
        mvc.perform(post("/api/notas/chave").contentType("application/json").content("{\"chave\":\"1234\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/notas/chave").contentType("application/json")
                .content("{\"chave\":\"35261006057223030755650110000447971110028221\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("notaChaveForm")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("notaDigitarManual")));
    }
    @Test void paginaRenderizaMenuLateralELeitor() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Leitor QR")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("notaForm")));
    }
    @Test void importaPrecoUnitarioComDataOriginalSemDuplicarNota() throws Exception {
        String arquivo = fotos.salvar(foto());
        var pedido = pedido(arquivo,new BigDecimal("8.49"),new BigDecimal("16.98"));
        var resultado = notas.importar(pedido);
        var produto = produtos.findById(resultado.primeiroProdutoId()).orElseThrow();
        var registros = precos.findByProduto(produto);
        assertEquals(1, registros.size());
        assertEquals(TipoPreco.PAGO,registros.get(0).getTipoPreco());
        assertEquals(new BigDecimal("8.49"),registros.get(0).getValor());
        assertEquals(pedido.dataCompra(),registros.get(0).getDataRegistro());
        assertFalse(produto.isNaListaDeCompras());
        assertThrows(IllegalArgumentException.class, () -> notas.importar(pedido));
        assertEquals(1,precos.findByProduto(produto).size());
    }
    @Test void importaPelaApiEExibePrecoPagoNoHistorico() throws Exception {
        String arquivo = fotos.salvar(foto());
        var pedido = pedido(arquivo,new BigDecimal("8.49"),new BigDecimal("16.98"));
        mvc.perform(post("/api/notas/importar").contentType("application/json").content(mapper.writeValueAsString(pedido)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itensSalvos").value(1));
        var produto = produtos.findAll().get(0);
        mvc.perform(get("/api/produtos/" + produto.getId() + "/historico"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].tipoPreco").value("PAGO"))
                .andExpect(jsonPath("$[0].notaFiscalId").exists());
    }
    @Test void preservaQuatroCasasDoPrecoUnitarioNoBanco() throws Exception {
        String arquivo = fotos.salvar(foto());
        var resultado = notas.importar(pedido(arquivo,new BigDecimal("8.4950"),new BigDecimal("16.99")));
        entityManager.flush(); entityManager.clear();
        var registros = precos.findByProduto(produtos.findById(resultado.primeiroProdutoId()).orElseThrow());
        assertEquals(0,new BigDecimal("8.4950").compareTo(registros.get(0).getValor()));
    }
    @Test void rejeitaTotaisInconsistentesSemSalvarProdutos() throws Exception {
        String arquivo = fotos.salvar(foto()); long antes = produtos.count();
        assertThrows(IllegalArgumentException.class, () -> notas.importar(pedido(arquivo,new BigDecimal("8.49"),new BigDecimal("10.00"))));
        assertEquals(antes,produtos.count());
    }
    @Test void rejeitaArquivoQueNaoEhFoto() throws Exception {
        mvc.perform(multipart("/api/notas/ler").file(new MockMultipartFile("file","texto.txt","text/plain","invalido".getBytes())))
                .andExpect(status().isBadRequest());
    }
    @Test void identificaQrDaCameraSemOcrOuSalvarFoto() throws Exception {
        String url="https://www.nfce.fazenda.sp.gov.br/qrcode?p=123%7C3%7C1";
        var matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(url,com.google.zxing.BarcodeFormat.QR_CODE,500,500);
        var bytes=new ByteArrayOutputStream();
        com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(matrix,"png",bytes);
        mvc.perform(multipart("/api/notas/qr").file(new MockMultipartFile("file","qr.png","image/png",bytes.toByteArray())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigoQr").value(url));
    }
    @Test void retornaSemCodigoQuandoQuadroDaCameraNaoTemQr() throws Exception {
        mvc.perform(multipart("/api/notas/qr").file(foto())).andExpect(status().isNoContent());
    }
    @Test void ocrReconheceItensDeUmaFotoDeComprovante() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(new File("./data/tessdata/por.traineddata").exists(), "Modelo OCR local não configurado");
        String arquivo = fotos.salvar(foto());
        var dados = ocr.extrairDadosDaImagem(arquivo);
        var itens = new NotaFiscalParser().analisar(dados.getTextoBruto());
        assertEquals(2,itens.size(), dados.getTextoBruto());
        assertEquals(new BigDecimal("8.49"), itens.get(0).precoUnitario());
    }
}
