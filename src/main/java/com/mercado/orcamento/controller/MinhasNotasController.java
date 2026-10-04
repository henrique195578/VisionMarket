package com.mercado.orcamento.controller;
import com.mercado.orcamento.model.*;
import com.mercado.orcamento.repository.*;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/minhas-notas")
public class MinhasNotasController {
    private final NotaFiscalRepository notas;
    private final RegistroPrecoRepository precos;
    public MinhasNotasController(NotaFiscalRepository notas, RegistroPrecoRepository precos) { this.notas=notas;this.precos=precos; }
    public record Item(Long id,String nome,BigDecimal quantidade,String unidade,BigDecimal precoUnitario,BigDecimal total,boolean estimado) {}
    public record Cupom(Long id,String nome,String emitenteOriginal,String mercado,String unidade,LocalDateTime dataCompra,
                        LocalDateTime dataImportacao,int quantidadeItens,BigDecimal total,boolean totalEstimado,
                        boolean estabelecimentoConfirmado,String arquivo,List<Item> itens) {}
    private Cupom dto(NotaFiscal n,List<RegistroPreco> registros) {
        var itens=registros.stream().map(r->new Item(r.getId(),r.getNomeItemNota()==null?r.getProduto().getNome():r.getNomeItemNota(),
            r.getQuantidadeCompra(),r.getUnidadeMedida(),r.getValor(),
            r.getTotalItemNota()!=null?r.getTotalItemNota():r.getQuantidadeCompra()==null?null:r.getValor().multiply(r.getQuantidadeCompra()).setScale(2,RoundingMode.HALF_UP),
            r.getTotalItemNota()==null)).toList();
        boolean estimado=n.getTotalItens()==null;
        BigDecimal total=estimado?itens.stream().map(Item::total).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add):n.getTotalItens();
        String nome=n.getNomeEstabelecimento()!=null?n.getNomeEstabelecimento():n.getMercado()==null?"Estabelecimento não informado":n.getMercado().getNomeExibicao();
        return new Cupom(n.getId(),nome,n.getEmitenteOriginal(),n.getMercado()==null?null:n.getMercado().name(),n.getUnidade(),n.getDataCompra(),
            n.getDataImportacao(),n.getQuantidadeItens()==null?itens.size():n.getQuantidadeItens(),total,estimado,
            Boolean.TRUE.equals(n.getEstabelecimentoConfirmado()),n.getNomeArquivoImagem(),itens);
    }
    @GetMapping
    @Transactional(readOnly=true)
    public List<Cupom> listar() {
        var lista=notas.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"dataCompra","id"));
        if(lista.isEmpty()) return List.of();
        var porNota=precos.findPorNotas(lista.stream().map(NotaFiscal::getId).toList()).stream().collect(Collectors.groupingBy(RegistroPreco::getNotaFiscalId));
        return lista.stream().map(n->dto(n,porNota.getOrDefault(n.getId(),List.of()))).toList();
    }
    @GetMapping("/{id}")
    @Transactional(readOnly=true)
    public ResponseEntity<?> detalhe(@PathVariable Long id) {
        return notas.findById(id).<ResponseEntity<?>>map(n->ResponseEntity.ok(dto(n,precos.findPorNotas(List.of(id)))))
            .orElseGet(()->ResponseEntity.notFound().build());
    }
    public record Alteracao(String nome,Mercado mercado,String unidade) {}
    @PatchMapping("/{id}/estabelecimento")
    @Transactional
    public ResponseEntity<?> alterar(@PathVariable Long id,@RequestBody Alteracao pedido) {
        if(pedido.nome()==null || pedido.nome().isBlank() || pedido.nome().trim().length()>150
            || pedido.mercado()==null || pedido.unidade()==null || pedido.unidade().isBlank() || pedido.unidade().trim().length()>150)
            return ResponseEntity.badRequest().body(Map.of("erro","Informe nome, mercado de comparação e filial, com até 150 caracteres."));
        var encontrado=notas.findById(id);
        if(encontrado.isEmpty()) return ResponseEntity.notFound().build();
        var n=encontrado.get();n.setNomeEstabelecimento(pedido.nome().trim());n.setMercado(pedido.mercado());
        n.setUnidade(pedido.unidade().trim());n.setEstabelecimentoConfirmado(true);
        var registros=precos.findPorNotas(List.of(id));
        registros.forEach(r->{r.setMercado(pedido.mercado());r.setUnidade(n.getUnidade());});
        return ResponseEntity.ok(dto(n,registros));
    }
}
