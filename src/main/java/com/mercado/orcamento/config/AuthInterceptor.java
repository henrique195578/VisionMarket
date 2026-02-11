package com.mercado.orcamento.config;

import com.mercado.orcamento.model.Sessao;
import com.mercado.orcamento.service.SessaoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private SessaoService sessaoService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        
        // Ignorar recursos estáticos e página de erro
        if (path.startsWith("/css") || path.startsWith("/js") || path.startsWith("/images") || 
            path.startsWith("/error") || path.equals("/acesso-negado")) {
            return true;
        }

        String token = null;
        if (request.getCookies() != null) {
            token = Arrays.stream(request.getCookies())
                    .filter(c -> "APP_SESSION_TOKEN".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (token != null) {
            if (sessaoService.validarToken(token)) {
                return true;
            } else {
                // Token inválido (ex: expirou no banco, servidor reiniciou ou foi forjado)
                
                // Se o usuário está tentando acessar a HOME, renovamos a sessão automaticamente
                // para evitar bloqueio eterno por cookie antigo
                if (path.equals("/")) {
                    System.out.println("Token inválido na Home. Renovando sessão...");
                    Sessao novaSessao = sessaoService.criarSessao(request);
                    adicionarCookieSessao(response, novaSessao.getToken());
                    return true;
                }

                // Para outras rotas, bloqueia
                response.sendRedirect("/acesso-negado");
                return false;
            }
        }

        // Se não tem token, cria uma nova sessão (fluxo de entrada)
        Sessao novaSessao = sessaoService.criarSessao(request);
        adicionarCookieSessao(response, novaSessao.getToken());
        
        return true;
    }

    private void adicionarCookieSessao(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("APP_SESSION_TOKEN", token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(60 * 60 * 24); // 1 dia
        response.addCookie(cookie);
    }
}
