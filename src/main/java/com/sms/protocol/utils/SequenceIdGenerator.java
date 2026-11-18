package com.sms.protocol.utils;


import java.util.concurrent.atomic.AtomicInteger;

public class SequenceIdGenerator {

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    public static int next() {
        return SEQ.incrementAndGet();
    }
}

