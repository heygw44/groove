package com.groove.common.seed;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.application.AlbumService;
import com.groove.catalog.artist.application.ArtistService;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.genre.application.GenreService;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.label.application.LabelService;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.common.transaction.CommonTransactionConfig;
import com.groove.coupon.application.AdminCouponService;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.member.application.MemberService;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LocalDataSeeder} 통합 테스트.
 *
 * <p>시더는 {@code @Profile("local")} 이라 {@code test} 프로파일에서는 빈으로 등록되지 않는다.
 * 따라서 협력자를 주입받아 시더를 직접 생성하고 {@code run(null)} 을 호출해 실제 코드 경로
 * (서비스·BCrypt·Flyway 스키마·실 트랜잭션)를 그대로 검증한다.
 *
 * <p>시더는 {@code REQUIRES_NEW} 단일 트랜잭션으로 <b>커밋</b>하므로 테스트의 @Transactional 롤백으로는
 * 되돌릴 수 없다 — 공유 컨테이너 오염을 막기 위해 {@code FullPurchaseJourneyE2ETest} 와 동일하게
 * FK 안전 순서로 직접 정리한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class LocalDataSeederIntegrationTest {

    @Autowired private AlbumRepository albumRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private LabelRepository labelRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private MemberCouponRepository memberCouponRepository;
    @Autowired private OrderRepository orderRepository;

    @Autowired private ArtistService artistService;
    @Autowired private GenreService genreService;
    @Autowired private LabelService labelService;
    @Autowired private AlbumService albumService;
    @Autowired private MemberService memberService;
    @Autowired private AdminCouponService adminCouponService;
    @Autowired private OrderService orderService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired
    @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE)
    private TransactionTemplate txTemplate;

    private LocalDataSeeder seeder;

    @BeforeEach
    void setUp() {
        cleanAll();
        seeder = new LocalDataSeeder(albumRepository, artistService, genreService, labelService,
                albumService, memberService, memberRepository, passwordEncoder, adminCouponService,
                orderService, txTemplate);
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        // FK 안전 순서: order_items→orders(DB CASCADE) → albums → artist/genre/label → member_coupon → coupon → member.
        // member_coupon 은 본 시더가 만들지 않지만, 형제 쿠폰 테스트(@AfterEach 없음)가 남긴 행이
        // coupon(ON DELETE RESTRICT, V14)을 가리켜 couponRepository 삭제를 막을 수 있으므로 먼저 비운다.
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("카탈로그·데모 계정·유저 풀·한정 쿠폰·DELIVERED 주문을 시드한다")
    void seedsAllDemoData() {
        seeder.run(null);

        // 카탈로그: LP 12장
        assertThat(albumRepository.count()).isEqualTo(12);

        // 데모 USER — role USER, BCrypt 검증
        Member demoUser = memberRepository.findByEmailAndDeletedAtIsNull("demo@groove.dev").orElseThrow();
        assertThat(demoUser.getRole()).isEqualTo(MemberRole.USER);
        assertThat(passwordEncoder.matches("demo1234", demoUser.getPassword())).isTrue();

        // 데모 ADMIN — role ADMIN, BCrypt 검증
        Member admin = memberRepository.findByEmailAndDeletedAtIsNull("admin@groove.dev").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(passwordEncoder.matches("admin1234", admin.getPassword())).isTrue();

        // 유저 풀 30 + 데모 2 = 총 32명, 샘플 계정 존재
        assertThat(memberRepository.count()).isEqualTo(32);
        assertThat(memberRepository.findByEmailAndDeletedAtIsNull("demo01@groove.dev")).isPresent();
        assertThat(memberRepository.findByEmailAndDeletedAtIsNull("demo30@groove.dev")).isPresent();

        // 한정 쿠폰 1장 — 발급 수량 20, ACTIVE 로 조회 가능
        List<Coupon> coupons = couponRepository.findAll();
        assertThat(coupons).hasSize(1);
        assertThat(coupons.get(0).getTotalQuantity()).isEqualTo(20);
        assertThat(couponRepository.findIssuable(java.time.Instant.now(), PageRequest.of(0, 10)))
                .isNotEmpty();

        // 데모 USER 의 주문 1건이 DELIVERED (리뷰 데모 #108 즉시 동작)
        List<Order> orders = orderRepository.findByMemberId(demoUser.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("재실행해도 앨범이 있으면 건너뛰어 중복 생성하지 않는다 (멱등)")
    void isIdempotentWhenAlreadySeeded() {
        seeder.run(null);
        long albumsAfterFirst = albumRepository.count();
        long membersAfterFirst = memberRepository.count();
        long couponsAfterFirst = couponRepository.count();

        seeder.run(null); // 2회차 — 앨범 카운트 가드로 전체 skip

        assertThat(albumRepository.count()).isEqualTo(albumsAfterFirst);
        assertThat(memberRepository.count()).isEqualTo(membersAfterFirst);
        assertThat(couponRepository.count()).isEqualTo(couponsAfterFirst);
    }
}
