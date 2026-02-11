package com.mercado.orcamento.repository;

import com.mercado.orcamento.model.Sessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SessaoRepository extends JpaRepository<Sessao, Long> {
    Optional<Sessao> findByToken(String token);
    long countByAtivoTrue();

    @Transactional
    @Modifying
    @Query("DELETE FROM Sessao s WHERE s.ativo = false OR s.ultimoAcesso < :limite")
    void deleteSessoesInativas(LocalDateTime limite);
    
    @Transactional
    @Modifying
    @Query("UPDATE Sessao s SET s.ativo = false WHERE s.token = :token")
    void invalidarToken(String token);
}
