package com.secondserve.web;

import com.secondserve.model.Donation;
import com.secondserve.service.AppContext;
import com.secondserve.service.DonationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/donations")
public class DonationController {
    private final DonationService service;

    public DonationController(AppContext ctx) { this.service = ctx.donationService; }

    @GetMapping
    public List<Donation> list() { return service.list(); }

    @PostMapping
    public ResponseEntity<DonationService.CreateResult> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
    }

    @PutMapping("/{id}")
    public Donation update(@PathVariable String id, @RequestBody Map<String, Object> body) { return service.update(id, body); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import-csv")
    public DonationService.ImportReport importCsv(@RequestBody Map<String, Object> body) {
        return service.importCsv(body.get("csv") == null ? null : String.valueOf(body.get("csv")));
    }
}
