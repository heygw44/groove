package com.groove.global.init;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.init.SeedCatalog.AlbumSeed;
import com.groove.inventory.service.StockService;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.review.entity.Review;
import com.groove.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 더미 데이터 시더. 회원은 이메일, 장르/레이블/아티스트는 이름, 앨범은 (title, artistId) 기준으로 파트별 멱등하게 동작한다.
 * local 외에 seed 프로파일에서도 뜬다 — 운영에 카탈로그만 적재하기 위해서다. 취향·위시·구매 신호는
 * local 전용인 {@link LocalSignalSeeder} 가 없으면 건너뛴다.
 */
@Slf4j
@Component
@Profile({"local", "seed"})
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

	private static final String ADMIN_EMAIL = "admin@groove.com";
	private static final String ADMIN_PASSWORD = "admin1234!";
	private static final String ADMIN_NICKNAME = "관리자";

	private static final String USER1_EMAIL = "user1@groove.com";
	private static final String USER1_NICKNAME = "그루브1";
	private static final String USER2_EMAIL = "user2@groove.com";
	private static final String USER2_NICKNAME = "그루브2";
	private static final String USER_PASSWORD = "user1234!";

	private static final String IMAGE_BASE_URL = "https://picsum.photos/seed/";

	private static final List<String> EXTRA_PRESSING_COUNTRIES = List.of("Japan", "UK", "Germany");
	private static final List<String> EXTRA_PRESSING_COLORS = List.of("Clear", "Translucent Blue", "Red");

	private static final List<String> REVIEW_TITLES = List.of(
			"자주 듣게 되는 앨범", "믹싱이 훌륭합니다", "소장 가치 있음", "기대 이상이었어요", "재구매 의사 있습니다");

	private static final List<String> REVIEW_CONTENTS = List.of(
			"판 상태도 좋고 배송도 빨랐습니다.",
			"음질이 생각보다 훨씬 좋네요.",
			"자켓 디자인이 마음에 듭니다.",
			"플레이어에 걸어두고 매일 듣고 있어요.",
			"선물용으로 구매했는데 반응이 좋았습니다.");

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final GenreRepository genreRepository;
	private final LabelRepository labelRepository;
	private final ArtistRepository artistRepository;
	private final AlbumRepository albumRepository;
	private final ProductRepository productRepository;
	private final StockService stockService;
	private final ReviewRepository reviewRepository;
	private final ObjectProvider<LocalSignalSeeder> localSignalSeederProvider;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seedMembers();
		seedCatalog();
		seedReviews();

		LocalSignalSeeder localSignalSeeder = localSignalSeederProvider.getIfAvailable();
		if (localSignalSeeder != null) {
			localSignalSeeder.seed(demoMembers());
		}
	}

	private void seedMembers() {
		createMemberIfAbsent(ADMIN_EMAIL,
				() -> Member.createAdmin(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), ADMIN_NICKNAME));
		createMemberIfAbsent(USER1_EMAIL,
				() -> Member.create(USER1_EMAIL, passwordEncoder.encode(USER_PASSWORD), USER1_NICKNAME));
		createMemberIfAbsent(USER2_EMAIL,
				() -> Member.create(USER2_EMAIL, passwordEncoder.encode(USER_PASSWORD), USER2_NICKNAME));
	}

	/** 조회 로그는 이 둘에게만 심는다. 취향 클러스터 회원에 심으면 추천 후보에서 빠져 품질 측정이 흔들린다. */
	private List<Member> demoMembers() {
		return Stream.of(USER1_EMAIL, USER2_EMAIL)
				.map(memberRepository::findByEmail)
				.flatMap(Optional::stream)
				.toList();
	}

	private void createMemberIfAbsent(String email, Supplier<Member> factory) {
		if (memberRepository.existsByEmail(email)) {
			log.info("더미 회원 계정이 이미 있어 건너뛴다: {}", email);
			return;
		}
		memberRepository.save(factory.get());
		log.info("더미 회원 계정을 생성했다: {}", email);
	}

	/** 레이블/아티스트는 이름, 앨범은 (title, artistId) 로 find-or-create 해 파트별로 멱등하게 동작한다. */
	private void seedCatalog() {
		List<Genre> genres = seedGenres();
		List<Label> labels = seedLabels();
		List<Artist> artists = seedArtists();

		List<AlbumSeed> albumSeeds = SeedAlbums.ALBUMS;
		int pressingCount = 0;
		for (int i = 0; i < albumSeeds.size(); i++) {
			AlbumSeed seed = albumSeeds.get(i);
			Artist artist = artists.get(seed.artistIndex());
			Label label = seed.labelIndex() != null ? labels.get(seed.labelIndex()) : null;
			Integer originalReleaseYear = seed.releaseDate() == null ? null : seed.releaseDate().getYear();

			Album album = albumRepository.findFirstByTitleAndArtistIdOrderByIdAsc(seed.title(), artist.getId())
					.orElseGet(() -> albumRepository.save(Album.create(seed.title(), artist, originalReleaseYear)));
			if (productRepository.existsByAlbumId(album.getId())) {
				continue;
			}

			int variantCount = pressingCountFor(i);
			for (int variant = 0; variant < variantCount; variant++) {
				Product product = createPressing(album, artist, label, seed, variant);
				seed.genreIndexes().forEach(genreIndex -> product.addGenre(genres.get(genreIndex)));
				addImages(product, seed.title() + variant, i % 2 == 0);

				productRepository.save(product);
				stockService.create(product, seed.stock());
				pressingCount++;
			}
		}

		log.info("더미 카탈로그를 시딩했다: 장르 {}개, 레이블 {}개, 아티스트 {}개, 앨범 {}개, 신규 프레싱 {}개",
				genres.size(), labels.size(), artists.size(), albumSeeds.size(), pressingCount);
	}

	private List<Label> seedLabels() {
		return SeedCatalog.LABELS.stream()
				.map(seed -> labelRepository.findFirstByNameOrderByIdAsc(seed.name())
						.orElseGet(() -> labelRepository.save(Label.create(seed.name(), seed.country()))))
				.toList();
	}

	private List<Artist> seedArtists() {
		return SeedCatalog.ARTISTS.stream()
				.map(seed -> artistRepository.findFirstByNameOrderByIdAsc(seed.name())
						.orElseGet(() -> artistRepository.save(
								Artist.create(seed.name(), seed.nameEn(), seed.description()))))
				.toList();
	}

	/** 일부 앨범(5의 배수 인덱스)은 국가/연도/컬러가 다른 프레싱을 2~3개 만들어 "다른 프레싱" 섹션을 채운다. */
	int pressingCountFor(int albumIndex) {
		if (albumIndex % 10 == 0) {
			return 3;
		}
		if (albumIndex % 5 == 0) {
			return 2;
		}
		return 1;
	}

	private Product createPressing(Album album, Artist artist, Label label, AlbumSeed seed, int variantIndex) {
		if (variantIndex == 0) {
			Integer pressingYear = seed.releaseDate() == null ? null : seed.releaseDate().getYear();
			return Product.create(album, seed.title(), artist, label, seed.releaseDate(), seed.pressingInfo(),
					seed.colorVariant(), "US", pressingYear, null, null, EditionType.STANDARD,
					BigDecimal.valueOf(seed.price()), seed.description());
		}
		String country = EXTRA_PRESSING_COUNTRIES.get((variantIndex - 1) % EXTRA_PRESSING_COUNTRIES.size());
		String colorVariant = EXTRA_PRESSING_COLORS.get((variantIndex - 1) % EXTRA_PRESSING_COLORS.size());
		LocalDate releaseDate = seed.releaseDate() == null ? null : seed.releaseDate().plusYears(variantIndex);
		String title = seed.title() + " (" + country + " Reissue)";
		return Product.create(album, title, artist, label, releaseDate, seed.pressingInfo(), colorVariant, country,
				releaseDate == null ? null : releaseDate.getYear(), null, null, EditionType.REISSUE,
				BigDecimal.valueOf(seed.price()), seed.description());
	}

	private void seedReviews() {
		if (reviewRepository.count() > 0) {
			log.info("리뷰 데이터가 이미 있어 시딩을 건너뛴다");
			return;
		}

		Member user1 = memberRepository.findByEmail(USER1_EMAIL).orElseThrow();
		Member user2 = memberRepository.findByEmail(USER2_EMAIL).orElseThrow();
		List<Member> reviewers = List.of(user1, user2);
		List<Product> products = productRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

		List<Review> reviews = new ArrayList<>();
		for (int productIndex = 0; productIndex < products.size(); productIndex++) {
			Product product = products.get(productIndex);
			for (int memberIndex = 0; memberIndex < reviewers.size(); memberIndex++) {
				Member reviewer = reviewers.get(memberIndex);
				int rating = (productIndex * 7 + memberIndex * 3) % 5 + 1;
				int phraseIndex = (productIndex + memberIndex) % REVIEW_TITLES.size();
				reviews.add(Review.create(product, reviewer, rating, REVIEW_TITLES.get(phraseIndex),
						REVIEW_CONTENTS.get(phraseIndex)));
			}
		}
		reviewRepository.saveAll(reviews);
		products.forEach(product -> productRepository.refreshReviewStats(product.getId()));

		log.info("더미 리뷰를 시딩했다: {}건", reviews.size());
	}

	private List<Genre> seedGenres() {
		return SeedCatalog.GENRES.stream()
				.map(name -> genreRepository.findByName(name).orElseGet(() -> genreRepository.save(Genre.create(name))))
				.toList();
	}

	private void addImages(Product product, String title, boolean includeBackImage) {
		String slug = slugify(title);
		product.addImage(IMAGE_BASE_URL + slug + "/600", 0);
		if (includeBackImage) {
			product.addImage(IMAGE_BASE_URL + slug + "-back/600", 1);
		}
	}

	private String slugify(String title) {
		String normalized = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
		return normalized.replaceAll("^-+|-+$", "");
	}
}
