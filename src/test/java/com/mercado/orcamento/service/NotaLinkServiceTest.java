package com.mercado.orcamento.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotaLinkServiceTest {
    private final NotaLinkService links=new NotaLinkService(new NotaDocumentoParser(new NotaFiscalParser()),new FotoService("./target/test-links"));
    @Test void aceitaConsultaOficialComSeparadoresDoQr() {
        var uri=links.validarLink("http://www.nfce.fazenda.sp.gov.br/qrcode?p=123|3|1");
        assertEquals("https",uri.getScheme());
        assertTrue(uri.toString().contains("%7C"));
    }
    @Test void rejeitaEnderecoLocalOuDominiosFalsos() {
        for (String url:new String[]{"http://localhost:3939/","http://127.0.0.1/","file:///C:/Windows/win.ini",
                "https://www.nfce.fazenda.sp.gov.br.evil.com/","https://evil.com/?url=nfce.fazenda.sp.gov.br",
                "https://user:pass@www.nfce.fazenda.sp.gov.br/","https://www.nfce.fazenda.sp.gov.br:8080/"}) {
            assertThrows(IllegalArgumentException.class,()->links.validarLink(url),url);
        }
    }
}
