package com.mercado.orcamento.service;

import com.mercado.orcamento.dto.RegistroPrecoDTO;
import com.mercado.orcamento.model.Mercado;
import com.mercado.orcamento.model.Produto;
import com.mercado.orcamento.model.RegistroPreco;
import com.mercado.orcamento.model.TipoPreco;
import com.mercado.orcamento.repository.ProdutoRepository;
import com.mercado.orcamento.repository.RegistroPrecoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class OrcamentoService {

    private final ProdutoRepository produtoRepository;
    private final RegistroPrecoRepository registroPrecoRepository;

    public OrcamentoService(ProdutoRepository produtoRepository, RegistroPrecoRepository registroPrecoRepository) {
        this.produtoRepository = produtoRepository;
        this.registroPrecoRepository = registroPrecoRepository;
    }

    public List<Produto> listarItens() {
        return produtoRepository.findAll(org.springframework.data.domain.Sort.by("nome"));
    }

    @Transactional
    public void limparListaDeCompras() {
        List<Produto> todos = produtoRepository.findAll();
        for (Produto p : todos) {
            p.setNaListaDeCompras(false);
        }
        produtoRepository.saveAll(todos);
    }

    @Transactional
    public void importarListaRapida(String textoLista) {
        if (textoLista == null || textoLista.trim().isEmpty()) return;

        // 1. Separa por vírgula ou múltiplas quebras de linha (robustez para inputs sujos)
        String[] itens = textoLista.split("[,\\r\\n]+");

        for (String itemStr : itens) {
            String nomeLimpo = itemStr.trim().toUpperCase(); // 2. Converte para Maiúsculo
            if (nomeLimpo.isEmpty()) continue;

            // 3. Busca Inteligente (Exata ou Fonética/Sem Acento)
            Optional<Produto> existente = buscarProdutoInteligente(nomeLimpo);
            
            if (existente.isPresent()) {
                Produto p = existente.get();
                p.setNaListaDeCompras(true); // Marca na lista
                produtoRepository.save(p);
            } else {
                Produto novo = new Produto(nomeLimpo, null);
                novo.setNaListaDeCompras(true); // Já nasce na lista
                produtoRepository.save(novo);
            }
        }
    }

    public List<RegistroPrecoDTO> listarPrecosPlanos() {
        List<RegistroPrecoDTO> tabelaPlana = new ArrayList<>();
        List<Produto> produtos = produtoRepository.findAll();

        for (Produto produto : produtos) {
            // Busca histórico do produto
            List<RegistroPreco> historico = registroPrecoRepository.findByProduto(produto);
            
            // Calcula estatísticas básicas
            BigDecimal menorPreco = registroPrecoRepository.findMenorPrecoByProduto(produto);
            
            for (RegistroPreco rp : historico) {
                boolean ehMelhor = menorPreco != null && rp.getValor().compareTo(menorPreco) == 0;
                
                tabelaPlana.add(new RegistroPrecoDTO(
                    rp.getMercado().getNomeExibicao(),
                    produto.getNome(),
                    rp.getValor(),
                    rp.getTipoPreco().getDescricao(),
                    ehMelhor
                ));
            }
        }
        return tabelaPlana;
    }

    public Produto buscarPorCodigoBarras(String codigoBarras) {
        return produtoRepository.findByCodigoBarras(codigoBarras).orElse(null);
    }

    @Transactional
    public Produto adicionarItem(String nome, String codigoBarras, String marca, String peso) {
        String nomeUpper = nome != null ? nome.trim().toUpperCase() : null;
        
        // Verifica duplicidade por código de barras
        if (codigoBarras != null && !codigoBarras.isEmpty()) {
            Optional<Produto> existente = produtoRepository.findByCodigoBarras(codigoBarras);
            if (existente.isPresent()) {
                return existente.get(); 
            }
        }

        // Verifica duplicidade por nome (Busca Inteligente)
        Optional<Produto> existenteNome = buscarProdutoInteligente(nomeUpper);
        
        if (existenteNome.isPresent()) {
             Produto p = existenteNome.get();
             // Atualiza dados se vieram novos e o produto existente tem campos vazios
             if (marca != null && !marca.isEmpty()) p.setMarca(marca);
             if (peso != null && !peso.isEmpty()) p.setPeso(peso);
             if (codigoBarras != null && !codigoBarras.isEmpty() && (p.getCodigoBarras() == null || p.getCodigoBarras().isEmpty())) {
                 p.setCodigoBarras(codigoBarras);
             }
             // Se o usuário está adicionando explicitamente, talvez queira por na lista?
             // Mantendo lógica original: retorna o existente atualizado
             return produtoRepository.save(p);
        }

        Produto novo = new Produto(nomeUpper, codigoBarras);
        novo.setMarca(marca);
        novo.setPeso(peso);
        novo.setNaListaDeCompras(false); // Default: Fora da lista
        return produtoRepository.save(novo);
    }
    
    @Transactional
    public void atualizarStatusListaCompras(Long idProduto, boolean naLista) {
        produtoRepository.findById(idProduto).ifPresent(p -> {
            p.setNaListaDeCompras(naLista);
            produtoRepository.save(p);
        });
    }

    @Transactional
    public void atualizarPreco(Long idProduto, Mercado mercado, TipoPreco tipo, BigDecimal valor) {
        produtoRepository.findById(idProduto).ifPresent(produto -> {
            RegistroPreco novoRegistro = new RegistroPreco(produto, mercado, valor, tipo);
            registroPrecoRepository.save(novoRegistro);
        });
    }

    @Transactional
    public void excluirItem(Long idProduto) {
        produtoRepository.deleteById(idProduto);
    }

    // --- Lógica de Inteligência de Mercado ---

    public BigDecimal getMediaPreco(Produto p) {
        BigDecimal media = registroPrecoRepository.findMediaPrecoByProduto(p);
        return media != null ? media.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public BigDecimal getMenorPreco(Produto p) {
        BigDecimal min = registroPrecoRepository.findMenorPrecoByProduto(p);
        return min != null ? min : BigDecimal.ZERO;
    }
    
    // Método auxiliar para compatibilidade ou uso rápido
    public void adicionarItem(String nome) {
        adicionarItem(nome, null, null, null);
    }

    public boolean produtoJaExiste(String nome) {
        return buscarProdutoInteligente(nome).isPresent();
    }

    /**
     * Busca um produto tentando encontrar correspondência exata ou aproximada (sem acentos).
     * Ex: Se existe "AÇÚCAR", encontra buscando por "ACUCAR" ou "AÇUCAR".
     */
    private Optional<Produto> buscarProdutoInteligente(String nomeBusca) {
        if (nomeBusca == null || nomeBusca.isEmpty()) return Optional.empty();

        // 1. Tentativa Direta (Banco)
        Optional<Produto> direto = produtoRepository.findByNomeContainingIgnoreCase(nomeBusca);
        if (direto.isPresent()) return direto;

        // 2. Tentativa "Normalizada" (Memória)
        // Normaliza o termo de busca (remove acentos)
        String buscaNormalizada = removerAcentos(nomeBusca);

        // Carrega todos (assumindo base pequena < 2000 itens) para filtrar
        // Otimização futura: Criar coluna "nome_normalizado" no banco e indexar
        List<Produto> todos = produtoRepository.findAll();
        
        return todos.stream()
            .filter(p -> {
                String nomeP = removerAcentos(p.getNome() != null ? p.getNome().toUpperCase() : "");
                return nomeP.contains(buscaNormalizada) || buscaNormalizada.contains(nomeP);
            })
            .findFirst();
    }

    private String removerAcentos(String str) {
        if (str == null) return "";
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD); 
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }
}
