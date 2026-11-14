package com.zlvolt.fin;

import com.zlvolt.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fin")
public class FinController {

    private final FinService finService;

    public FinController(FinService finService) {
        this.finService = finService;
    }

    @GetMapping("/reconciliations")
    public ApiResponse<List<Map<String, Object>>> reconciliations() {
        return ApiResponse.ok(finService.reconciliations());
    }

    @GetMapping("/reconciliations/{id}")
    public ApiResponse<Map<String, Object>> reconciliation(@PathVariable long id) {
        return ApiResponse.ok(finService.reconciliation(id));
    }

    @PostMapping("/reconciliations/generate")
    public ApiResponse<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(finService.generateReconciliation(body));
    }

    @PostMapping("/reconciliations/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable long id) {
        return ApiResponse.ok(finService.confirmReconciliation(id));
    }

    @GetMapping("/invoices")
    public ApiResponse<List<Map<String, Object>>> invoices() {
        return ApiResponse.ok(finService.invoices());
    }

    @PostMapping("/invoices")
    public ApiResponse<Map<String, Object>> createInvoice(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(finService.createInvoice(body));
    }

    @PostMapping("/invoices/{id}/match")
    public ApiResponse<Map<String, Object>> match(@PathVariable long id) {
        return ApiResponse.ok(finService.matchInvoice(id));
    }

    @GetMapping("/payables")
    public ApiResponse<List<Map<String, Object>>> payables() {
        return ApiResponse.ok(finService.payables());
    }

    @GetMapping("/payables/{id}")
    public ApiResponse<Map<String, Object>> payable(@PathVariable long id) {
        return ApiResponse.ok(finService.payable(id));
    }
}
