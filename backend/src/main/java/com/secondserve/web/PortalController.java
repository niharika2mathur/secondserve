package com.secondserve.web;

import com.secondserve.model.Delivery;
import com.secondserve.model.Donation;
import com.secondserve.model.Ngo;
import com.secondserve.model.Principal;
import com.secondserve.service.AppContext;
import com.secondserve.service.NotificationService;
import com.secondserve.service.PortalService;
import com.secondserve.service.RoleDashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Donor and NGO self-service plus the delivery life cycle. Ownership checks live in the services. */
@RestController
@RequestMapping("/api")
public class PortalController {
    private final AppContext ctx;

    public PortalController(AppContext ctx) { this.ctx = ctx; }

    // ---- donors
    @GetMapping("/my/donations")
    public List<Donation> myDonations(@RequestAttribute("principal") Principal p) { return ctx.portal.myDonations(p); }

    @PostMapping("/my/donations")
    public ResponseEntity<PortalService.DonorCreateResult> create(@RequestAttribute("principal") Principal p, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ctx.portal.createDonation(p, body));
    }

    @PutMapping("/my/donations/{id}")
    public Donation update(@RequestAttribute("principal") Principal p, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ctx.portal.updateDonation(p, id, body);
    }

    @DeleteMapping("/my/donations/{id}")
    public ResponseEntity<Void> delete(@RequestAttribute("principal") Principal p, @PathVariable String id) {
        ctx.portal.deleteDonation(p, id);
        return ResponseEntity.noContent().build();
    }

    // ---- NGOs
    @GetMapping("/my/ngo")
    public Ngo myNgo(@RequestAttribute("principal") Principal p) { return ctx.portal.myNgo(p); }

    @PutMapping("/my/ngo")
    public Ngo updateNgo(@RequestAttribute("principal") Principal p, @RequestBody Map<String, Object> body) { return ctx.portal.updateMyNgo(p, body); }

    // ---- deliveries
    @GetMapping("/my/deliveries")
    public List<Delivery> myDeliveries(@RequestAttribute("principal") Principal p) { return ctx.dispatch.list(p); }

    @PostMapping("/deliveries/{id}/handover")
    public Delivery handover(@RequestAttribute("principal") Principal p, @PathVariable String id) { return ctx.dispatch.handover(p, id); }

    @PostMapping("/deliveries/{id}/confirm")
    public Delivery confirm(@RequestAttribute("principal") Principal p, @PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return ctx.dispatch.confirm(p, id, body);
    }

    @PostMapping("/deliveries/{id}/report")
    public Delivery report(@RequestAttribute("principal") Principal p, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ctx.dispatch.report(p, id, body);
    }

    // ---- role dashboards and notifications
    @GetMapping("/dashboard/donor")
    public RoleDashboardService.DonorDashboard donorDashboard(@RequestAttribute("principal") Principal p) { return ctx.roleDashboards.donor(p); }

    @GetMapping("/dashboard/ngo")
    public RoleDashboardService.NgoDashboard ngoDashboard(@RequestAttribute("principal") Principal p) { return ctx.roleDashboards.ngo(p); }

    @GetMapping("/notifications")
    public List<NotificationService.Notification> notifications(@RequestAttribute("principal") Principal p) { return ctx.notifications.list(p.userId()); }

    @PostMapping("/notifications/read-all")
    public Map<String, String> readAll(@RequestAttribute("principal") Principal p) {
        ctx.notifications.markAllRead(p.userId());
        return Map.of("status", "ok");
    }
}
