package com.zlvolt.om;

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
@RequestMapping("/api/om")
public class OmController {

    private final OmService omService;

    public OmController(OmService omService) {
        this.omService = omService;
    }

    @GetMapping("/stations")
    public ApiResponse<List<Map<String, Object>>> stations() {
        return ApiResponse.ok(omService.stations());
    }

    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> devices() {
        return ApiResponse.ok(omService.devices());
    }

    @GetMapping("/alarms")
    public ApiResponse<List<Map<String, Object>>> alarms() {
        return ApiResponse.ok(omService.alarms());
    }

    @PostMapping("/alarms/{id}/ack")
    public ApiResponse<Map<String, Object>> ack(@PathVariable long id) {
        return ApiResponse.ok(omService.ackAlarm(id));
    }

    @GetMapping("/work-orders")
    public ApiResponse<List<Map<String, Object>>> workOrders() {
        return ApiResponse.ok(omService.workOrders());
    }

    @PostMapping("/work-orders")
    public ApiResponse<Map<String, Object>> createWorkOrder(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(omService.createWorkOrder(body));
    }

    @PostMapping("/work-orders/{id}/assign")
    public ApiResponse<Map<String, Object>> assign(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(omService.assign(id, body));
    }

    @PostMapping("/work-orders/{id}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(omService.complete(id, body == null ? Map.of() : body));
    }

    @PostMapping("/work-orders/{id}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable long id) {
        return ApiResponse.ok(omService.close(id));
    }
}
