package com.zlvolt.common;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CodeGen {

    private final Map<String, AtomicInteger> sequences = new ConcurrentHashMap<>();

    public String next(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = sequences.computeIfAbsent(prefix + date, k -> new AtomicInteger(0)).incrementAndGet();
        return prefix + "-" + date + "-" + String.format("%06d", seq);
    }
}
