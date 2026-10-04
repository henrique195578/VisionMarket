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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class OrcamentoController {

    private static final Logger logger = LoggerFactory.getLogger(OrcamentoController.class);

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
            model.addAttribute("gradeValores", service.listarGradeValores());
            model.addAttribute("fotos", imagemController.listarImagens());
            
            // Verifica se a lista de compras está vazia (para uso na View)
            boolean listaVazia = itens == null || itens.stream().noneMatch(Produto::isNaListaDeCompras);
            model.addAttribute("listaVazia", listaVazia);
            long totalItensLista = itens == null ? 0 : itens.stream().filter(Produto::isNaListaDeCompras).count();
            model.addAttribute("totalItensLista", totalItensLista);
            
            // Serializa os itens para JSON para uso no JavaScript (Lógica de Melhor Preço)
            String itensJson = "[]";
            try {
                if (itens != null && !itens.isEmpty()) {
                    itensJson = objectMapper.writeValueAsString(itens);
                }
            } catch (Exception e) {
                logger.error("Erro ao serializar itens para JSON: ", e);
            }
            model.addAttribute("itensJson", itensJson);
            
            long fim = System.currentTimeMillis();
            logger.info("Tempo processamento Controller Index: {}ms", (fim - inicio));
            
            return "index";
        } catch (Exception e) {
            logger.error("Erro fatal ao carregar index: ", e);
            throw new RuntimeException(e);
        }
    }
    
    @PostMapping("/api/logout")
    @ResponseBody
    public ResponseEntity<Void> logout(@CookieValue(value = "APP_SESSION_TOKEN", required = false) String token) {
        if (token != null) {
            logger.info("Logout solicitado para token: {}", token);
            sessaoService.encerrarSessao(token);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/adicionar")
    public String adicionar(@RequestParam String nome, 
                            @RequestParam(required = false) String codigoBarras,
                            @RequestParam(required = false) String marca,
                            @RequestParam(required = false) String peso,
                            @RequestParam(required = false) Mercado mercado,
                            @RequestParam(required = false) BigDecimal precoVarejo,
                            @RequestParam(required = false) BigDecimal precoAtacado,
                            @RequestParam(required = false) BigDecimal precoCartao,
                            RedirectAttributes redirectAttributes) {
        boolean jaExiste = service.produtoJaExiste(nome, marca, peso, mercado);
        boolean temPrecoInformado = Stream.of(precoVarejo, precoAtacado, precoCartao).anyMatch(valor -> valor != null);

        if (temPrecoInformado && mercado == null) {
            redirectAttributes.addFlashAttribute("erroCadastro", "Selecione o mercado para salvar os precos informados.");
            return "redirect:/?tab=home";
        }

        service.cadastrarProdutoComPrecos(nome, codigoBarras, marca, peso, mercado, precoVarejo, precoAtacado, precoCartao);
        if (temPrecoInformado) {
            redirectAttributes.addFlashAttribute("mensagemCadastro", "Produto e precos salvos com sucesso.");
            return "redirect:/?tab=valores";
        }
        
        if (jaExiste) {
            redirectAttributes.addFlashAttribute("mensagemCadastro", "Produto ja existente. Os dados foram atualizados.");
            return "redirect:/?tab=home&status=existente";
        }
        redirectAttributes.addFlashAttribute("mensagemCadastro", "Produto salvo com sucesso.");
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
        return "redirect:/?etapa=lista"; // Mantém na aba de compras
    }

    @PostMapping("/minha-lista/adicionar")
    public String adicionarItemNaLista(@RequestParam String nome,
                                       @RequestParam(required = false) String peso,
                                       @RequestParam(required = false) String marca,
                                       @RequestParam(required = false) Integer quantidadeDesejada,
                                       RedirectAttributes redirectAttributes) {
        Produto produto = service.adicionarItemNaLista(nome, peso, marca, quantidadeDesejada);
        if (produto != null) {
            redirectAttributes.addFlashAttribute("mensagemLista",
                    "Item adicionado a sua missao de compra: " + produto.getNome());
        }
        return "redirect:/?etapa=lista";
    }

    @PostMapping("/minha-lista/atualizar")
    public String atualizarItemDaLista(@RequestParam Long idItem,
                                       @RequestParam(required = false) String peso,
                                       @RequestParam(required = false) String marca,
                                       @RequestParam(required = false) Integer quantidadeDesejada,
                                       RedirectAttributes redirectAttributes) {
        Produto produto = service.atualizarPreferenciasItemDaLista(idItem, peso, marca, quantidadeDesejada);
        if (produto != null) {
            redirectAttributes.addFlashAttribute("mensagemLista",
                    "Item atualizado na sua missao de compra: " + produto.getNome());
        }
        return "redirect:/?etapa=lista";
    }

    @PostMapping("/limparLista")
    public String limparLista() {
        service.limparListaDeCompras();
        return "redirect:/?etapa=lista";
    }

    @PostMapping("/excluirItem")
    public String excluirItem(@RequestParam Long idItem) {
        service.excluirItem(idItem);
        return "redirect:/?etapa=lista"; // Mantém na aba de compras
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

    @PostMapping("/valores/editar")
    public String editarRegistroDaGrade(@RequestParam Long idProduto,
                                        @RequestParam Mercado mercado,
                                        @RequestParam String nome,
                                        @RequestParam(required = false) String marca,
                                        @RequestParam(required = false) String peso,
                                        @RequestParam(required = false) BigDecimal precoVarejo,
                                        @RequestParam(required = false) BigDecimal precoAtacado,
                                        @RequestParam(required = false) BigDecimal precoCartao,
                                        RedirectAttributes redirectAttributes) {
        Produto produto = service.atualizarRegistroDaGrade(idProduto, mercado, nome, marca, peso, precoVarejo, precoAtacado, precoCartao);
        if (produto != null) {
            redirectAttributes.addFlashAttribute("mensagemValores", "Registro atualizado com sucesso.");
        } else {
            redirectAttributes.addFlashAttribute("erroCadastro", "Nao foi possivel localizar o registro para edicao.");
        }
        return "redirect:/?tab=valores";
    }

    @GetMapping(value = "/acesso-negado", produces = "text/html;charset=UTF-8")
    public String acessoNegado() {
        return "acesso-negado";
    }

    // Endpoints de Imagem e OCR delegados ou mantidos aqui por simplicidade (já estão no ImagemController, mas podemos centralizar se quiser)
    // Como já existem no ImagemController, não preciso duplicar aqui.
}
