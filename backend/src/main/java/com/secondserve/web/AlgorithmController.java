package com.secondserve.web;

import com.secondserve.dto.Dtos.*;
import com.secondserve.exception.NotFoundException;
import com.secondserve.service.AppContext;
import com.secondserve.service.BenchmarkService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/algorithms")
public class AlgorithmController {
    private final AppContext ctx;

    public AlgorithmController(AppContext ctx) { this.ctx = ctx; }

    @GetMapping("/network-analysis")
    public NetworkAnalysis networkAnalysis() { return ctx.allocationService.analysis(); }

    @GetMapping("/bloom-stats")
    public BloomStats bloomStats() { return ctx.bloomService.stats(); }

    @PostMapping("/bloom-check")
    public BloomCheck bloomCheck(@RequestBody Map<String, Object> body) { return ctx.bloomService.check(body); }

    @PostMapping("/bloom-demo")
    public BloomDemo bloomDemo(@RequestBody Map<String, Object> body) { return ctx.bloomService.demo(body); }

    @PostMapping("/benchmarks")
    public BenchmarkService.Report benchmarks(@RequestBody(required = false) Map<String, Object> body) { return ctx.benchmarkService.runRequest(body); }

    @GetMapping("/benchmarks/latest")
    public BenchmarkService.Report latestBenchmarks() {
        BenchmarkService.Report r = ctx.benchmarkService.latest();
        if (r == null) throw new NotFoundException("No benchmark has been run in this session yet");
        return r;
    }
}
