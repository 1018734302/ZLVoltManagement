package com.zlvolt.fund;

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
@RequestMapping("/api/fund")
public class FundController {

    private final FundService fundService;

    public FundController(FundService fundService) {
        this.fundService = fundService;
    }

    @GetMapping("/accounts")
    public ApiResponse<List<Map<String, Object>>> accounts() {
        return ApiResponse.ok(fundService.accounts());
    }

    @GetMapping("/payment-requests")
    public ApiResponse<List<Map<String, Object>>> paymentRequests() {
        return ApiResponse.ok(fundService.paymentRequests());
    }

    @PostMapping("/payment-requests")
    public ApiResponse<Map<String, Object>> createPaymentRequest(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(fundService.createPaymentRequest(body));
    }

    @PostMapping("/payment-requests/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable long id) {
        return ApiResponse.ok(fundService.approve(id));
    }

    @PostMapping("/payment-requests/{id}/pay")
    public ApiResponse<Map<String, Object>> pay(@PathVariable long id) {
        return ApiResponse.ok(fundService.pay(id));
    }

    @GetMapping("/payments")
    public ApiResponse<List<Map<String, Object>>> payments() {
        return ApiResponse.ok(fundService.payments());
    }
}
