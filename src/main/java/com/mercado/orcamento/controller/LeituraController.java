package com.mercado.orcamento.controller;

import com.mercado.orcamento.model.*;
import com.mercado.orcamento.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;
import java.util.List;

@Controller
public class LeituraController {
    private final LeituraService leituras;
    private final FotoService fotos;
    public LeituraController(LeituraService leituras, FotoService fotos) {
        this.leituras = leituras;
        this.fotos = fotos;
    }

    @GetMapping("/api/produtos/{id}/historico")
    @ResponseBody
    public ResponseEntity<List<RegistroPreco>> historico(@PathVariable Long id) {
        try { return ResponseEntity.ok(leituras.historico(id)); }
        catch (IllegalArgumentException e) { return ResponseEntity.notFound().build(); }
    }

    @PostMapping("/leituras")
    public String registrar(@RequestParam Long idProduto, @RequestParam Mercado mercado,
            @RequestParam String unidade, @RequestParam(required=false) BigDecimal precoVarejo,
            @RequestParam(required=false) BigDecimal precoAtacado, @RequestParam(required=false) BigDecimal precoCartao,
            @RequestParam(required=false) Integer quantidadeMinima, @RequestParam(required=false) String condicoes,
            @RequestParam(required=false) String arquivo, @RequestParam(required=false) String codigoBarras,
            RedirectAttributes redirect) {
        try {
            if (arquivo != null && !arquivo.isBlank() && !fotos.existe(arquivo))
                throw new IllegalArgumentException("A foto não foi encontrada. Fotografe novamente antes de salvar.");
            leituras.registrar(idProduto, mercado, unidade, precoVarejo, precoAtacado, precoCartao,
                    quantidadeMinima, condicoes, arquivo, codigoBarras);
            redirect.addFlashAttribute("mensagem", "Leitura salva. O último preço e a data desta filial foram atualizados; o histórico foi preservado.");
            return "redirect:/?etapa=comparar&produto=" + idProduto;
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            redirect.addFlashAttribute("leituraRascunho", new Rascunho(idProduto, mercado.name(), unidade,
                    precoVarejo, precoAtacado, precoCartao, quantidadeMinima, condicoes, arquivo, codigoBarras));
            return "redirect:/?etapa=foto&produto=" + idProduto;
        }
    }

    public record Rascunho(Long idProduto, String mercado, String unidade, BigDecimal precoVarejo,
            BigDecimal precoAtacado, BigDecimal precoCartao, Integer quantidadeMinima,
            String condicoes, String arquivo, String codigoBarras) {}
}
