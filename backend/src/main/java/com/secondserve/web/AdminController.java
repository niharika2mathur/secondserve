package com.secondserve.web;

import com.secondserve.model.Delivery;
import com.secondserve.model.Principal;
import com.secondserve.service.*;
import com.secondserve.util.Fields;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Coordinator-only features: dispatch plan, deliveries overview, accounts, audit log, analytics and what-if insights. */
@RestController
@RequestMapping("/api")
public class AdminController {
    private final AppContext ctx;

    public AdminController(AppContext ctx) { this.ctx = ctx; }

    @GetMapping("/dashboard/admin")
    public RoleDashboardService.AdminOverview overview(@RequestAttribute("principal") Principal p) { return ctx.roleDashboards.admin(p); }

    @PostMapping("/dispatch/commit")
    public DispatchService.CommitReport commit(@RequestAttribute("principal") Principal p) { return ctx.dispatch.commit(p); }

    @GetMapping("/deliveries")
    public List<Delivery> deliveries(@RequestAttribute("principal") Principal p) { return ctx.dispatch.list(p); }

    @GetMapping("/admin/users")
    public List<AuthService.UserView> users() { return ctx.auth.listUsers(); }

    @PostMapping("/admin/users/{id}/approve")
    public AuthService.UserView approve(@RequestAttribute("principal") Principal p, @PathVariable String id) { return ctx.auth.approve(p, id); }

    @PostMapping("/admin/users/{id}/reject")
    public AuthService.UserView reject(@RequestAttribute("principal") Principal p, @PathVariable String id) { return ctx.auth.reject(p, id); }

    @PostMapping("/admin/users/{id}/disable")
    public AuthService.UserView disable(@RequestAttribute("principal") Principal p, @PathVariable String id) { return ctx.auth.setEnabled(p, id, false); }

    @PostMapping("/admin/users/{id}/enable")
    public AuthService.UserView enable(@RequestAttribute("principal") Principal p, @PathVariable String id) { return ctx.auth.setEnabled(p, id, true); }

    @GetMapping("/admin/audit")
    public List<AuditService.Entry> audit() { return ctx.audit.recent(200); }

    @GetMapping("/analytics")
    public AnalyticsService.Summary analytics() { return ctx.analytics.summary(); }

    @GetMapping("/insights/recommendations")
    public InsightService.Insights recommendations(@RequestParam("planningTime") String planningTime) {
        return ctx.insights.recommendations(Fields.parseTime(planningTime, "Planning date and time"));
    }

    @PostMapping("/insights/what-if")
    public InsightService.WhatIfResult whatIf(@RequestBody Map<String, Object> body) { return ctx.insights.whatIf(body); }
}
