package com.mercado.orcamento.service;

import com.mercado.orcamento.model.Sessao;
import com.mercado.orcamento.repository.SessaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SessaoService {

    @Autowired
    private SessaoRepository repository;

    public Sessao criarSessao(HttpServletRequest request) {
        Sessao sessao = new Sessao();
        sessao.setToken(UUID.randomUUID().toString());
        sessao.setDataHoraAcesso(LocalDateTime.now());
        sessao.setUltimoAcesso(LocalDateTime.now());
        sessao.setAtivo(true);
        
        // Captura IP e URL
        String ip = request.getRemoteAddr();
        String url = request.getRequestURL().toString();
        
        // Simplificação: se for localhost (0:0:0:0... ou 127.0.0.1)
        sessao.setUrlOrigem(url + " | IP: " + ip);
        
        return repository.save(sessao);
    }

    public boolean validarToken(String token) {
        return repository.findByToken(token)
                .map(sessao -> {
                    if (!sessao.isAtivo()) return false;
                    
                    LocalDateTime agora = LocalDateTime.now();

                    // Verifica inatividade (5 minutos)
                    if (sessao.getUltimoAcesso().plusMinutes(5).isBefore(agora)) {
                        sessao.setAtivo(false);
                        repository.save(sessao);
                        return false;
                    }
                    
                    // Otimização: Só atualiza no banco se passou mais de 1 minuto
                    if (sessao.getUltimoAcesso().plusMinutes(1).isBefore(agora)) {
                        sessao.setUltimoAcesso(agora);
                        repository.save(sessao);
                    }
                    
                    return true;
                })
                .orElse(false);
    }

    public void encerrarSessao(String token) {
        if (token != null) {
            repository.invalidarToken(token);
        }
    }

    // Executa a cada 1 minuto para limpar sessões antigas
    @Scheduled(fixedRate = 60000)
    public void limparSessoesInativas() {
        // Remove sessões inativas há mais de 5 minutos
        LocalDateTime limite = LocalDateTime.now().minusMinutes(5);
        repository.deleteSessoesInativas(limite);
        System.out.println("Limpeza de sessões executada: removidas sessões anteriores a " + limite);
    }

    public long contarSessoesAtivas() {
        return repository.countByAtivoTrue();
    }
}
