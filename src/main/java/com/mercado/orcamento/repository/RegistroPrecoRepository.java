package com.mercado.orcamento.repository;

import com.mercado.orcamento.model.Produto;
import com.mercado.orcamento.model.RegistroPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface RegistroPrecoRepository extends JpaRepository<RegistroPreco, Long> {
    
    List<RegistroPreco> findByProduto(Produto produto);

    @Query("SELECT MIN(r.valor) FROM RegistroPreco r WHERE r.produto = :produto")
    BigDecimal findMenorPrecoByProduto(@Param("produto") Produto produto);

    @Query("SELECT AVG(r.valor) FROM RegistroPreco r WHERE r.produto = :produto")
    BigDecimal findMediaPrecoByProduto(@Param("produto") Produto produto);
}
