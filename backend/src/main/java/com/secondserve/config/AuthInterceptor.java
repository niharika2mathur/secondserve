package com.secondserve.config;

import com.secondserve.model.Principal;
import com.secondserve.service.AccessPolicy;
import com.secondserve.service.AppContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the Bearer token to a Principal (401 if missing/invalid) and applies the same AccessPolicy as the standalone
 * server (403 if the role may not use the route). Domain exceptions thrown here are mapped by GlobalExceptionHandler.
 */
public class AuthInterceptor implements HandlerInterceptor {
    public static final String ATTR = "principal";
    private final AppContext ctx;

    public AuthInterceptor(AppContext ctx) { this.ctx = ctx; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod(), path = request.getRequestURI();
        if (AccessPolicy.isPublic(method, path)) return true;
        Principal p = ctx.auth.authenticate(request.getHeader("Authorization"));
        AccessPolicy.check(method, path, p);
        request.setAttribute(ATTR, p);
        return true;
    }
}
