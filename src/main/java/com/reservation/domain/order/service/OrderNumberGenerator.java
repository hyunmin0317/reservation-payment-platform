package com.reservation.domain.order.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong sequence = new AtomicLong(0);

    public String generate() {
        String date = LocalDate.now().format(DATE_FORMAT);
        long seq = sequence.incrementAndGet();
        return String.format("ORD-%s-%06d", date, seq);
    }
}