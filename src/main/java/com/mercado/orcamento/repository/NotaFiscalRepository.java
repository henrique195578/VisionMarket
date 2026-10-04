package com.mercado.orcamento.repository;
import com.mercado.orcamento.model.NotaFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {
    boolean existsByHashArquivo(String hash);
    boolean existsByHashQr(String hash);
}
