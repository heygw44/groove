package com.groove.claim.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.claim.domain.ClaimRepository;
import com.groove.claim.domain.ClaimStatus;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.payment.domain.PaymentRepository;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.support.ClaimFixtures;
import com.groove.support.MemberFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 반품 접수 API 통합 — MockMvc 로 실 필터·서비스·DB 를 모두 거친다. 탈퇴 회원의 만료 전 access 토큰이
 * 서비스단 활성 가드에 막혀 보호 엔드포인트에 도달하지 못함을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("반품 접수 API 통합 — 탈퇴 회원 토큰 차단 (#269)")
class ClaimApiIntegrationTest {

    private static final long UNIT_PRICE = 15_000L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ShippingRepository shippingRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private LabelRepository labelRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private Long memberId;
    private Long albumId;
    private String userBearer;
    private int seq;

    @BeforeEach
    void setUp() {
        clearAll();
        Member member = memberRepository.saveAndFlush(
                MemberFixtures.register("claim-api@example.com", "$2a$10$dummy", "회원", "01000000001"));
        memberId = member.getId();
        String uniq = "-" + System.nanoTime();
        Artist artist = artistRepository.saveAndFlush(Artist.create("Artist" + uniq, null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock" + uniq));
        Label label = labelRepository.saveAndFlush(Label.create("Label" + uniq));
        albumId = albumRepository.saveAndFlush(Album.create("Album", artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, UNIT_PRICE, 100, AlbumStatus.SELLING, false, null, null)).getId();
        userBearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);
    }

    @AfterEach
    void tearDown() {
        clearAll();
    }

    private void clearAll() {
        // 자식(FK 참조)부터 정리 — refresh_token/claim/shipping/payment → orders/album → member 순.
        refreshTokenRepository.deleteAllInBatch();
        claimRepository.deleteAllInBatch();
        shippingRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    /** 배송완료(DELIVERED) 회원 주문 + PAID 결제 + delivered_at 배송행을 영속화한다(반품 접수 자격). */
    private Order persistDeliveredOrder(int qty) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        return ClaimFixtures.persistDeliveredOrder(orderRepository, paymentRepository, shippingRepository,
                album, memberId, qty, (++seq) + "-" + System.nanoTime());
    }

    private String requestBody(Order order, int quantity) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("orderItemId", order.getItems().get(0).getId());
        line.put("quantity", quantity);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNumber", order.getOrderNumber());
        body.put("reason", "단순 변심");
        body.put("items", List.of(line));
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("활성 회원 → 배송완료 주문 반품 접수 → 201")
    void request_activeMember_returns201() throws Exception {
        Order order = persistDeliveredOrder(2);

        mockMvc.perform(post("/api/v1/claims")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(order, 1)))
                .andExpect(status().isCreated());

        // 공유 DB 오염에 견디도록 전역 count() 대신 이 주문에 묶인 반품으로 스코프해 단언한다.
        assertThat(claimRepository.findByOrder_IdAndStatusNot(order.getId(), ClaimStatus.REJECTED)).hasSize(1);
    }

    @Test
    @DisplayName("탈퇴 회원이 만료 전 토큰으로 반품 접수 시도 → 404 MEMBER_NOT_FOUND, 접수 안 됨 (#269)")
    void request_withdrawnMember_returns404() throws Exception {
        Order order = persistDeliveredOrder(2);
        // 탈퇴(soft delete) — userBearer 는 탈퇴 전 발급분이라 서명·만료상 여전히 유효(필터는 통과).
        Member member = memberRepository.findById(memberId).orElseThrow();
        member.withdraw(Instant.now());
        memberRepository.saveAndFlush(member);

        mockMvc.perform(post("/api/v1/claims")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(order, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        assertThat(claimRepository.findByOrder_IdAndStatusNot(order.getId(), ClaimStatus.REJECTED)).isEmpty();
    }
}
