package com.mercado.orcamento.service;

import com.mercado.orcamento.dto.NotaFiscalDTO.Item;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

@Component
public class NotaFiscalParser {
    private static final String NUM = "(\\d+(?:[.,]\\d{1,4})?)";
    private static final String DIN = "(\\d+(?:\\.\\d{3})*[,]\\d{2,4}|\\d+[.]\\d{2,4})";
    private static final Pattern LINHA = Pattern.compile(
            "^(.*?)\\s+" + NUM + "\\s*(UN|UND|UNID|KG|G|LT|L|ML|PC|CX)\\s*(?:[xX*]\\s*)?(?:R\\$\\s*)?"
                    + DIN + "\\s+(?:R\\$\\s*)?" + DIN + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROTULADA = Pattern.compile(
            "(?:QTD\\.?|QUANTIDADE)\\s*:?\\s*" + NUM
                    + "\\s+(?:UN\\.?\\s*:\\s*)?(UN|UND|UNID|KG|G|LT|L|ML|PC|CX)"
                    + "\\s+(?:VL\\.?\\s*UNIT\\.?|VALOR\\s*UNITARIO|UNITARIO)\\s*:?\\s*(?:R\\$\\s*)?"
                    + DIN + "\\s+(?:VL\\.?\\s*TOTAL|TOTAL)\\s*:?\\s*(?:R\\$\\s*)?" + DIN, Pattern.CASE_INSENSITIVE);
    private static final Pattern IGNORAR = Pattern.compile(
            "^(?:TOTAL\\b|SUBTOTAL\\b|DESCONTO\\b|ACRESCIMO\\b|TROCO\\b|DINHEIRO\\b|CARTAO\\b|CNPJ\\b|CPF\\b|"
                    + "CONSUMIDOR\\b|NFC.?E\\b|SAT\\b|CHAVE\\b|TRIBUTOS\\b|VALOR\\s+TOTAL\\b|QTD\\.?\\s+TOTAL\\b|"
                    + "QTDE\\.?\\s+TOTAL\\b|DOCUMENTO\\b|CODIGO\\s+DESCRICAO\\b)", Pattern.CASE_INSENSITIVE);

    public List<Item> analisar(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        List<Item> itens = new ArrayList<>();
        String pendente = null;
        for (String original : texto.split("\\R")) {
            String linha = original.trim().replaceAll("\\s+", " ");
            String normal = Normalizer.normalize(linha, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            if (linha.isBlank() || IGNORAR.matcher(normal).find()) { pendente = null; continue; }
            Matcher completa = LINHA.matcher(linha);
            if (completa.matches()) {
                String nome = limparNome(completa.group(1));
                if (nome != null) itens.add(item(nome, completa.group(2), completa.group(3), completa.group(4), completa.group(5)));
                pendente = null;
                continue;
            }
            Matcher rotulada = ROTULADA.matcher(normal);
            if (rotulada.find()) {
                String antes = linha.substring(0, rotulada.start()).trim();
                String nome = limparNome(antes.isBlank() ? pendente : antes);
                if (nome != null) itens.add(item(nome, rotulada.group(1), rotulada.group(2), rotulada.group(3), rotulada.group(4)));
                pendente = null;
                continue;
            }
            // Linha de valores após uma descrição: "2 UN X 8,49 16,98".
            Matcher valores = LINHA.matcher("ITEM " + linha);
            if (pendente != null && valores.matches() && "ITEM".equals(valores.group(1))) {
                itens.add(item(pendente, valores.group(2), valores.group(3), valores.group(4), valores.group(5)));
                pendente = null;
                continue;
            }
            if (!linha.matches(".*\\d+[.,]\\d{2,4}.*")) pendente = limparNome(linha);
            else pendente = null;
        }
        return itens;
    }

    private Item item(String nome, String qtd, String unidade, String valor, String total) {
        BigDecimal quantidade = decimal(qtd), preco = decimal(valor), pago = decimal(total);
        String aviso = preco.multiply(quantidade).subtract(pago).abs().compareTo(new BigDecimal("0.03")) > 0
                ? "Quantidade × preço difere do total. Confira descontos e os números reconhecidos." : "";
        return new Item(nome, "", "", "", quantidade, unidade.toUpperCase(Locale.ROOT), preco, pago, aviso);
    }

    private String limparNome(String nome) {
        if (nome == null) return null;
        String limpo = nome.replaceFirst("^\\d{1,4}\\s+(?:\\d{4,14}\\s+)?", "")
                .replaceAll("\\s*\\(?C[oó]d(?:igo)?\\.?\\s*:\\s*\\d+\\)?", "")
                .trim().toUpperCase(Locale.ROOT);
        return limpo.length() >= 3 && limpo.matches(".*[\\p{L}].*") ? limpo : null;
    }

    private BigDecimal decimal(String numero) {
        return new BigDecimal(numero.contains(",") ? numero.replace(".", "").replace(",", ".") : numero);
    }
}
