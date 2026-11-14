package com.zlvolt.pm;

import com.zlvolt.common.ApiResponse;
import com.zlvolt.common.MapUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pm")
public class PmController {

    private final PmService pmService;

    public PmController(PmService pmService) {
        this.pmService = pmService;
    }

    @GetMapping("/projects")
    public ApiResponse<List<Map<String, Object>>> projects() {
        return ApiResponse.ok(pmService.projects());
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<Map<String, Object>> project(@PathVariable long id) {
        return ApiResponse.ok(pmService.project(id));
    }

    @PostMapping("/projects/from-contract/{contractId}")
    public ApiResponse<Map<String, Object>> fromContract(@PathVariable long contractId) {
        return ApiResponse.ok(pmService.createProjectFromContract(contractId));
    }

    @PostMapping("/projects/{id}/progress")
    public ApiResponse<Map<String, Object>> progress(@PathVariable long id, @RequestBody Map<String, Object> body) {
        int percent = (int) MapUtils.toLong(body.get("percent"), 0L);
        return ApiResponse.ok(pmService.updateProgress(id, percent));
    }

    @PostMapping("/projects/{id}/change-orders")
    public ApiResponse<Map<String, Object>> changeOrder(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(pmService.createChangeOrder(id, body));
    }

    @PostMapping("/projects/{id}/costs")
    public ApiResponse<Map<String, Object>> addCost(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(pmService.addCost(id, body));
    }

    @GetMapping("/projects/{id}/costs")
    public ApiResponse<Map<String, Object>> costs(@PathVariable long id) {
        return ApiResponse.ok(pmService.costSummary(id));
    }

    @PostMapping("/projects/{id}/grid-connection")
    public ApiResponse<Map<String, Object>> gridConnection(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(pmService.gridConnection(id, body == null ? Map.of() : body));
    }

    @PostMapping("/projects/{id}/settle")
    public ApiResponse<Map<String, Object>> settle(@PathVariable long id) {
        return ApiResponse.ok(pmService.settle(id));
    }
}
