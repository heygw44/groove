package com.groove.claim.domain;

/** 취소(발송 전)와 반품(발송 후)을 하나의 Claim aggregate 로 통합. */
public enum ClaimType {

    /** 발송 전 부분 취소 — REQUESTED → REFUNDED 즉시 환불. */
    CANCEL,

    /** 발송 후 반품 — 역물류 상태머신. */
    RETURN
}
