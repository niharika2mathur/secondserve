package com.secondserve.web;

import com.secondserve.model.Principal;
import com.secondserve.service.AppContext;
import com.secondserve.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppContext ctx;

    public AuthController(AppContext ctx) { this.ctx = ctx; }

    @PostMapping("/login")
    public AuthService.LoginResult login(@RequestBody Map<String, Object> body) { return ctx.auth.login(body); }

    @PostMapping("/register")
    public ResponseEntity<AuthService.UserView> register(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ctx.auth.register(body));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        ctx.auth.logout(AuthService.bearer(request.getHeader("Authorization")));
        return Map.of("status", "ok");
    }

    @GetMapping("/me")
    public AuthService.UserView me(@RequestAttribute("principal") Principal p) { return ctx.auth.me(p); }

    @PostMapping("/password")
    public Map<String, String> password(@RequestAttribute("principal") Principal p, @RequestBody Map<String, Object> body) {
        ctx.auth.changePassword(p, body);
        return Map.of("status", "ok");
    }
}
