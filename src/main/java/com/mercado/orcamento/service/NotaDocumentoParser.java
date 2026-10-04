package com.mercado.orcamento.service;

import com.mercado.orcamento.dto.NotaFiscalDTO.Item;
import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import java.io.ByteArrayInputStream;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

@Component
public class NotaDocumentoParser {
    public record Documento(List<Item> itens, String texto, LocalDateTime dataCompra, String emitente) {}
    private final NotaFiscalParser textoParser;
    public NotaDocumentoParser(NotaFiscalParser textoParser) { this.textoParser = textoParser; }

    public Documento xml(byte[] bytes) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            var doc = builder.parse(new ByteArrayInputStream(bytes));
            List<Item> itens = new ArrayList<>();
            var detalhes = doc.getElementsByTagNameNS("*","det");
            for (int i=0; i<detalhes.getLength(); i++) {
                if (i >= 500) throw new IllegalArgumentException("A nota deve conter até 500 itens.");
                Element produto = primeiro((Element)detalhes.item(i),"prod");
                if (produto == null) continue;
                String nome = valor(produto,"xProd"), codigo = valor(produto,"cEAN");
                if ("SEM GTIN".equalsIgnoreCase(codigo)) codigo = "";
                BigDecimal qtd = new BigDecimal(valor(produto,"qCom")).stripTrailingZeros();
                BigDecimal total = new BigDecimal(valor(produto,"vProd"));
                String desconto = valor(produto,"vDesc");
                if (!desconto.isBlank()) total = total.subtract(new BigDecimal(desconto));
                if (qtd.signum() <= 0 || total.signum() <= 0) continue;
                BigDecimal preco = total.divide(qtd,4,RoundingMode.HALF_UP).stripTrailingZeros();
                itens.add(new Item(nome,"","",codigo,qtd,valor(produto,"uCom"),preco,total.setScale(2,RoundingMode.HALF_UP),
                        desconto.isBlank() ? "" : "Preço unitário calculado após o desconto do item. Confira o valor pago."));
            }
            if (itens.isEmpty()) throw new IllegalArgumentException("O XML não contém itens de uma NF-e/NFC-e.");
            Element raiz = doc.getDocumentElement(), emit = primeiro(raiz,"emit");
            String data = valor(raiz,"dhEmi");
            LocalDateTime compra = null;
            if (!data.isBlank()) {
                try { compra = OffsetDateTime.parse(data).toLocalDateTime(); }
                catch (Exception e) { compra = LocalDateTime.parse(data); }
            } else if (!valor(raiz,"dEmi").isBlank()) compra = LocalDate.parse(valor(raiz,"dEmi")).atStartOfDay();
            return new Documento(itens,"Itens extraídos do XML da nota fiscal.",compra,emit == null ? "" : valor(emit,"xNome"));
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Não foi possível ler o XML. Envie o arquivo original da NF-e/NFC-e."); }
    }

    public Documento html(String html) {
        var doc = Jsoup.parse(html);
        doc.select("script,style,noscript").remove();
        List<Item> itens = new ArrayList<>();
        for (var row : doc.select("tr:has(.txtTit)")) {
            String nome = row.select("span.txtTit").text();
            BigDecimal qtd = numero(row.select(".Rqtd, .rqtd").text());
            BigDecimal preco = numero(row.select(".RvlUnit, .rvlUnit").text());
            BigDecimal total = numero(row.select(".valor, .RvlTotal").text());
            String unidade = row.select(".RUN, .run").text().replaceFirst("(?i)^UN\\.?\\s*:\\s*", "").trim();
            if (nome.isBlank() || qtd == null || qtd.signum() <= 0 || total == null || total.signum() <= 0) continue;
            if (preco == null) preco = total.divide(qtd,4,RoundingMode.HALF_UP);
            String aviso = preco.multiply(qtd).subtract(total).abs().compareTo(new BigDecimal("0.03")) > 0
                    ? "Confira descontos e o preço unitário efetivamente pago." : "";
            itens.add(new Item(nome,"","","",qtd,unidade.isBlank() ? "UN" : unidade,preco,total,aviso));
        }
        String texto = doc.body() == null ? doc.text() : doc.body().wholeText();
        if (itens.isEmpty()) itens = textoParser.analisar(texto);
        LocalDateTime data = null;
        Matcher m = Pattern.compile("(?:Emiss[aã]o|Data\\s*(?:da compra)?)\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4})\\s*(\\d{2}:\\d{2}(?::\\d{2})?)",Pattern.CASE_INSENSITIVE).matcher(doc.text());
        if (m.find()) {
            String hora = m.group(2); if (hora.length() == 5) hora += ":00";
            try { data = LocalDateTime.parse(m.group(1)+" "+hora,DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")); } catch (Exception ignored) {}
        }
        String emitente = doc.select("#u20, #emitente, .emitente").text();
        return new Documento(itens,texto,data,emitente);
    }
    private BigDecimal numero(String texto) {
        Matcher m = Pattern.compile("\\d+(?:[.,]\\d+)*").matcher(texto);
        BigDecimal valor = null;
        while (m.find()) {
            String num = m.group();
            try { valor = new BigDecimal(num.contains(",") ? num.replace(".","").replace(",",".") : num); } catch (Exception ignored) {}
        }
        return valor;
    }
    private Element primeiro(Element raiz, String tag) {
        var nodes = raiz.getElementsByTagNameNS("*",tag);
        return nodes.getLength() == 0 ? null : (Element)nodes.item(0);
    }
    private String valor(Element raiz,String tag) {
        Element node = primeiro(raiz,tag);
        return node == null ? "" : node.getTextContent().trim();
    }
}
