package com.zlvolt.iot;

import com.zlvolt.common.ApiResponse;
import com.zlvolt.common.MapUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/iot")
public class IotController {

    private final IotService iotService;

    public IotController(IotService iotService) {
        this.iotService = iotService;
    }

    @GetMapping("/telemetry")
    public ApiResponse<List<Map<String, Object>>> telemetry(
            @RequestParam(required = false) String stationCode,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(iotService.telemetry(stationCode, limit));
    }

    @PostMapping("/telemetry")
    public ApiResponse<Map<String, Object>> ingest(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(iotService.ingest(payload));
    }

    @PostMapping("/simulate")
    public ApiResponse<Map<String, Object>> simulate(@RequestBody(required = false) Map<String, Object> body) {
        String mode = body == null ? "normal" : MapUtils.text(body, "mode");
        return ApiResponse.ok(iotService.simulate(mode == null ? "normal" : mode));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(iotService.status());
    }
}
