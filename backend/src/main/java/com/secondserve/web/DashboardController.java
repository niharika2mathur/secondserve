package com.secondserve.web;

import com.secondserve.dto.Dtos.Dashboard;
import com.secondserve.service.AppContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final AppContext ctx;

    public DashboardController(AppContext ctx) { this.ctx = ctx; }

    @GetMapping("/dashboard")
    public Dashboard dashboard() { return ctx.dashboardService.build(); }

    /** Body: {"mode": "sample" | "empty"}. */
    @PostMapping("/data/reset")
    public Map<String, String> reset(@RequestBody(required = false) Map<String, Object> body) {
        String mode = body != null && "empty".equals(body.get("mode")) ? "empty" : "sample";
        ctx.reset(mode);
        return Map.of("status", "ok", "mode", mode);
    }
}
