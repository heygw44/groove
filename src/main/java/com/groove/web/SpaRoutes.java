package com.groove.web;

/**
 * SPA(Vue Router history 모드) 클라이언트 라우트 패턴 — 단일 진실 소스.
 *
 * <p>두 곳이 이 동일 배열을 참조해 sync 를 보장한다:
 * <ul>
 *   <li>{@link SpaForwardConfig} — 이 경로들을 {@code forward:/index.html} 로 보낸다(직접 진입·새로고침 fallback).</li>
 *   <li>{@code SecurityConfig} — 이 경로들의 GET 을 permitAll 로 연다(HTML 셸 공개).</li>
 * </ul>
 *
 * <p>여기 있는 건 <b>클라이언트 라우트</b>일 뿐이며 HTML 셸만 공개한다. 실제 데이터 인가는
 * 여전히 {@code /api/v1/**} 토큰 검증이 담당한다(예: {@code /admin} 셸은 누구나 받지만
 * {@code /api/v1/admin/**} 는 ADMIN 만). 백엔드 경로({@code /api}, {@code /actuator}, {@code /error})와
 * 정적 에셋({@code /assets/**})은 의도적으로 포함하지 않는다.
 *
 * <p><b>유지보수 의무:</b> {@code frontend/src/router/index.js} 의 Vue Router 에 top-level 라우트를
 * 추가하면 여기에도 대응 패턴을 추가해야 한다. 누락하면 in-app 이동은 되지만 <b>새로고침·딥링크에서
 * 404/401</b> 이 되며, 이는 백엔드 테스트로는 잡히지 않는다. 또한 이 배열은 <b>forward 대상이자
 * GET permitAll 표면</b>을 겸하므로, 항목 추가는 곧 공개 GET 범위 확대를 뜻한다(현재 모든 컨트롤러가
 * {@code /api/v1} prefix 라 bare 경로와 겹치지 않아 실유출은 없다).
 */
public final class SpaRoutes {

    private SpaRoutes() {
    }

    public static final String[] PATTERNS = {
            "/login",
            "/signup",
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
