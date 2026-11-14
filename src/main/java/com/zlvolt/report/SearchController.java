package com.zlvolt.report;

import com.zlvolt.common.ApiResponse;
import com.zlvolt.common.MapUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final JdbcTemplate jdbc;

    public SearchController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam(required = false) String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.ok(List.of());
        }
        String like = "%" + keyword.trim() + "%";
        List<Map<String, Object>> results = new ArrayList<>();
        jdbc.queryForList("""
                        SELECT id, code, name, 'CUSTOMER' AS result_type, '客户' AS type_name
                        FROM crm_customer WHERE name LIKE ? OR code LIKE ?
                        """, like, like).forEach(row -> results.add(MapUtils.camel(row)));
        jdbc.queryForList("""
                        SELECT id, code, name, 'PROJECT' AS result_type, '项目' AS type_name
                        FROM pm_project WHERE name LIKE ? OR code LIKE ?
                        """, like, like).forEach(row -> results.add(MapUtils.camel(row)));
        jdbc.queryForList("""
                        SELECT id, code, name, 'SUPPLIER' AS result_type, '供应商' AS type_name
                        FROM srm_supplier WHERE name LIKE ? OR code LIKE ?
                        """, like, like).forEach(row -> results.add(MapUtils.camel(row)));
        jdbc.queryForList("""
                        SELECT id, code, name, 'MATERIAL' AS result_type, '物料' AS type_name
                        FROM srm_material WHERE name LIKE ? OR code LIKE ?
                        """, like, like).forEach(row -> results.add(MapUtils.camel(row)));
        return ApiResponse.ok(results);
    }
}
