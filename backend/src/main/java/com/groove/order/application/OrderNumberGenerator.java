package com.groove.order.application;

/**
 * orderNumber 발급기 — 형식 ORD-YYYYMMDD-XXXXXX. 운영에서는 RandomOrderNumberGenerator 가 등록된다.
 */
public interface OrderNumberGenerator {

    /** 새 orderNumber 를 1건 생성한다. */
    String generate();
}
