package com.zlvolt.common;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MapUtils {

    private MapUtils() {
    }

    public static Map<String, Object> camel(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((key, value) -> out.put(toCamel(key), value));
        return out;
    }

    public static List<Map<String, Object>> camel(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows != null) {
            rows.forEach(row -> out.add(camel(row)));
        }
        return out;
    }

    public static String toCamel(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String lower = name.toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char ch : lower.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(ch));
                upper = false;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static long longVal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }

    public static BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    public static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : Long.parseLong(text);
    }

    public static BigDecimal toDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : new BigDecimal(text);
    }

    public static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    public static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
