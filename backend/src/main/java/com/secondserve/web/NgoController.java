package com.secondserve.web;

import com.secondserve.model.Ngo;
import com.secondserve.service.AppContext;
import com.secondserve.service.NgoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ngos")
public class NgoController {
    private final NgoService service;

    public NgoController(AppContext ctx) { this.service = ctx.ngoService; }

    @GetMapping
    public List<Ngo> list() { return service.list(); }

    @PostMapping
    public ResponseEntity<Ngo> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
    }

    @PutMapping("/{id}")
    public Ngo update(@PathVariable String id, @RequestBody Map<String, Object> body) { return service.update(id, body); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
