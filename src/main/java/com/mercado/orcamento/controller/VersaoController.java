package com.mercado.orcamento.controller;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@ControllerAdvice
public class VersaoController {
    private final String versao, build;
    public VersaoController(ObjectProvider<BuildProperties> provider) {
        BuildProperties p = provider.getIfAvailable();
        versao = p == null ? "desenvolvimento" : p.getVersion();
        build = p == null || p.getTime() == null ? UUID.randomUUID().toString() : versao + "-" + p.getTime();
    }
    @ModelAttribute("appBuild")
    public String appBuild() { return build; }
    @GetMapping("/api/versao")
    public ResponseEntity<Map<String,String>> consultar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("versao",versao,"build",build));
    }
}
