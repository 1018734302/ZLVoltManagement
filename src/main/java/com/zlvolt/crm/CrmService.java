package com.zlvolt.crm;

import com.zlvolt.common.BizException;
import com.zlvolt.common.CodeGen;
import com.zlvolt.common.IdGen;
import com.zlvolt.common.MapUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class CrmService {

    private final JdbcTemplate jdbc;
    private final IdGen idGen;
    private final CodeGen codeGen;

    public CrmService(JdbcTemplate jdbc, IdGen idGen, CodeGen codeGen) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.codeGen = codeGen;
    }

    public List<Map<String, Object>> customers() {
        String sql = """
                SELECT c.id, c.code, c.name, c.type, c.level, c.region,
                       c.contact_name, c.contact_phone, c.status,
                       u.real_name AS owner_name
                FROM crm_customer c
                LEFT JOIN sys_user u ON u.id = c.owner_id
                ORDER BY c.id DESC
                """;
        return MapUtils.camel(jdbc.queryForList(sql));
    }

    public Map<String, Object> createCustomer(Map<String, Object> body) {
        String name = MapUtils.text(body, "name");
        if (name == null || name.isBlank()) {
            throw new BizException("客户名称不能为空");
        }
        long id = idGen.next("crm_customer");
        String code = codeGen.next("CUS");
        String type = defaultText(MapUtils.text(body, "type"), "ROOFTOP_OWNER");
        String level = defaultText(MapUtils.text(body, "level"), "B");
        String region = MapUtils.text(body, "region");
        String contactName = MapUtils.text(body, "contactName");
        String contactPhone = MapUtils.text(body, "contactPhone");
        long ownerId = MapUtils.toLong(body.get("ownerId"), 3L);
        jdbc.update("""
                        INSERT INTO crm_customer
                        (id, code, name, type, level, region, contact_name, contact_phone, owner_id, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                        """,
                id, code, name, type, level, region, contactName, contactPhone, ownerId);
        return customer(id);
    }

    public Map<String, Object> customer(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id, c.code, c.name, c.type, c.level, c.region,
                       c.contact_name, c.contact_phone, c.status,
                       u.real_name AS owner_name
                FROM crm_customer c
                LEFT JOIN sys_user u ON u.id = c.owner_id
                WHERE c.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "客户不存在");
        }
        return MapUtils.camel(rows.get(0));
    }

    public List<Map<String, Object>> leads() {
        String sql = """
                SELECT l.id, l.code, l.name, l.customer_id, c.name AS customer_name,
                       l.source, l.estimated_capacity_mw, l.estimated_amount,
                       l.stage, l.next_action, u.real_name AS owner_name
                FROM crm_lead l
                LEFT JOIN crm_customer c ON c.id = l.customer_id
                LEFT JOIN sys_user u ON u.id = l.owner_id
                ORDER BY l.id DESC
                """;
        return MapUtils.camel(jdbc.queryForList(sql));
    }

    public Map<String, Object> createLead(Map<String, Object> body) {
        String name = MapUtils.text(body, "name");
        if (name == null || name.isBlank()) {
            throw new BizException("线索名称不能为空");
        }
        long id = idGen.next("crm_lead");
        String code = codeGen.next("LEAD");
        Long customerId = body.get("customerId") == null ? null : MapUtils.toLong(body.get("customerId"), 0L);
        String source = defaultText(MapUtils.text(body, "source"), "官网咨询");
        BigDecimal capacity = MapUtils.toDecimal(body.get("estimatedCapacityMw"), BigDecimal.ONE);
        BigDecimal amount = MapUtils.toDecimal(body.get("estimatedAmount"), new BigDecimal("4200000"));
        long ownerId = MapUtils.toLong(body.get("ownerId"), 3L);
        jdbc.update("""
                        INSERT INTO crm_lead
                        (id, code, name, customer_id, source, estimated_capacity_mw, estimated_amount,
                         owner_id, stage, next_action, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'NEW', '电话联系', 'ACTIVE')
                        """,
                id, code, name, customerId, source, capacity, amount, ownerId);
        return lead(id);
    }

    public Map<String, Object> lead(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT l.id, l.code, l.name, l.customer_id, c.name AS customer_name,
                       l.source, l.estimated_capacity_mw, l.estimated_amount,
                       l.stage, l.next_action, u.real_name AS owner_name
                FROM crm_lead l
                LEFT JOIN crm_customer c ON c.id = l.customer_id
                LEFT JOIN sys_user u ON u.id = l.owner_id
                WHERE l.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "线索不存在");
        }
        return MapUtils.camel(rows.get(0));
    }

    public Map<String, Object> convertLead(long leadId) {
        Map<String, Object> lead = lead(leadId);
        if ("CONVERTED".equals(lead.get("stage"))) {
            throw new BizException("该线索已转商机");
        }
        long customerId = MapUtils.toLong(lead.get("customerId"), 0L);
        if (customerId == 0L) {
            throw new BizException("线索未关联客户，无法转商机");
        }
        long id = idGen.next("crm_opportunity");
        String code = codeGen.next("OPP");
        BigDecimal amount = MapUtils.decimal(lead, "estimatedAmount");
        BigDecimal capacity = MapUtils.decimal(lead, "estimatedCapacityMw");
        jdbc.update("""
                        INSERT INTO crm_opportunity
                        (id, code, lead_id, customer_id, name, capacity_mw, amount, win_rate,
                         stage, expected_sign_date, owner_id, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 40.00, 'QUALIFYING', ?, 3, 'ACTIVE')
                        """,
                id, code, leadId, customerId, lead.get("name") + " 商机", capacity, amount,
                Date.valueOf(LocalDate.now().plusDays(30)));
        jdbc.update("UPDATE crm_lead SET stage = 'CONVERTED', next_action = '已转商机' WHERE id = ?", leadId);
        return opportunity(id);
    }

    public List<Map<String, Object>> opportunities() {
        String sql = """
                SELECT o.id, o.code, o.lead_id, o.customer_id, c.name AS customer_name,
                       o.name, o.capacity_mw, o.amount, o.win_rate, o.stage,
                       o.expected_sign_date, u.real_name AS owner_name
                FROM crm_opportunity o
                LEFT JOIN crm_customer c ON c.id = o.customer_id
                LEFT JOIN sys_user u ON u.id = o.owner_id
                ORDER BY o.id DESC
                """;
        return MapUtils.camel(jdbc.queryForList(sql));
    }

    public Map<String, Object> opportunity(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT o.id, o.code, o.lead_id, o.customer_id, c.name AS customer_name,
                       o.name, o.capacity_mw, o.amount, o.win_rate, o.stage,
                       o.expected_sign_date, u.real_name AS owner_name
                FROM crm_opportunity o
                LEFT JOIN crm_customer c ON c.id = o.customer_id
                LEFT JOIN sys_user u ON u.id = o.owner_id
                WHERE o.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "商机不存在");
        }
        return MapUtils.camel(rows.get(0));
    }

    public List<Map<String, Object>> contracts() {
        String sql = """
                SELECT ct.id, ct.code, ct.customer_id, c.name AS customer_name,
                       ct.opportunity_id, ct.name, ct.type, ct.amount, ct.tax_rate,
                       ct.sign_date, ct.status, p.id AS project_id, p.code AS project_code,
                       p.name AS project_name
                FROM crm_contract ct
                LEFT JOIN crm_customer c ON c.id = ct.customer_id
                LEFT JOIN pm_project p ON p.contract_id = ct.id
                ORDER BY ct.id DESC
                """;
        return MapUtils.camel(jdbc.queryForList(sql));
    }

    public Map<String, Object> createContract(Map<String, Object> body) {
        long customerId = MapUtils.toLong(body.get("customerId"), 0L);
        if (customerId == 0L) {
            throw new BizException("请选择客户");
        }
        long id = idGen.next("crm_contract");
        String code = codeGen.next("CT");
        Long opportunityId = body.get("opportunityId") == null ? null : MapUtils.toLong(body.get("opportunityId"), 0L);
        if (opportunityId != null && opportunityId == 0L) {
            opportunityId = null;
        }
        String name = MapUtils.text(body, "name");
        if (name == null || name.isBlank()) {
            name = "光伏电站 EPC 合同";
        }
        BigDecimal amount = MapUtils.toDecimal(body.get("amount"), new BigDecimal("4200000"));
        BigDecimal taxRate = MapUtils.toDecimal(body.get("taxRate"), new BigDecimal("0.1300"))
                .setScale(4, RoundingMode.HALF_UP);
        jdbc.update("""
                        INSERT INTO crm_contract
                        (id, code, customer_id, opportunity_id, name, type, amount, tax_rate,
                         sign_date, start_date, end_date, payment_terms, retention_rate,
                         warranty_months, status)
                        VALUES (?, ?, ?, ?, ?, 'EPC', ?, ?, ?, ?, ?, '30%预付款，65%并网后30天，5%质保金', 0.0500, 12, 'EFFECTIVE')
                        """,
                id, code, customerId, opportunityId, name, amount, taxRate,
                Date.valueOf(LocalDate.now()), Date.valueOf(LocalDate.now()),
                Date.valueOf(LocalDate.now().plusYears(1)));
        return contract(id);
    }

    public Map<String, Object> contract(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ct.id, ct.code, ct.customer_id, c.name AS customer_name,
                       ct.opportunity_id, ct.name, ct.type, ct.amount, ct.tax_rate,
                       ct.sign_date, ct.status, p.id AS project_id, p.code AS project_code,
                       p.name AS project_name
                FROM crm_contract ct
                LEFT JOIN crm_customer c ON c.id = ct.customer_id
                LEFT JOIN pm_project p ON p.contract_id = ct.id
                WHERE ct.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "合同不存在");
        }
        return MapUtils.camel(rows.get(0));
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
