package com.zlvolt.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 演示版数据库初始化：
 * 复用 db/mysql 下已经验证过的 MySQL schema/seed，在启动时转换为 H2 可执行语法。
 * 生产环境仍使用 MySQL + Flyway，本类只在 demo profile 生效。
 */
@Component
public class H2DemoInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(H2DemoInitializer.class);

    private final JdbcTemplate jdbc;

    @Value("${zl.demo.reset-on-start:true}")
    private boolean resetOnStart;

    public H2DemoInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!resetOnStart) {
            return;
        }
        jdbc.execute("DROP ALL OBJECTS");
        int schema = executeScript("db/mysql/01_schema.sql", true);
        int seed = executeScript("db/mysql/02_seed.sql", true);
        int extra = executeScript("db/demo-extra.sql", false);

        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = 'public'", Integer.class);
        Integer customers = jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer", Integer.class);
        Integer projects = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project", Integer.class);
        Integer purchaseOrders = jdbc.queryForObject("SELECT COUNT(*) FROM srm_purchase_order", Integer.class);
        Integer payments = jdbc.queryForObject("SELECT COUNT(*) FROM fund_payment_record", Integer.class);
        log.info("H2 demo initialized: schemaStatements={}, seedStatements={}, extraStatements={}", schema, seed, extra);
        log.info("H2 demo data: tables={}, customers={}, projects={}, purchaseOrders={}, payments={}",
                tables, customers, projects, purchaseOrders, payments);
    }

    private int executeScript(String classpath, boolean transform) {
        String sql = read(classpath);
        if (transform) {
            sql = transformMySqlToH2(sql);
        }
        sql = stripLineComments(sql);
        List<String> statements = splitStatements(sql);
        int ok = 0;
        for (String statement : statements) {
            if (statement.isBlank()) {
                continue;
            }
            try {
                jdbc.execute(statement);
                ok++;
            } catch (Exception e) {
                String preview = statement.replaceAll("\\s+", " ");
                if (preview.length() > 260) {
                    preview = preview.substring(0, 260);
                }
                throw new IllegalStateException("执行 " + classpath + " 失败: " + preview, e);
            }
        }
        return ok;
    }

    private String read(String classpath) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(classpath).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new IllegalStateException("读取脚本失败: " + classpath, e);
        }
    }

    private String transformMySqlToH2(String sql) {
        // 数据库/连接级语句去掉，所有表放入 PUBLIC
        sql = sql.replaceAll("(?im)^\\s*CREATE\\s+DATABASE.*?;\\s*$", "");
        sql = sql.replaceAll("(?im)^\\s*USE\\s+\\w+\\s*;\\s*$", "");
        sql = sql.replaceAll("(?im)^\\s*SET\\s+NAMES.*?;\\s*$", "");
        sql = sql.replaceAll("(?im)^\\s*SET\\s+FOREIGN_KEY_CHECKS.*?;\\s*$", "");

        // 表/列注释、MySQL 专有属性
        sql = sql.replaceAll("(?i)COMMENT\\s*=?\\s*'[^']*'", "");
        sql = sql.replaceAll("(?i)ON\\s+UPDATE\\s+CURRENT_TIMESTAMP", "");
        sql = sql.replaceAll("(?i)DATETIME", "TIMESTAMP");
        sql = sql.replaceAll("(?i)TINYINT", "INT");
        sql = sql.replaceAll("(?im)^\\s*version\\s+INT\\s+NOT\\s+NULL\\s+DEFAULT\\s+0\\s*,?\\s*$", "");

        // MySQL 内联索引转 H2 约束；普通 KEY 行删除
        sql = sql.replaceAll("(?i)UNIQUE\\s+KEY\\s+(\\w+)\\s*\\(([^)]*)\\)", "CONSTRAINT $1 UNIQUE ($2)");
        sql = sql.replaceAll("(?im)^\\s*KEY\\s+\\w+\\s*\\([^)]*\\)\\s*,?\\s*$", "");
        sql = sql.replaceAll("(?i)\\)\\s*ENGINE\\s*=\\s*InnoDB[^;]*;", ");");

        // 删除字段后可能出现的尾逗号
        sql = sql.replaceAll(",\\s*\\)", ")");
        return sql;
    }

    private String stripLineComments(String sql) {
        return Pattern.compile("(?m)^\\s*--.*$").matcher(sql).replaceAll("");
    }

    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (inString) {
                buf.append(ch);
                if (ch == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        buf.append(sql.charAt(++i));
                    } else {
                        inString = false;
                    }
                }
                continue;
            }
            if (ch == '\'') {
                inString = true;
                buf.append(ch);
                continue;
            }
            if (ch == ';') {
                statements.add(buf.toString().trim());
                buf.setLength(0);
                continue;
            }
            buf.append(ch);
        }
        if (!buf.toString().isBlank()) {
            statements.add(buf.toString().trim());
        }
        return statements;
    }
}
