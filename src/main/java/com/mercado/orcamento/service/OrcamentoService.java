package com.mercado.orcamento.service;

import com.mercado.orcamento.dto.RegistroPrecoDTO;
import com.mercado.orcamento.dto.GradeValorDTO;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Pattern;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrcamentoService {
    private static final DateTimeFormatter FORMATADOR_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yy - HH:mm");

    private final ProdutoRepository produtoRepository;
    private final RegistroPrecoRepository registroPrecoRepository;

    public OrcamentoService(ProdutoRepository produtoRepository, RegistroPrecoRepository registroPrecoRepository) {
        this.produtoRepository = produtoRepository;
        this.registroPrecoRepository = registroPrecoRepository;
    }

    public List<Produto> listarItens() {
        return produtoRepository.findAllWithPrecos();
    }

    public List<GradeValorDTO> listarGradeValores() {
        List<Produto> produtos = listarItens();
        List<GradeValorDTO> grade = new ArrayList<>();

        for (Produto produto : produtos) {
            if (produto.getHistoricoPrecos() == null || produto.getHistoricoPrecos().isEmpty()) {
                continue;
            }

            Map<Mercado, List<RegistroPreco>> registrosPorMercado = produto.getHistoricoPrecos().stream()
                    .filter(registro -> registro.getMercado() != null)
                    .collect(Collectors.groupingBy(RegistroPreco::getMercado));

            for (Map.Entry<Mercado, List<RegistroPreco>> entry : registrosPorMercado.entrySet()) {
                BigDecimal precoVarejo = obterPrecoMaisRecente(entry.getValue(), TipoPreco.VAREJO);
                BigDecimal precoAtacado = obterPrecoMaisRecente(entry.getValue(), TipoPreco.ATACADO);
                BigDecimal precoCartao = obterPrecoMaisRecente(entry.getValue(), TipoPreco.CARTAO);

                if (precoVarejo == null && precoAtacado == null && precoCartao == null) {
                    continue;
                }

                grade.add(new GradeValorDTO(
                        produto.getId(),
                        produto.getNome(),
                        produto.getMarca(),
                        produto.getPeso(),
                        entry.getKey().name(),
                        entry.getKey().getNomeExibicao(),
                        formatarDataHoraCadastro(entry.getValue()),
                        precoVarejo,
                        precoAtacado,
                        precoCartao
                ));
            }
        }

        return grade;
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

    @Transactional
    public Produto adicionarItemNaLista(String nome, String peso, String marca, Integer quantidadeDesejada) {
        String nomeNormalizado = normalizarTexto(nome);
        if (nomeNormalizado == null) {
            return null;
        }

        String pesoNormalizado = normalizarTexto(peso);
        String marcaNormalizada = normalizarTexto(marca);

        Optional<Produto> existente = buscarProdutoExato(nomeNormalizado, marcaNormalizada, pesoNormalizado);
        Produto produto = existente.orElseGet(() -> {
            Produto novo = new Produto(nomeNormalizado, null);
            novo.setNaListaDeCompras(false);
            return novo;
        });

        boolean jaEstavaNaLista = produto.isNaListaDeCompras();
        produto.setNaListaDeCompras(true);

        if (pesoNormalizado != null) {
            produto.setPeso(pesoNormalizado);
        }

        if (marcaNormalizada != null) {
            produto.setMarca(marcaNormalizada);
        }

        if (quantidadeDesejada != null && quantidadeDesejada > 0) {
            if (jaEstavaNaLista && produto.getQuantidadeDesejada() != null && produto.getQuantidadeDesejada() > 0) {
                produto.setQuantidadeDesejada(produto.getQuantidadeDesejada() + quantidadeDesejada);
            } else {
                produto.setQuantidadeDesejada(quantidadeDesejada);
            }
        }

        return produtoRepository.save(produto);
    }

    @Transactional
    public Produto cadastrarProdutoComPrecos(String nome,
                                             String codigoBarras,
                                             String marca,
                                             String peso,
                                             Mercado mercado,
                                             BigDecimal precoVarejo,
                                             BigDecimal precoAtacado,
                                             BigDecimal precoCartao) {
        Produto produto = adicionarItem(nome, codigoBarras, marca, peso, mercado);

        if (mercado != null) {
            if (precoVarejo != null) {
                atualizarPreco(produto.getId(), mercado, TipoPreco.VAREJO, precoVarejo);
            }
            if (precoAtacado != null) {
                atualizarPreco(produto.getId(), mercado, TipoPreco.ATACADO, precoAtacado);
            }
            if (precoCartao != null) {
                atualizarPreco(produto.getId(), mercado, TipoPreco.CARTAO, precoCartao);
            }
        }

        return produtoRepository.findById(produto.getId()).orElse(produto);
    }

    @Transactional
    public Produto atualizarPreferenciasItemDaLista(Long idProduto, String peso, String marca, Integer quantidadeDesejada) {
        Optional<Produto> produtoOpt = produtoRepository.findById(idProduto);
        if (produtoOpt.isEmpty()) {
            return null;
        }

        Produto produto = produtoOpt.get();
        produto.setNaListaDeCompras(true);
        produto.setPeso(normalizarTexto(peso));
        produto.setMarca(normalizarTexto(marca));
        produto.setQuantidadeDesejada(quantidadeDesejada != null && quantidadeDesejada > 0 ? quantidadeDesejada : null);

        return produtoRepository.save(produto);
    }

    public List<RegistroPrecoDTO> listarPrecosPlanos() {
        // 1. Busca TUDO em UMA query (Performance Otimizada)
        List<RegistroPreco> todosPrecos = registroPrecoRepository.findAllCompleto();
        
        // 2. Calcula o menor preço por produto em memória
        Map<Long, BigDecimal> menoresPrecos = todosPrecos.stream()
            .collect(Collectors.groupingBy(
                rp -> rp.getProduto().getId(),
                Collectors.mapping(RegistroPreco::getValor, Collectors.minBy(BigDecimal::compareTo))
            ))
            .entrySet().stream()
            .filter(e -> e.getValue().isPresent())
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));

        List<RegistroPrecoDTO> tabelaPlana = new ArrayList<>();
        
        // 3. Monta a lista final
        for (RegistroPreco rp : todosPrecos) {
            BigDecimal menor = menoresPrecos.get(rp.getProduto().getId());
            boolean ehMelhor = menor != null && rp.getValor().compareTo(menor) == 0;

            tabelaPlana.add(new RegistroPrecoDTO(
                rp.getMercado().getNomeExibicao(),
                rp.getProduto().getNome(),
                rp.getValor(),
                rp.getTipoPreco().getDescricao(),
                ehMelhor
            ));
        }
        
        return tabelaPlana;
    }

    public Produto buscarPorCodigoBarras(String codigoBarras) {
        return produtoRepository.findByCodigoBarras(codigoBarras).orElse(null);
    }

    @Transactional
    public Produto adicionarItem(String nome, String codigoBarras, String marca, String peso) {
        return adicionarItem(nome, codigoBarras, marca, peso, null);
    }

    @Transactional
    public Produto adicionarItem(String nome, String codigoBarras, String marca, String peso, Mercado mercado) {
        String nomeUpper = normalizarTexto(nome);
        String codigoBarrasNormalizado = normalizarCodigoBarras(codigoBarras);
        String marcaNormalizada = normalizarTexto(marca);
        String pesoNormalizado = normalizarTexto(peso);
        
        // Verifica duplicidade por código de barras
        if (codigoBarrasNormalizado != null) {
            Optional<Produto> existente = produtoRepository.findByCodigoBarras(codigoBarrasNormalizado);
            if (existente.isPresent()) {
                return existente.get(); 
            }
        }

        // Cadastro manual: so considera duplicado quando nome, marca, peso e mercado forem identicos.
        Optional<Produto> existenteNome = buscarProdutoExato(nomeUpper, marcaNormalizada, pesoNormalizado);
        if (existenteNome.isPresent()) {
            Produto produtoExistente = existenteNome.get();
            if (mercado == null || possuiPrecoNoMercado(produtoExistente, mercado)) {
                return produtoExistente;
            }
        }
        
        Produto novo = new Produto(nomeUpper, codigoBarrasNormalizado);
        novo.setMarca(marcaNormalizada);
        novo.setPeso(pesoNormalizado);
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

    @Transactional
    public Produto atualizarRegistroDaGrade(Long idProduto,
                                            Mercado mercado,
                                            String nome,
                                            String marca,
                                            String peso,
                                            BigDecimal precoVarejo,
                                            BigDecimal precoAtacado,
                                            BigDecimal precoCartao) {
        Optional<Produto> produtoOpt = produtoRepository.findById(idProduto);
        if (produtoOpt.isEmpty()) {
            return null;
        }

        Produto produto = produtoOpt.get();
        String nomeNormalizado = normalizarTexto(nome);
        if (nomeNormalizado != null) {
            produto.setNome(nomeNormalizado);
        }
        produto.setMarca(normalizarTexto(marca));
        produto.setPeso(normalizarTexto(peso));
        produtoRepository.save(produto);

        atualizarOuManterPreco(produto, mercado, TipoPreco.VAREJO, precoVarejo);
        atualizarOuManterPreco(produto, mercado, TipoPreco.ATACADO, precoAtacado);
        atualizarOuManterPreco(produto, mercado, TipoPreco.CARTAO, precoCartao);

        return produto;
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
        return buscarProdutoExato(normalizarTexto(nome), null, null).isPresent();
    }

    public boolean produtoJaExiste(String nome, String marca, String peso, Mercado mercado) {
        Optional<Produto> existente = buscarProdutoExato(normalizarTexto(nome), normalizarTexto(marca), normalizarTexto(peso));
        if (existente.isEmpty()) {
            return false;
        }
        return mercado == null || possuiPrecoNoMercado(existente.get(), mercado);
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

    private Optional<Produto> buscarProdutoExato(String nome, String marca, String peso) {
        if (nome == null || nome.isBlank()) {
            return Optional.empty();
        }

        return produtoRepository.findAll().stream()
                .filter(produto -> textosIguais(normalizarTexto(produto.getNome()), nome))
                .filter(produto -> textosIguais(normalizarTexto(produto.getMarca()), marca))
                .filter(produto -> textosIguais(normalizarTexto(produto.getPeso()), peso))
                .findFirst();
    }

    private boolean possuiPrecoNoMercado(Produto produto, Mercado mercado) {
        if (produto == null || mercado == null || produto.getHistoricoPrecos() == null) {
            return false;
        }

        return produto.getHistoricoPrecos().stream()
                .anyMatch(registro -> mercado.equals(registro.getMercado()));
    }

    private boolean textosIguais(String valorA, String valorB) {
        return java.util.Objects.equals(normalizarTexto(valorA), normalizarTexto(valorB));
    }

    private void atualizarOuManterPreco(Produto produto, Mercado mercado, TipoPreco tipoPreco, BigDecimal novoValor) {
        if (produto == null || mercado == null || tipoPreco == null) {
            return;
        }

        Optional<RegistroPreco> registroExistente = registroPrecoRepository
                .findTopByProdutoIdAndMercadoAndTipoPrecoOrderByDataRegistroDesc(produto.getId(), mercado, tipoPreco);

        if (novoValor == null) {
            return;
        }

        RegistroPreco registro = registroExistente.orElseGet(() -> new RegistroPreco(produto, mercado, novoValor, tipoPreco));
        registro.setProduto(produto);
        registro.setMercado(mercado);
        registro.setTipoPreco(tipoPreco);
        registro.setValor(novoValor);
        registro.setDataRegistro(LocalDateTime.now());
        registroPrecoRepository.save(registro);
    }

    private BigDecimal obterPrecoMaisRecente(List<RegistroPreco> registros, TipoPreco tipoPreco) {
        if (registros == null || tipoPreco == null) {
            return null;
        }

        return registros.stream()
                .filter(registro -> tipoPreco.equals(registro.getTipoPreco()))
                .filter(registro -> registro.getValor() != null)
                .max(Comparator.comparing(
                        RegistroPreco::getDataRegistro,
                        Comparator.nullsFirst(LocalDateTime::compareTo)
                ))
                .map(RegistroPreco::getValor)
                .orElse(null);
    }

    private String formatarDataHoraCadastro(List<RegistroPreco> registros) {
        if (registros == null) {
            return "-";
        }

        return registros.stream()
                .map(RegistroPreco::getDataRegistro)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .map(FORMATADOR_DATA_HORA::format)
                .orElse("-");
    }


    private String removerAcentos(String str) {
        if (str == null) return "";
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD); 
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    private String normalizarTexto(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        return valor.trim().toUpperCase();
    }

    private String normalizarCodigoBarras(String codigoBarras) {
        if (codigoBarras == null || codigoBarras.trim().isEmpty()) {
            return null;
        }
        return codigoBarras.trim();
    }
}
