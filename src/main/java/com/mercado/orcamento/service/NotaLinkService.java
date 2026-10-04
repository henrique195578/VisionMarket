package com.mercado.orcamento.service;

import com.mercado.orcamento.dto.NotaFiscalDTO.Leitura;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;

@Service
public class NotaLinkService {
    private static final Set<String> HOSTS = Set.of("www.nfce.fazenda.sp.gov.br","nfce.fazenda.sp.gov.br",
            "sat.fazenda.sp.gov.br","satsp.fazenda.sp.gov.br","www.nfp.fazenda.sp.gov.br","nfp.fazenda.sp.gov.br",
            "www.fazenda.sp.gov.br","fazenda.sp.gov.br");
    private final NotaDocumentoParser parser;
    private final FotoService fotos;
    private final HttpClient cliente;

    public NotaLinkService(NotaDocumentoParser parser, FotoService fotos) {
        this.parser = parser; this.fotos = fotos;
        this.cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();
    }

    public URI validarLink(String link) {
        if (link == null || link.isBlank() || link.length() > 4096)
            throw new IllegalArgumentException("Cole o link de consulta da nota fiscal paulista.");
        try {
            URI uri = URI.create(link.trim().replace("|","%7C"));
            String host = uri.getHost();
            if (host == null || !HOSTS.contains(host.toLowerCase(Locale.ROOT)) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80)
                    || !Set.of("http","https").contains(uri.getScheme().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Use um link de consulta oficial da Fazenda de São Paulo, como o endereço contido no QR da nota.");
            if ("http".equalsIgnoreCase(uri.getScheme()))
                uri = URI.create("https://" + host + (uri.getRawPath() == null ? "" : uri.getRawPath())
                        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
            return uri;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("Use um link")) throw e;
            throw new IllegalArgumentException("O link informado é inválido. Copie o endereço completo de consulta da nota.");
        }
    }

    public Leitura consultar(String link) {
        URI inicial = validarLink(link), atual = inicial;
        try {
            long inicio = System.nanoTime();
            for (int i=0; i<5; i++) {
                if (Duration.ofNanos(System.nanoTime()-inicio).toSeconds() > 40)
                    throw new IllegalArgumentException("O portal demorou para responder. Tente novamente ou envie o arquivo da nota.");
                validarLink(atual.toString());
                var request = HttpRequest.newBuilder(atual).timeout(Duration.ofSeconds(15))
                        .header("User-Agent","VisionMarket/1.0").header("Accept","text/html,application/xml").GET().build();
                var resposta = cliente.send(request,HttpResponse.BodyHandlers.ofInputStream());
                try (var body = resposta.body()) {
                    if (Set.of(301,302,303,307,308).contains(resposta.statusCode())) {
                        String local = resposta.headers().firstValue("Location").orElseThrow(() -> new IllegalArgumentException("O portal redirecionou sem informar a nota."));
                        atual = validarLink(atual.resolve(local.replace("|","%7C")).toString());
                        continue;
                    }
                    if (resposta.statusCode() != 200)
                        throw new IllegalArgumentException("O portal fiscal não entregou a nota. Abra o link para conferir ou envie imagem/XML.");
                    byte[] bytes = body.readNBytes(4*1024*1024+1);
                    if (bytes.length > 4*1024*1024) throw new IllegalArgumentException("A página da nota excede o limite de leitura.");
                    String tipo = resposta.headers().firstValue("Content-Type").orElse("");
                    Charset charset = tipo.toLowerCase(Locale.ROOT).contains("iso-8859-1") ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;
                    String html = new String(bytes,charset);
                    boolean xml = tipo.contains("xml") || html.stripLeading().startsWith("<?xml");
                    var documento = xml ? parser.xml(bytes) : parser.html(html);
                    String arquivo = fotos.salvarDocumento(xml ? bytes : documento.texto().getBytes(StandardCharsets.UTF_8),xml ? ".xml" : ".txt");
                    String aviso = documento.itens().isEmpty()
                            ? "O portal não disponibilizou os itens para leitura automática. Se pedir CAPTCHA/login, abra a consulta e envie a nota em imagem ou XML."
                            : "Itens consultados pelo link. Confira preços, descontos, mercado e data antes de salvar.";
                    return new Leitura(arquivo,documento.texto(),inicial.toString(),documento.itens(),aviso,
                            documento.dataCompra(),documento.emitente(),inicial.toString());
                }
            }
            throw new IllegalArgumentException("O link redirecionou muitas vezes. Abra a consulta ou envie o arquivo da nota.");
        } catch (IllegalArgumentException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("A consulta foi interrompida. Tente novamente.");
        } catch (Exception e) {
            throw new IllegalArgumentException("Não foi possível acessar o portal fiscal agora. Abra o link ou envie o arquivo da nota.");
        }
    }
}
