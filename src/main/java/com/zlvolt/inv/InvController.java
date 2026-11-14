package com.zlvolt.inv;

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
@RequestMapping("/api/inv")
public class InvController {

    private final InvService invService;

    public InvController(InvService invService) {
        this.invService = invService;
    }

    @GetMapping("/warehouses")
    public ApiResponse<List<Map<String, Object>>> warehouses() {
        return ApiResponse.ok(invService.warehouses());
    }

    @GetMapping("/stocks")
    public ApiResponse<List<Map<String, Object>>> stocks() {
        return ApiResponse.ok(invService.stocks());
    }

    @GetMapping("/receipts")
    public ApiResponse<List<Map<String, Object>>> receipts() {
        return ApiResponse.ok(invService.receipts());
    }

    @GetMapping("/receipts/{id}")
    public ApiResponse<Map<String, Object>> receipt(@PathVariable long id) {
        return ApiResponse.ok(invService.receipt(id));
    }

    @GetMapping("/movements")
    public ApiResponse<List<Map<String, Object>>> movements() {
        return ApiResponse.ok(invService.movements());
    }

    @PostMapping("/receipts")
    public ApiResponse<Map<String, Object>> receive(@RequestBody Map<String, Object> body) {
        long poId = MapUtils.toLong(body.get("poId"), 0L);
        Long warehouseId = body.get("warehouseId") == null ? null : MapUtils.toLong(body.get("warehouseId"), 2L);
        return ApiResponse.ok(invService.receivePo(poId, warehouseId));
    }

    @PostMapping("/issues")
    public ApiResponse<Map<String, Object>> issue(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(invService.issueMaterials(body));
    }

    @GetMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> issueDetail(@PathVariable long id) {
        return ApiResponse.ok(invService.issue(id));
    }
}
