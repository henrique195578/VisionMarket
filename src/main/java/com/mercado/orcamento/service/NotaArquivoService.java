package com.mercado.orcamento.service;

import com.mercado.orcamento.dto.NotaFiscalDTO.Leitura;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Locale;

@Service
public class NotaArquivoService {
    private final FotoService fotos;
    private final OcrService ocr;
    private final NotaFiscalParser parser;
    private final NotaDocumentoParser documentos;
    private final NotaLinkService links;
    public NotaArquivoService(FotoService fotos,OcrService ocr,NotaFiscalParser parser,NotaDocumentoParser documentos,NotaLinkService links) {
        this.fotos=fotos;this.ocr=ocr;this.parser=parser;this.documentos=documentos;this.links=links;
    }
    public Leitura ler(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize()>15*1024*1024) throw new IllegalArgumentException("Escolha um arquivo de até 15 MB.");
        String nome = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (nome.endsWith(".xml") || "application/xml".equals(file.getContentType()) || "text/xml".equals(file.getContentType())) {
            byte[] bytes = file.getBytes();
            var doc = documentos.xml(bytes);
            String arquivo = fotos.salvarDocumento(bytes,".xml");
            return new Leitura(arquivo,doc.texto(),"",doc.itens(),"XML lido. Confira os itens antes de importar.",doc.dataCompra(),doc.emitente(),"");
        }
        String arquivo=fotos.salvar(file);
        var dados=ocr.extrairDadosDaImagem(arquivo);
        String texto=dados.getTextoBruto()==null?"":dados.getTextoBruto();
        var itens=parser.analisar(texto);
        String qr=dados.getCodigoBarras()!=null && dados.getCodigoBarras().startsWith("http") ? dados.getCodigoBarras() : "";
        if (itens.isEmpty() && !qr.isBlank()) {
            try { return links.consultar(qr); } catch (IllegalArgumentException ignored) {}
        }
        return new Leitura(arquivo,texto,qr,itens,itens.isEmpty()
                ? "Não foi possível separar os itens. Confira a imagem, consulte o link do QR ou adicione os itens manualmente."
                : "Confira os itens reconhecidos antes de importar.");
    }
}
