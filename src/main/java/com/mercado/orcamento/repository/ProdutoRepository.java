package com.mercado.orcamento.repository;

import com.mercado.orcamento.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    Optional<Produto> findByCodigoBarras(String codigoBarras);
    // Busca por nome para OCR (aproximado pode ser feito com query customizada, mas vamos simples por enquanto)
    Optional<Produto> findByNomeContainingIgnoreCase(String nome);
    
    // Busca todos os produtos com seus preços para evitar N+1 queries e LazyInitializationException
    @Query("SELECT DISTINCT p FROM Produto p LEFT JOIN FETCH p.historicoPrecos ORDER BY p.nome ASC")
    List<Produto> findAllWithPrecos();
}
