package com.zlvolt.crm;

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
@RequestMapping("/api/crm")
public class CrmController {

    private final CrmService crmService;

    public CrmController(CrmService crmService) {
        this.crmService = crmService;
    }

    @GetMapping("/customers")
    public ApiResponse<List<Map<String, Object>>> customers() {
        return ApiResponse.ok(crmService.customers());
    }

    @PostMapping("/customers")
    public ApiResponse<Map<String, Object>> createCustomer(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(crmService.createCustomer(body));
    }

    @GetMapping("/leads")
    public ApiResponse<List<Map<String, Object>>> leads() {
        return ApiResponse.ok(crmService.leads());
    }

    @PostMapping("/leads")
    public ApiResponse<Map<String, Object>> createLead(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(crmService.createLead(body));
    }

    @PostMapping("/leads/{id}/convert")
    public ApiResponse<Map<String, Object>> convertLead(@PathVariable long id) {
        return ApiResponse.ok(crmService.convertLead(id));
    }

    @GetMapping("/opportunities")
    public ApiResponse<List<Map<String, Object>>> opportunities() {
        return ApiResponse.ok(crmService.opportunities());
    }

    @GetMapping("/contracts")
    public ApiResponse<List<Map<String, Object>>> contracts() {
        return ApiResponse.ok(crmService.contracts());
    }

    @PostMapping("/contracts")
    public ApiResponse<Map<String, Object>> createContract(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(crmService.createContract(body));
    }
}
