package com.mercado.orcamento.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class NotaDocumentoParserTest {
    private final NotaDocumentoParser parser = new NotaDocumentoParser(new NotaFiscalParser());

    @Test void extraiCamposDoHtmlSemAcrescentarTotalAoNome() {
        var nota = parser.html("<div id='u20'>MERCADO TESTE</div><table id='tabResult'><tr><td>"
                +"<span class='txtTit'>FEIJAO 1KG</span><span class='Rqtd'>Qtde.: 2</span>"
                +"<span class='RUN'>UN: UN</span><span class='RvlUnit'>Vl. Unit.: 8,49</span>"
                +"</td><td class='txtTit'>Vl. Total<span class='valor'>16,98</span></td></tr></table>"
                +"<p>Emissão: 03/10/2026 13:45:17</p>");
        assertEquals(1,nota.itens().size());
        assertEquals("FEIJAO 1KG",nota.itens().get(0).nome());
        assertEquals(new BigDecimal("8.49"),nota.itens().get(0).precoUnitario());
        assertEquals(LocalDateTime.of(2026,10,3,13,45,17),nota.dataCompra());
        assertEquals("MERCADO TESTE",nota.emitente());
    }

    @Test void aceitaQuantidadeComPontoDecimalNoHtml() {
        var nota=parser.html("<table><tr><td><span class='txtTit'>BANANA</span>"
                +"<span class='Rqtd'>Qtde.: 0.750</span><span class='RUN'>UN: KG</span>"
                +"<span class='RvlUnit'>Vl. Unit.: 6,00</span><span class='valor'>4,50</span></td></tr></table>");
        assertEquals(new BigDecimal("0.750"),nota.itens().get(0).quantidade());
    }

    @Test void extraiXmlOriginalComDescontoEData() {
        String xml="<nfeProc xmlns='http://www.portalfiscal.inf.br/nfe'><NFe><infNFe><ide><dhEmi>2026-10-03T13:45:17-03:00</dhEmi></ide>"
                +"<emit><xNome>MERCADO TESTE</xNome></emit><det nItem='1'><prod>"
                +"<xProd>FEIJAO</xProd><cEAN>7891234567890</cEAN><qCom>2.0000</qCom><uCom>UN</uCom>"
                +"<vProd>20.00</vProd><vDesc>3.02</vDesc></prod></det></infNFe></NFe></nfeProc>";
        var nota=parser.xml(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(new BigDecimal("8.49"),nota.itens().get(0).precoUnitario());
        assertEquals(new BigDecimal("16.98"),nota.itens().get(0).total());
        assertEquals("7891234567890",nota.itens().get(0).codigoBarras());
        assertEquals(LocalDateTime.of(2026,10,3,13,45,17),nota.dataCompra());
    }
    @Test void rejeitaXmlComEntidadeExterna() {
        String xml="<!DOCTYPE nota [<!ENTITY teste SYSTEM 'file:///C:/Windows/win.ini'>]><nota>&teste;</nota>";
        assertThrows(IllegalArgumentException.class,()->parser.xml(xml.getBytes()));
    }
    @Test void consultaSemItensNaoInventaProdutos() {
        assertTrue(parser.html("<form>Chave de acesso: Digite os caracteres da imagem CAPTCHA</form>").itens().isEmpty());
    }
    @Test void validaFormatoDaNotaRealFornecidaSemDependerDaRede() throws Exception {
        Path arquivo=Path.of("target/consulta-validation/nota-real.html");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(arquivo));
        var nota=parser.html(Files.readString(arquivo));
        assertTrue(nota.itens().size()>20);
        assertEquals("MOSTARDA HEMMER 1kg",nota.itens().get(0).nome());
        assertEquals(new BigDecimal("18.99"),nota.itens().get(0).precoUnitario());
        assertEquals(LocalDateTime.of(2026,10,3,13,45,17),nota.dataCompra());
    }
}
