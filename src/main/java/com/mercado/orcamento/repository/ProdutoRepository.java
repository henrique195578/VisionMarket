package com.mercado.orcamento.repository;

import com.mercado.orcamento.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    Optional<Produto> findByCodigoBarras(String codigoBarras);
    // Busca por nome para OCR (aproximado pode ser feito com query customizada, mas vamos simples por enquanto)
    Optional<Produto> findByNomeContainingIgnoreCase(String nome);
}
