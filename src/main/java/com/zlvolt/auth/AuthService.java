package com.zlvolt.auth;

import com.zlvolt.common.BizException;
import com.zlvolt.common.MapUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final JdbcTemplate jdbc;
    private final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> login(String username, String password) {
        String sql = """
                SELECT u.id, u.username, u.password_hash, u.real_name, u.dept_id,
                       d.name AS dept_name, r.code AS role_code, r.name AS role_name
                FROM sys_user u
                LEFT JOIN sys_dept d ON d.id = u.dept_id
                LEFT JOIN sys_user_role ur ON ur.user_id = u.id
                LEFT JOIN sys_role r ON r.id = ur.role_id
                WHERE u.username = ? AND u.status = 'ACTIVE'
                LIMIT 1
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, username);
        if (rows.isEmpty()) {
            throw new BizException(40101, "用户名或密码错误");
        }
        Map<String, Object> row = MapUtils.camel(rows.get(0));
        String stored = MapUtils.str(row, "passwordHash");
        if (stored != null && stored.startsWith("{noop}")) {
            stored = stored.substring(6);
        }
        if (stored == null || !stored.equals(password)) {
            throw new BizException(40101, "用户名或密码错误");
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", row.get("id"));
        user.put("username", row.get("username"));
        user.put("realName", row.get("realName"));
        user.put("deptName", row.get("deptName"));
        user.put("role", row.get("roleCode"));
        user.put("roleName", row.get("roleName"));
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, user);
        return MapUtils.map("token", token, "user", user);
    }

    public Map<String, Object> require(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(40101, "未登录或登录已过期");
        }
        Map<String, Object> user = sessions.get(token);
        if (user == null) {
            throw new BizException(40101, "未登录或登录已过期");
        }
        return user;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public String tokenOf(String authorization) {
        if (authorization == null) {
            return null;
        }
        String prefix = "Bearer ";
        return authorization.startsWith(prefix) ? authorization.substring(prefix.length()).trim() : authorization.trim();
    }
}
