package com.groove.web;

/**
 * SPA(Vue Router history 모드) 클라이언트 라우트 패턴 — 단일 진실 소스.
 * SpaForwardConfig 가 forward:/index.html 대상으로, SecurityConfig 가 GET permitAll 대상으로 참조한다.
 */
public final class SpaRoutes {

    private SpaRoutes() {
    }

    public static final String[] PATTERNS = {
            "/login",
            "/signup",
            "/catalog",
            "/cart",
            "/checkout",
            "/mypage",
            "/me/**",
            "/albums/**",
            "/artists/**",
            "/orders/**",
            "/coupons/**",
            "/admin",
            "/admin/**"
    };
}
