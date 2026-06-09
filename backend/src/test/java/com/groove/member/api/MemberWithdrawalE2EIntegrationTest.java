package com.groove.member.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.auth.security.RefreshTokenCookieFactory;
import com.groove.cart.domain.Cart;
import com.groove.cart.domain.CartRepository;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderShippingInfo;
import com.groove.order.domain.OrderStatus;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 탈퇴 크로스 도메인 E2E (#78).
 *
 * <p>{@code MemberControllerTest}(단일 도메인 컨트롤러 검증)와 중복되지 않도록, 본 테스트는 탈퇴가
 * 트리거하는 모듈 간 정리(이벤트 기반)와 사후 인증 상태만 다룬다:
 * <ul>
 *   <li>정상 탈퇴 → 장바구니 삭제(cart) + 리프레시/로그인 차단(auth)</li>
 *   <li>진행 중 주문(PAID) 존재 시 409 차단 + 미탈퇴 유지</li>
 *   <li>탈퇴 회원 이메일 재가입 차단(패턴 A)</li>
 * </ul>
 *
 * <p><b>비트랜잭션</b>: cart·token 정리는 {@code MemberWithdrawnEvent} 의 {@code @TransactionalEventListener
 * (AFTER_COMMIT)} 로 수행되므로, 테스트가 {@code @Transactional} 이면 커밋이 일어나지 않아 리스너가
 * 발화하지 않는다. 실제 커밋 경로를 쓰고 {@code @BeforeEach} 에서 자식 테이블부터 정리한다(#82 교훈 —
 * 공유 DB 교차오염 방지).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("회원 탈퇴 E2E (DELETE /members/me — soft delete + 이벤트 정리)")
class MemberWithdrawalE2EIntegrationTest {

    private static final String EMAIL = "withdraw-e2e@example.com";
    private static final String RAW_PASSWORD = "P@ssw0rd!2024";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // 자식(비-cascade FK)부터 정리: refresh_token → orders → cart → member.
        refreshTokenRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    private Member persistMember() {
        return memberRepository.saveAndFlush(
                Member.register(EMAIL, passwordEncoder.encode(RAW_PASSWORD), "김철수", "01012345678"));
    }

    private static OrderShippingInfo shipping() {
        return new OrderShippingInfo("김철수", "01012345678", "서울시 강남구", "101동 202호", "06000", false);
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        // refresh 토큰은 body 가 아닌 HttpOnly 쿠키로 내려간다 (#163)
        return result.getResponse().getCookie(RefreshTokenCookieFactory.COOKIE_NAME).getValue();
    }

    @Test
    @DisplayName("정상 탈퇴 → 204, soft delete + 장바구니 삭제 + 리프레시/로그인 차단")
    void withdraw_success_cleansCartAndBlocksAuth() throws Exception {
        Member member = persistMember();
        Long memberId = member.getId();
        cartRepository.saveAndFlush(Cart.openFor(memberId));
        String refreshToken = login();
        String bearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);

        assertThat(cartRepository.findByMemberId(memberId)).isPresent();

        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        // soft delete + PII 익명화 (#170): 평문 제거, email_hash 보존
        Member withdrawn = memberRepository.findById(memberId).orElseThrow();
        assertThat(withdrawn.isWithdrawn()).isTrue();
        assertThat(withdrawn.getEmail()).startsWith("withdrawn-").endsWith("@deleted.local");
        assertThat(withdrawn.getName()).isEqualTo("탈퇴회원");
        assertThat(withdrawn.getPhone()).isNull();
        assertThat(withdrawn.getEmailHash()).isEqualTo(Member.hashEmail(EMAIL));
        // cart 정리 (AFTER_COMMIT cart 리스너)
        assertThat(cartRepository.findByMemberId(memberId)).isEmpty();

        // 리프레시 불가 (revoke + soft delete)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(RefreshTokenCookieFactory.COOKIE_NAME, refreshToken)))
                .andExpect(status().isUnauthorized());

        // 로그인 불가 (soft delete → findByEmailAndDeletedAtIsNull 비어 있음)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("진행 중 주문(PAID) 존재 → 409, 탈퇴되지 않음")
    void withdraw_withPaidOrder_blockedWith409() throws Exception {
        Member member = persistMember();
        Long memberId = member.getId();
        Order order = Order.placeForMember("ORD-E2E-WITHDRAW-1", memberId, shipping());
        order.changeStatus(OrderStatus.PAID, null);
        orderRepository.saveAndFlush(order);
        String bearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);

        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isConflict());

        assertThat(memberRepository.findById(memberId).orElseThrow().isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("탈퇴 회원 이메일 재가입 불가 → 409 (평문 제거 후에도 email_hash 점유로 차단, 패턴 A)")
    void withdrawnEmail_cannotReSignup() throws Exception {
        Member member = persistMember();
        Long memberId = member.getId();
        String bearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);

        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        // 평문 이메일은 익명화로 제거됐지만(점유는 해시로 유지)...
        assertThat(memberRepository.findById(memberId).orElseThrow().getEmail()).isNotEqualTo(EMAIL);

        // ...같은 평문 이메일 재가입은 여전히 409 로 차단된다 (email_hash 점유).
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + RAW_PASSWORD
                                + "\",\"name\":\"재가입\",\"phone\":\"01099998888\"}"))
                .andExpect(status().isConflict());
    }
}
