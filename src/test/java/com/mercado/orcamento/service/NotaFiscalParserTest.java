package com.mercado.orcamento.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class NotaFiscalParserTest {
    private final NotaFiscalParser parser = new NotaFiscalParser();
    @Test void separaItensSemImportarTotaisOuPagamento() {
        var itens = parser.analisar("001 123456 FEIJAO CARIOCA 1KG 2 UN X 8,49 16,98\n"
                + "002 987654 LEITE INTEGRAL 1L 3 UN X 4,99 14,97\nTOTAL 31,95\nCARTAO 31,95");
        assertEquals(2, itens.size());
        assertEquals("FEIJAO CARIOCA 1KG", itens.get(0).nome());
        assertEquals(new BigDecimal("8.49"), itens.get(0).precoUnitario());
        assertEquals(new BigDecimal("2"), itens.get(0).quantidade());
    }
    @Test void leDescricaoEValoresEmLinhasSeparadas() {
        var itens = parser.analisar("001 123456 FEIJAO CARIOCA\n2 UN X 8,49 16,98");
        assertEquals(1, itens.size());
        assertEquals("FEIJAO CARIOCA", itens.get(0).nome());
    }
    @Test void aceitaQuantidadeFracionadaEPrecoPorQuilo() {
        var itens = parser.analisar("001 BANANA 0,750 KG X 6,00 4,50");
        assertEquals(new BigDecimal("0.750"), itens.get(0).quantidade());
        assertEquals("KG", itens.get(0).unidadeMedida());
        assertEquals("", itens.get(0).aviso());
    }
    @Test void leCamposRotuladosDaNfce() {
        var itens = parser.analisar("FEIJAO CARIOCA\nQtd.: 2 UN: UN Vl. Unit.: 8,49 Vl. Total: 16,98");
        assertEquals(1, itens.size());
        assertEquals(new BigDecimal("16.98"), itens.get(0).total());
    }
    @Test void sinalizaDescontoOuLeituraInconsistente() {
        var itens = parser.analisar("ARROZ 2 UN X 10,00 18,00");
        assertFalse(itens.get(0).aviso().isEmpty());
    }
    @Test void naoCriaProdutosComCabecalhoOuNumerosSoltos() {
        assertTrue(parser.analisar("CNPJ 00.000.000/0001-00\nTOTAL 39,99\nTROCO 0,01").isEmpty());
    }
}
