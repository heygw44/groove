package com.groove.common.seed;

import com.groove.admin.api.dto.AdminCouponCreateRequest;
import com.groove.catalog.album.application.AlbumCommand;
import com.groove.catalog.album.application.AlbumService;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.application.ArtistCommand;
import com.groove.catalog.artist.application.ArtistService;
import com.groove.catalog.genre.application.GenreCommand;
import com.groove.catalog.genre.application.GenreService;
import com.groove.catalog.label.application.LabelCommand;
import com.groove.catalog.label.application.LabelService;
import com.groove.common.transaction.CommonTransactionConfig;
import com.groove.coupon.application.AdminCouponService;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.member.application.MemberService;
import com.groove.member.application.SignupCommand;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.security.EmailHasher;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderItemRequest;
import com.groove.order.api.dto.ShippingInfoRequest;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 로컬 데모 자동 시드. local 프로파일 기동 시 샘플 카탈로그·데모 계정·유저 풀·한정 쿠폰·DELIVERED 주문을 생성한다.
 * @Profile("local") 로만 등록되며 run 진입 시 활성 프로파일을 한 번 더 확인한다. 앨범이 한 장이라도 있으면 전체를
 * 건너뛰고, 시드 본체는 단일 트랜잭션으로 커밋한다. 생성은 가능한 한 application service 를 경유하고, 관리자 role
 * 지정과 주문 상태 전진만 도메인/리포지터리를 직접 사용한다.
 */
