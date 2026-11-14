package com.zlvolt.srm;

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
@RequestMapping("/api/srm")
public class SrmController {

    private final SrmService srmService;

    public SrmController(SrmService srmService) {
        this.srmService = srmService;
    }

    @GetMapping("/suppliers")
    public ApiResponse<List<Map<String, Object>>> suppliers() {
        return ApiResponse.ok(srmService.suppliers());
    }

    @GetMapping("/materials")
    public ApiResponse<List<Map<String, Object>>> materials() {
        return ApiResponse.ok(srmService.materials());
    }

    @GetMapping("/purchase-orders")
    public ApiResponse<List<Map<String, Object>>> purchaseOrders() {
        return ApiResponse.ok(srmService.purchaseOrders());
    }

    @GetMapping("/purchase-orders/{id}")
    public ApiResponse<Map<String, Object>> purchaseOrder(@PathVariable long id) {
        return ApiResponse.ok(srmService.purchaseOrder(id));
    }

    @PostMapping("/purchase-orders")
    public ApiResponse<Map<String, Object>> createPurchaseOrder(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(srmService.createPurchaseOrder(body));
    }

    @PostMapping("/purchase-orders/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable long id) {
        return ApiResponse.ok(srmService.approve(id));
    }

    @PostMapping("/purchase-orders/{id}/deliver")
    public ApiResponse<Map<String, Object>> deliver(@PathVariable long id) {
        return ApiResponse.ok(srmService.deliver(id));
    }
}
