package com.groove.common.seed;

/** 로컬 데모 시드가 생성하는 계정 식별자 단일 출처. LocalDataSeeder·ProductionSeedGuard 가 공유한다. */
public final class DemoAccounts {

    /** 데모 일반 회원 (role USER). */
    public static final String DEMO_USER_EMAIL = "demo@groove.dev";

    /** 데모 관리자 (role ADMIN). */
    public static final String ADMIN_EMAIL = "admin@groove.dev";

    /** 유저 풀 크기 (demo01@ … demo{N}@). */
    public static final int USER_POOL_SIZE = 30;

    /** 유저 풀 이메일 — demo01@groove.dev … demo{USER_POOL_SIZE}@groove.dev. */
    public static String poolEmail(int index) {
        return String.format("demo%02d@groove.dev", index);
    }

    private DemoAccounts() {
    }
}
