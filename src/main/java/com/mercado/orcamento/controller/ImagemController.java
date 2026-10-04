package com.mercado.orcamento.controller;

import com.mercado.orcamento.dto.DadosExtraidos;
import com.mercado.orcamento.service.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.util.*;

@Controller
public class ImagemController {
    private final FotoService fotos;
    private final OcrService ocr;
    public ImagemController(FotoService fotos, OcrService ocr) { this.fotos = fotos; this.ocr = ocr; }
    public List<String> listarImagens() { return fotos.listar(); }

    @GetMapping("/imagens/{nome:.+}")
    @ResponseBody
    public ResponseEntity<Resource> servirImagem(@PathVariable String nome) throws IOException {
        try {
            if (!fotos.existe(nome)) return ResponseEntity.notFound().build();
            MediaType tipo = nome.endsWith(".xml") ? MediaType.APPLICATION_XML
                    : nome.endsWith(".txt") ? new MediaType("text","plain",java.nio.charset.StandardCharsets.UTF_8)
                    : nome.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().contentType(tipo).header("X-Content-Type-Options","nosniff")
                    .body(new UrlResource(fotos.resolver(nome).toUri()));
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().build(); }
    }

    @GetMapping("/ocr/{nome:.+}")
    @ResponseBody
    public ResponseEntity<DadosExtraidos> extrairDados(@PathVariable String nome) {
        try {
            if (!fotos.existe(nome)) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(ocr.extrairDadosDaImagem(nome));
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().build(); }
    }

    @PostMapping("/api/fotos")
    @ResponseBody
    public ResponseEntity<?> fotografar(@RequestParam("file") MultipartFile file) {
        try {
            String nome = fotos.salvar(file);
            return ResponseEntity.ok(ocr.extrairDadosDaImagem(nome));
        } catch (IOException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public String uploadImagem(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        try {
            String nome = fotos.salvar(file);
            redirect.addFlashAttribute("arquivoRecemCarregado", nome);
        } catch (IOException | IllegalArgumentException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/?etapa=foto";
    }
}
