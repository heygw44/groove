package com.groove.common.seed;

/**
 * 로컬 데모 시드가 생성하는 계정 식별자 단일 출처 (이슈 #128).
 *
 * <p>{@link LocalDataSeeder}(데모 계정 생성)와 {@link ProductionSeedGuard}(운영 유입 감지)가 같은
 * 식별자를 공유하도록 한곳에 모은다 — 한쪽만 바뀌면 가드가 무력화되는 drift 를 막는다. 시드는 단일
 * 트랜잭션 all-or-nothing 이므로 이 두 대표 계정의 존재 여부로 전체 시드 유무를 판정할 수 있다.
 */
public final class DemoAccounts {

    /** 데모 일반 회원 (role USER). */
    public static final String DEMO_USER_EMAIL = "demo@groove.dev";

    /** 데모 관리자 (role ADMIN, 알려진 비밀번호 — 운영 DB 유입 시 가장 위험). */
    public static final String ADMIN_EMAIL = "admin@groove.dev";

    /**
     * 유저 풀 크기 (demo01@ … demo{N}@). 본래 프론트 "쿠폰 동시성 라이브 데모" 전용이었으나 그 데모는
     * 은퇴했다(동시성·부하 검증은 {@code loadtest/} k6 + 동시성 테스트로 일원화). 현재 이 풀을 소비하는
     * 앱 기능은 없으며, 풀 시드 제거는 ProductionSeedGuard·시더 테스트 동반 수정이 필요해 후속으로 둔다.
     */
    public static final int USER_POOL_SIZE = 30;

    /** 유저 풀 이메일 — {@code demo01@groove.dev} … {@code demo{USER_POOL_SIZE}@groove.dev}. */
    public static String poolEmail(int index) {
        return String.format("demo%02d@groove.dev", index);
    }

    private DemoAccounts() {
    }
}
