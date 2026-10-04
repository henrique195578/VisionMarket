package com.mercado.orcamento.controller;

import com.mercado.orcamento.dto.NotaFiscalDTO.*;
import com.mercado.orcamento.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import javax.imageio.ImageIO;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

@RestController
@RequestMapping("/api/notas")
public class NotaFiscalController {
    private final NotaArquivoService arquivos;
    private final NotaLinkService links;
    private final NotaFiscalService notas;
    public NotaFiscalController(NotaArquivoService arquivos, NotaLinkService links, NotaFiscalService notas) {
        this.arquivos=arquivos; this.links=links; this.notas=notas;
    }
    @PostMapping("/ler")
    public ResponseEntity<?> ler(@RequestParam("file") MultipartFile file) {
        try { return ResponseEntity.ok(arquivos.ler(file)); }
        catch (java.io.IOException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro",e.getMessage()));
        }
    }
    public record LinkPedido(String url) {}
    @PostMapping("/link")
    public ResponseEntity<?> link(@RequestBody LinkPedido pedido) {
        try { return ResponseEntity.ok(links.consultar(pedido.url())); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("erro",e.getMessage())); }
    }
    @PostMapping("/qr")
    public ResponseEntity<?> qr(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize()>3*1024*1024)
            return ResponseEntity.badRequest().body(Map.of("erro","Envie uma imagem do QR de até 3 MB."));
        try (var stream = file.getInputStream()) {
            var imagem=ImageIO.read(stream);
            if (imagem==null) return ResponseEntity.badRequest().body(Map.of("erro","Imagem do QR inválida."));
            var bitmap=new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagem)));
            Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER,true);
            hints.put(DecodeHintType.POSSIBLE_FORMATS,List.of(BarcodeFormat.QR_CODE));
            var resultado=new MultiFormatReader().decode(bitmap,hints);
            return ResponseEntity.ok(Map.of("codigoQr",resultado.getText()));
        } catch (NotFoundException e) { return ResponseEntity.noContent().build(); }
        catch (java.io.IOException e) {
            return ResponseEntity.badRequest().body(Map.of("erro","Não foi possível ler a imagem do QR."));
        }
    }
    @PostMapping("/importar")
    public ResponseEntity<?> importar(@RequestBody Importacao pedido) {
        try { return ResponseEntity.ok(notas.importar(pedido)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("erro",e.getMessage())); }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of("erro","Esta nota ou produto já foi registrado. Confira os dados antes de importar novamente."));
        }
    }
}
