package com.mercado.orcamento.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class FotoService {
    private final Path diretorio;
    public FotoService(@Value("${visionmarket.fotos.path:./data/fotos}") String caminho) {
        diretorio = Paths.get(caminho).toAbsolutePath().normalize();
    }
    public Path resolver(String nome) {
        if (nome == null || nome.isBlank() || nome.contains("/") || nome.contains("\\"))
            throw new IllegalArgumentException("Nome de foto inválido.");
        Path arquivo = diretorio.resolve(nome).normalize();
        if (!arquivo.startsWith(diretorio)) throw new IllegalArgumentException("Nome de foto inválido.");
        return arquivo;
    }
    public boolean existe(String nome) {
        return Files.isRegularFile(resolver(nome));
    }
    public String salvar(MultipartFile foto) throws IOException {
        if (foto.isEmpty() || foto.getSize() > 15 * 1024 * 1024)
            throw new IllegalArgumentException("Selecione uma foto de até 15 MB.");
        byte[] bytes = foto.getBytes();
        var imagem = ImageIO.read(new ByteArrayInputStream(bytes));
        if (imagem == null) throw new IllegalArgumentException("Envie uma foto JPEG ou PNG legível.");
        Files.createDirectories(diretorio);
        String nome = UUID.randomUUID() + ".jpg";
        var rgb = new java.awt.image.BufferedImage(imagem.getWidth(), imagem.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(imagem, 0, 0, null);
        g.dispose();
        ImageIO.write(rgb, "jpg", resolver(nome).toFile());
        return nome;
    }
    public String salvarDocumento(byte[] bytes, String extensao) throws IOException {
        if (!java.util.Set.of(".xml", ".txt").contains(extensao) || bytes.length == 0 || bytes.length > 15*1024*1024)
            throw new IllegalArgumentException("Documento da nota inválido ou acima de 15 MB.");
        Files.createDirectories(diretorio);
        String nome = UUID.randomUUID() + extensao;
        Files.write(resolver(nome), bytes);
        return nome;
    }
    public List<String> listar() {
        if (!Files.isDirectory(diretorio)) return List.of();
        try (var arquivos = Files.list(diretorio)) {
            return arquivos.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".jpeg")).sorted().toList();
        } catch (IOException e) { return List.of(); }
    }
}
