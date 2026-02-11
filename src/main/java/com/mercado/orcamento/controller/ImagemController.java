package com.mercado.orcamento.controller;

import com.mercado.orcamento.dto.DadosExtraidos;
import com.mercado.orcamento.service.OcrService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class ImagemController {

    private final String CAMINHO_FOTOS = "C:\\Users\\Borges\\Downloads\\MERCADO_FOTOS";
    private final OcrService ocrService;

    public ImagemController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    // Retorna a lista de nomes de arquivos de imagem
    public List<String> listarImagens() {
        try (Stream<Path> paths = Files.walk(Paths.get(CAMINHO_FOTOS))) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(nome -> nome.toLowerCase().endsWith(".jpg") || nome.toLowerCase().endsWith(".jpeg") || nome.toLowerCase().endsWith(".png"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // Serve a imagem para o navegador conseguir mostrar
    @GetMapping("/imagens/{nomeArquivo:.+}")
    @ResponseBody
    public ResponseEntity<Resource> servirImagem(@PathVariable String nomeArquivo) {
        try {
            Path arquivoPath = Paths.get(CAMINHO_FOTOS).resolve(nomeArquivo);
            Resource resource = new UrlResource(arquivoPath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG) // Assumindo JPEG, o navegador se vira com outros
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // NOVO: Endpoint para extrair dados da imagem via OCR
    @GetMapping("/ocr/{nomeArquivo:.+}")
    @ResponseBody
    public ResponseEntity<DadosExtraidos> extrairDados(@PathVariable String nomeArquivo) {
        DadosExtraidos dados = ocrService.extrairDadosDaImagem(nomeArquivo);
        return ResponseEntity.ok(dados);
    }

    // NOVO: Endpoint para Upload de Imagens
    @PostMapping("/upload")
    public String uploadImagem(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("mensagem", "Por favor, selecione uma imagem.");
            return "redirect:/";
        }

        try {
            // Garante que o diretório existe
            Path diretorio = Paths.get(CAMINHO_FOTOS);
            if (!Files.exists(diretorio)) {
                Files.createDirectories(diretorio);
            }

            // Salva o arquivo
            byte[] bytes = file.getBytes();
            Path path = diretorio.resolve(file.getOriginalFilename());
            Files.write(path, bytes);

            redirectAttributes.addFlashAttribute("mensagem", "Upload realizado com sucesso: " + file.getOriginalFilename());
            // Passa o nome do arquivo para que a tela já possa sugerir o scan
            redirectAttributes.addFlashAttribute("arquivoRecemCarregado", file.getOriginalFilename());

        } catch (IOException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("erro", "Erro ao fazer upload: " + e.getMessage());
        }

        return "redirect:/";
    }
}
