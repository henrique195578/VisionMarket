package com.mercado.orcamento.service;

import com.mercado.orcamento.model.Sessao;
import com.mercado.orcamento.repository.SessaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SessaoService {

    private static final Logger logger = LoggerFactory.getLogger(SessaoService.class);

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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // Executa a cada 1 minuto para limpar sessões antigas
    @jakarta.annotation.PostConstruct
    public void limparNoStartup() {
        logger.info("Iniciando limpeza total de sessões no banco de dados...");
        try {
            // Tenta limpar e resetar o ID (funciona no PostgreSQL e H2 modernos)
            jdbcTemplate.execute("TRUNCATE TABLE sessao RESTART IDENTITY");
            logger.info("Tabela truncada e ID reiniciado com sucesso.");
        } catch (Exception e) {
            // Fallback caso o banco não suporte TRUNCATE ou ocorra erro
            repository.deleteAll();
            logger.warn("Limpeza realizada via deleteAll (ID não resetado): {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60000)
    public void limparSessoesInativas() {
        // Remove sessões inativas há mais de 5 minutos
        LocalDateTime limite = LocalDateTime.now().minusMinutes(5);
        repository.deleteSessoesInativas(limite);
        
        // Verifica se a tabela ficou vazia para tentar resetar o ID
        if (repository.count() == 0) {
            try {
                jdbcTemplate.execute("ALTER SEQUENCE sessao_id_seq RESTART WITH 1");
                logger.info("Tabela vazia: ID reiniciado para 1.");
            } catch (Exception e) {
                // Ignora erro se a sequence tiver outro nome ou banco for diferente
            }
        }
        
        logger.debug("Limpeza de sessões executada: removidas sessões anteriores a {}", limite);
    }

    public long contarSessoesAtivas() {
        return repository.countByAtivoTrue();
    }
}
