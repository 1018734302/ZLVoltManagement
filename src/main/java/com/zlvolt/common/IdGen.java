package com.zlvolt.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 演示版 ID 生成器：从表当前最大 ID 开始递增。
 * 生产环境应使用雪花 ID 或号段服务。
 */
@Component
public class IdGen {

    private final JdbcTemplate jdbc;
    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public IdGen(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long next(String table) {
        if (!table.matches("[a-z_]+")) {
            throw new IllegalArgumentException("非法表名: " + table);
        }
        AtomicLong seq = sequences.computeIfAbsent(table, t -> {
            Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + t, Long.class);
            return new AtomicLong(max == null ? 0 : max);
        });
        return seq.incrementAndGet();
    }
}