@Component
@Profile("local")
public class LocalDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);

    // 계정 식별자는 DemoAccounts, 비밀번호는 시드 생성 전용으로 여기 둔다.
    private static final String DEMO_USER_PASSWORD = "demo1234";
    private static final String ADMIN_PASSWORD = "admin1234";
    /** 한정 쿠폰 발급 수량. */
    private static final int COUPON_TOTAL_QUANTITY = 20;

    private final AlbumRepository albumRepository;
    private final ArtistService artistService;
    private final GenreService genreService;
    private final LabelService labelService;
    private final AlbumService albumService;
    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailHasher emailHasher;
    private final AdminCouponService adminCouponService;
    private final OrderService orderService;
    private final TransactionTemplate txTemplate;
    private final Environment environment;

    public LocalDataSeeder(AlbumRepository albumRepository,
                           ArtistService artistService,
                           GenreService genreService,
                           LabelService labelService,
                           AlbumService albumService,
                           MemberService memberService,
                           MemberRepository memberRepository,
                           PasswordEncoder passwordEncoder,
                           EmailHasher emailHasher,
                           AdminCouponService adminCouponService,
                           OrderService orderService,
                           @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE)
                           TransactionTemplate txTemplate,
                           Environment environment) {
        this.albumRepository = albumRepository;
        this.artistService = artistService;
        this.genreService = genreService;
        this.labelService = labelService;
        this.albumService = albumService;
        this.memberService = memberService;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailHasher = emailHasher;
        this.adminCouponService = adminCouponService;
        this.orderService = orderService;
        this.txTemplate = txTemplate;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 활성 프로파일에 'local' 이 없으면 시드를 건너뛴다.
        if (!environment.matchesProfiles("local")) {
            log.warn("[seed] 활성 프로파일에 'local' 이 없어 데모 시드를 건너뜁니다 (active={})",
                    String.join(",", environment.getActiveProfiles()));
            return;
        }
        if (albumRepository.count() > 0) {
            log.info("[seed] 기존 데이터 감지 — 로컬 데모 시드 건너뜀");
            return;
        }
        log.info("[seed] 로컬 데모 데이터 시드 시작");
        txTemplate.executeWithoutResult(status -> seedAll());
        log.info("[seed] 완료: 앨범 12, 데모계정 2(USER/ADMIN), 유저풀 {}, 한정쿠폰 1, DELIVERED 주문 1",
                DemoAccounts.USER_POOL_SIZE);
    }

    /** 시드 본체 — 단일 트랜잭션 안에서 호출된다. */
    private void seedAll() {
        List<Album> albums = seedCatalog();
        Member demoUser = seedDemoAccounts();
        seedUserPool();
        seedLimitedCoupon();
        seedDeliveredOrder(demoUser, albums);
    }

    /** 장르·레이블·아티스트 + LP 12장 생성. */
    private List<Album> seedCatalog() {
        Long rock = genreService.create(new GenreCommand("Rock")).getId();
        Long jazz = genreService.create(new GenreCommand("Jazz")).getId();
        Long pop = genreService.create(new GenreCommand("Pop")).getId();
        Long electronic = genreService.create(new GenreCommand("Electronic")).getId();
        Long hipHop = genreService.create(new GenreCommand("Hip-Hop")).getId();

        Long blueNote = labelService.create(new LabelCommand("Blue Note")).getId();
        Long motown = labelService.create(new LabelCommand("Motown")).getId();
        Long subPop = labelService.create(new LabelCommand("Sub Pop")).getId();
        Long verve = labelService.create(new LabelCommand("Verve")).getId();

        Long velvetEchoes = artist("The Velvet Echoes", "몽환적 사운드의 4인조 인디 록 밴드");
        Long milesQuartet = artist("Miles Foster Quartet", "정통 하드밥 재즈 콰르텟");
        Long neonDistrict = artist("Neon District", "신스웨이브/일렉트로닉 듀오");
        Long ariaSol = artist("Aria Sol", "감성 팝 싱어송라이터");
        Long gravelGold = artist("Gravel & Gold", "사우스 힙합 크루");

        List<Album> albums = new ArrayList<>(12);
        albums.add(album("Midnight Reverie", velvetEchoes, rock, subPop, 2019, 32_000L, 25, false));
        albums.add(album("Echo Chamber", velvetEchoes, rock, subPop, 2021, 35_000L, 12, true));
        albums.add(album("Blue Modes", milesQuartet, jazz, blueNote, 1998, 41_000L, 8, false));
        albums.add(album("After Hours Session", milesQuartet, jazz, blueNote, 2003, 38_000L, 5, true));
        albums.add(album("Verve Standards Vol. 1", milesQuartet, jazz, verve, 2010, 36_000L, 18, false));
        albums.add(album("Neon Skyline", neonDistrict, electronic, null, 2022, 29_000L, 40, false));
        albums.add(album("Analog Dreams", neonDistrict, electronic, null, 2020, 31_000L, 15, true));
        albums.add(album("Golden Hour", ariaSol, pop, motown, 2023, 27_000L, 50, false));
        albums.add(album("Paper Hearts", ariaSol, pop, motown, 2021, 26_000L, 30, false));
        albums.add(album("Heavy Rotation", gravelGold, hipHop, null, 2022, 33_000L, 20, false));
        albums.add(album("Concrete Bloom", gravelGold, hipHop, null, 2024, 34_000L, 10, true));
        albums.add(album("Live at the Riverside", velvetEchoes, rock, verve, 2018, 45_000L, 6, true));
        return albums;
    }

    private Long artist(String name, String description) {
        return artistService.create(new ArtistCommand(name, description)).getId();
    }

    private Album album(String title, Long artistId, Long genreId, Long labelId,
                        int releaseYear, long price, int stock, boolean limited) {
        String cover = "https://placehold.co/300x300?text="
                + URLEncoder.encode(title, StandardCharsets.UTF_8);
        AlbumCommand command = new AlbumCommand(
                title, artistId, genreId, labelId, (short) releaseYear,
                AlbumFormat.LP_12, price, AlbumStatus.SELLING, limited, cover,
                title + " — 데모 시드 LP");
        return albumService.create(command, stock);
    }

    /** 데모 USER(서비스 경유) + 데모 ADMIN(role 지정 위해 도메인 팩토리 직접). */
    private Member seedDemoAccounts() {
        Member demoUser = memberService.signup(
                new SignupCommand(DemoAccounts.DEMO_USER_EMAIL, DEMO_USER_PASSWORD, "데모유저", "01000000000"));
        // 점유 해시는 EmailHasher 로 계산해 주입한다.
        Member admin = Member.registerAdmin(
                DemoAccounts.ADMIN_EMAIL, emailHasher.hash(DemoAccounts.ADMIN_EMAIL),
                passwordEncoder.encode(ADMIN_PASSWORD), "데모관리자", "01000000001");
        memberRepository.saveAndFlush(admin);
        return demoUser;
    }

    /** 유저 풀 (demo01@ … demo30@, 공통 비번). */
    private void seedUserPool() {
        for (int i = 1; i <= DemoAccounts.USER_POOL_SIZE; i++) {
            String email = DemoAccounts.poolEmail(i);
            String phone = String.format("0102%07d", i); // 11자리 숫자
            memberService.signup(new SignupCommand(
                    email, DEMO_USER_PASSWORD, String.format("데모회원%02d", i), phone));
        }
    }

    /** 한정 수량 선착순 쿠폰 1장. */
    private void seedLimitedCoupon() {
        Instant now = Instant.now();
        adminCouponService.create(new AdminCouponCreateRequest(
                "데모 선착순 쿠폰 (5,000원)",
                CouponDiscountType.FIXED_AMOUNT,
                5_000L,
                null,                       // maxDiscountAmount
                0L,                         // minOrderAmount
                COUPON_TOTAL_QUANTITY,      // totalQuantity
                1,                          // perMemberLimit
                now.minus(Duration.ofHours(1)),
                now.plus(Duration.ofDays(30))));
    }

    /**
     * 데모 USER 의 DELIVERED 주문 1건. place 로 PENDING 주문을 만든 뒤 managed 엔티티를
     * PENDING→PAID→PREPARING→SHIPPED→DELIVERED 로 직접 전진시킨다(결제/배송 행은 만들지 않음).
     */
    private void seedDeliveredOrder(Member demoUser, List<Album> albums) {
        Album target = albums.get(0);
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(target.getId(), 1)),
                null, // 회원 주문
                new ShippingInfoRequest("데모유저", "01000000000",
                        "서울특별시 강남구 데모로 123", "그루브빌딩 4층", "06000", false),
                null); // 쿠폰 미적용
        Order order = orderService.place(demoUser.getId(), request);
        Instant now = Instant.now();
        order.changeStatus(OrderStatus.PAID, "데모 시드", now);
        order.changeStatus(OrderStatus.PREPARING, "데모 시드", now);
        order.changeStatus(OrderStatus.SHIPPED, "데모 시드", now);
        order.changeStatus(OrderStatus.DELIVERED, "데모 시드", now);
    }
}
