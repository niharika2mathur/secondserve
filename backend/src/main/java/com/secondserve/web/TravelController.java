package com.secondserve.web;

import com.secondserve.service.AppContext;
import com.secondserve.service.FeasibilityService;
import com.secondserve.service.TravelService;
import com.secondserve.util.Fields;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TravelController {
    private final TravelService travel;
    private final FeasibilityService feasibility;

    public TravelController(AppContext ctx) { this.travel = ctx.travelService; this.feasibility = ctx.feasibilityService; }

    @GetMapping("/travel")
    public TravelService.TravelView get() { return travel.view(); }

    @PutMapping("/travel")
    public TravelService.TravelView update(@RequestBody Map<String, Object> body) { return travel.update(body); }

    @GetMapping("/feasibility")
    public FeasibilityService.View feasibility(@RequestParam String planningTime) {
        return feasibility.view(Fields.parseTime(planningTime, "Planning date and time"));
    }
}
