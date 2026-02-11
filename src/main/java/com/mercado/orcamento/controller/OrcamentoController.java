package com.mercado.orcamento.controller;

import com.mercado.orcamento.model.Mercado;
import com.mercado.orcamento.model.Produto;
import com.mercado.orcamento.model.TipoPreco;
import com.mercado.orcamento.service.OcrService;
import com.mercado.orcamento.service.OrcamentoService;
import com.mercado.orcamento.service.SessaoService;
import com.mercado.orcamento.dto.DadosExtraidos;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.CookieValue;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class OrcamentoController {

    private final OrcamentoService service;
    private final ImagemController imagemController;
    private final OcrService ocrService;
    private final ObjectMapper objectMapper;
    private final SessaoService sessaoService;

    public OrcamentoController(OrcamentoService service, ImagemController imagemController, OcrService ocrService, ObjectMapper objectMapper, SessaoService sessaoService) {
        this.service = service;
        this.imagemController = imagemController;
        this.ocrService = ocrService;
        this.objectMapper = objectMapper;
        this.sessaoService = sessaoService;
    }

    @GetMapping("/")
    public String index(Model model) {
        try {
            long inicio = System.currentTimeMillis();
            
            List<Produto> itens = service.listarItens();
            model.addAttribute("itens", itens);
            
            model.addAttribute("mercados", Mercado.values());
            model.addAttribute("tiposPreco", TipoPreco.values());
            
            model.addAttribute("tabelaPrecos", service.listarPrecosPlanos());
            model.addAttribute("fotos", imagemController.listarImagens());
            
            // Verifica se a lista de compras está vazia (para uso na View)
            boolean listaVazia = itens == null || itens.stream().noneMatch(Produto::isNaListaDeCompras);
            model.addAttribute("listaVazia", listaVazia);
            
            // Serializa os itens para JSON para uso no JavaScript (Lógica de Melhor Preço)
            String itensJson = "[]";
            try {
                if (itens != null && !itens.isEmpty()) {
                    itensJson = objectMapper.writeValueAsString(itens);
                }
            } catch (Exception e) {
                System.err.println("Erro ao serializar itens para JSON: " + e.getMessage());
                e.printStackTrace();
            }
            model.addAttribute("itensJson", itensJson);
            
            long fim = System.currentTimeMillis();
            System.out.println("Tempo processamento Controller Index: " + (fim - inicio) + "ms");
            
            return "index";
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    @PostMapping("/api/logout")
    @ResponseBody
    public ResponseEntity<Void> logout(@CookieValue(value = "auth_token", required = false) String token) {
        if (token != null) {
            System.out.println("Logout solicitado para token: " + token);
            sessaoService.encerrarSessao(token);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/adicionar")
    public String adicionar(@RequestParam String nome, 
                            @RequestParam(required = false) String codigoBarras,
                            @RequestParam(required = false) String marca,
                            @RequestParam(required = false) String peso) {
        boolean jaExiste = service.produtoJaExiste(nome);
        service.adicionarItem(nome, codigoBarras, marca, peso);
        
        if (jaExiste) {
            return "redirect:/?tab=home&status=existente";
        }
        return "redirect:/?tab=home&status=sucesso";
    }
    
    @PostMapping("/atualizarListaCompras")
    @ResponseBody
    public ResponseEntity<Void> atualizarListaCompras(@RequestParam Long idItem, @RequestParam boolean naLista) {
        service.atualizarStatusListaCompras(idItem, naLista);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/importarLista")
    public String importarLista(@RequestParam String listaRapida) {
        service.importarListaRapida(listaRapida);
        return "redirect:/?tab=comprar"; // Mantém na aba de compras
    }

    @PostMapping("/limparLista")
    public String limparLista() {
        service.limparListaDeCompras();
        return "redirect:/?tab=comprar";
    }

    @PostMapping("/excluirItem")
    public String excluirItem(@RequestParam Long idItem) {
        service.excluirItem(idItem);
        return "redirect:/?tab=comprar"; // Mantém na aba de compras
    }

    @PostMapping("/preco")
    public String definirPreco(@RequestParam Long idItem, 
                               @RequestParam Mercado mercado,
                               @RequestParam(required = false) BigDecimal precoVarejo,
                               @RequestParam(required = false) BigDecimal precoAtacado,
                               @RequestParam(required = false) BigDecimal precoCartao) {
        
        if (precoVarejo != null) {
            service.atualizarPreco(idItem, mercado, TipoPreco.VAREJO, precoVarejo);
        }
        if (precoAtacado != null) {
            service.atualizarPreco(idItem, mercado, TipoPreco.ATACADO, precoAtacado);
        }
        if (precoCartao != null) {
            service.atualizarPreco(idItem, mercado, TipoPreco.CARTAO, precoCartao);
        }
        
        return "redirect:/?tab=home";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado() {
        return "acesso-negado";
    }

    // Endpoints de Imagem e OCR delegados ou mantidos aqui por simplicidade (já estão no ImagemController, mas podemos centralizar se quiser)
    // Como já existem no ImagemController, não preciso duplicar aqui.
}
