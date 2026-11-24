package com.secondserve.web;

import com.secondserve.dto.Dtos.AllocationResult;
import com.secondserve.service.AllocationService;
import com.secondserve.service.AppContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/allocation")
public class AllocationController {
    private final AllocationService service;

    public AllocationController(AppContext ctx) { this.service = ctx.allocationService; }

    @PostMapping("/run")
    public AllocationResult run(@RequestBody Map<String, Object> body) { return service.runRequest(body); }

    @GetMapping("/results")
    public AllocationResult results() { return service.results(); }
}
