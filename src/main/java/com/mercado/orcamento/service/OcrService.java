package com.mercado.orcamento.service;

import com.mercado.orcamento.dto.DadosExtraidos;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private final String CAMINHO_FOTOS = "C:\\Users\\Borges\\Downloads\\MERCADO_FOTOS";

    public DadosExtraidos extrairDadosDaImagem(String nomeArquivo) {
        File imagem = new File(CAMINHO_FOTOS, nomeArquivo);
        DadosExtraidos dados = new DadosExtraidos();

        if (!imagem.exists()) {
            dados.setTextoBruto("Erro: Arquivo não encontrado.");
            return dados;
        }

        // 1. Tenta ler Código de Barras (Prioridade para Identificação)
        try {
            String codigo = lerCodigoBarras(imagem);
            if (codigo != null) {
                dados.setCodigoBarras(codigo);
            }
        } catch (Exception e) {
            System.err.println("Erro ao ler código de barras: " + e.getMessage());
        }

        // 2. Tenta ler Texto (OCR)
        ITesseract instance = new Tesseract();
        // Configura o caminho dos dados de treinamento (tessdata). 
        instance.setDatapath("d:\\PROJETO_AUTOMACAO_TRAE\\MERCADO_ORCAMENTOS\\tessdata");
        instance.setLanguage("eng"); // Começamos com inglês que lê bem números

        try {
            String resultado = instance.doOCR(imagem);
            dados.setTextoBruto(resultado);

            // 3. Processa o texto para achar padrões (Preço, Peso, Nome)
            processarTexto(resultado, dados);

        } catch (TesseractException e) {
            dados.setTextoBruto("Erro ao processar imagem: " + e.getMessage());
            e.printStackTrace();
        }

        return dados;
    }

    private String lerCodigoBarras(File arquivoImagem) throws IOException, NotFoundException {
        BufferedImage imagem = ImageIO.read(arquivoImagem);
        if (imagem == null) return null;

        LuminanceSource source = new BufferedImageLuminanceSource(imagem);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.allOf(BarcodeFormat.class));

        try {
            Result result = new MultiFormatReader().decode(bitmap, hints);
            return result.getText();
        } catch (NotFoundException e) {
            return null; // Nenhum código encontrado
        }
    }

    private void processarTexto(String texto, DadosExtraidos dados) {
        // Limpeza básica
        String limpo = texto.replaceAll("\n", " ").trim();

        // Tenta achar preço (ex: 10,90 ou 10.90 ou R$ 10,90)
        // Regex procura por números com vírgula ou ponto, possivelmente precedidos de R$
        Pattern padraoPreco = Pattern.compile("(?:R\\$\\s*)?(\\d+[.,]\\d{2})");
        Matcher matcherPreco = padraoPreco.matcher(limpo);
        if (matcherPreco.find()) {
            // Normaliza para ponto (formato que o Java entende no input html)
            dados.setPrecoEncontrado(matcherPreco.group(1).replace(",", "."));
        }

        // Tenta achar peso (ex: 500g, 1kg, 1.5kg, 2 Litros)
        // Melhorado para aceitar espaços e variações de unidade
        Pattern padraoPeso = Pattern.compile("(\\d+(?:[.,]\\d+)?\\s*(?:kg|g|ml|l|litros?))", Pattern.CASE_INSENSITIVE);
        Matcher matcherPeso = padraoPeso.matcher(limpo);
        if (matcherPeso.find()) {
            dados.setPesoEncontrado(matcherPeso.group(1));
        }
        
        // Tenta chutar o nome (pega as primeiras palavras que não são preço/peso)
        // Isso é difícil sem IA avançada, mas vamos pegar a primeira linha válida
        String[] linhas = texto.split("\n");
        for (String linha : linhas) {
            linha = linha.trim();
            // Ignora linhas muito curtas ou que parecem só números/preços
            if (linha.length() > 3 && !linha.matches(".*R\\$\\s*\\d.*") && !linha.matches("^\\d+[.,]?\\d*$")) {
                dados.setNomePossivel(linha);
                break;
            }
        }
    }
}
