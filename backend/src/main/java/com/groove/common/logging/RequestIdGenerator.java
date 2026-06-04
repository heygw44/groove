package com.groove.common.logging;

import java.util.UUID;

@FunctionalInterface
public interface RequestIdGenerator {

    String generate();

    static RequestIdGenerator uuid() {
        return () -> UUID.randomUUID().toString();
    }
}
