package com.groove.global.init;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.init.SeedCatalog.AlbumSeed;
import com.groove.inventory.service.StockService;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** local 프로파일 전용 더미 데이터 시더. 상품 개수/회원 이메일/장르 이름을 기준으로 멱등하게 동작한다. */
@Slf4j
@Component
@Profile("local")
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

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final GenreRepository genreRepository;
	private final LabelRepository labelRepository;
	private final ArtistRepository artistRepository;
	private final ProductRepository productRepository;
	private final StockService stockService;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seedMembers();
		seedCatalog();
	}

	private void seedMembers() {
		createMemberIfAbsent(ADMIN_EMAIL,
				() -> Member.createAdmin(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), ADMIN_NICKNAME));
		createMemberIfAbsent(USER1_EMAIL,
				() -> Member.create(USER1_EMAIL, passwordEncoder.encode(USER_PASSWORD), USER1_NICKNAME));
		createMemberIfAbsent(USER2_EMAIL,
				() -> Member.create(USER2_EMAIL, passwordEncoder.encode(USER_PASSWORD), USER2_NICKNAME));
	}

	private void createMemberIfAbsent(String email, Supplier<Member> factory) {
		if (memberRepository.existsByEmail(email)) {
			log.info("더미 회원 계정이 이미 있어 건너뛴다: {}", email);
			return;
		}
		memberRepository.save(factory.get());
		log.info("더미 회원 계정을 생성했다: {}", email);
	}

	private void seedCatalog() {
		if (productRepository.count() > 0) {
			log.info("상품 데이터가 이미 있어 시딩을 건너뛴다");
			return;
		}

		List<Genre> genres = seedGenres();
		List<Label> labels = labelRepository.saveAll(SeedCatalog.LABELS.stream()
				.map(seed -> Label.create(seed.name(), seed.country()))
				.toList());
		List<Artist> artists = artistRepository.saveAll(SeedCatalog.ARTISTS.stream()
				.map(seed -> Artist.create(seed.name(), seed.nameEn(), seed.description()))
				.toList());

		List<AlbumSeed> albums = SeedCatalog.ALBUMS;
		for (int i = 0; i < albums.size(); i++) {
			AlbumSeed seed = albums.get(i);
			Artist artist = artists.get(seed.artistIndex());
			Label label = seed.labelIndex() != null ? labels.get(seed.labelIndex()) : null;

			Product product = Product.create(seed.title(), artist, label, seed.releaseDate(), seed.pressingInfo(),
					seed.colorVariant(), BigDecimal.valueOf(seed.price()), seed.description());
			seed.genreIndexes().forEach(genreIndex -> product.addGenre(genres.get(genreIndex)));
			addImages(product, seed.title(), i % 2 == 0);

			productRepository.save(product);
			stockService.create(product, seed.stock());
		}

		log.info("더미 카탈로그를 시딩했다: 장르 {}개, 레이블 {}개, 아티스트 {}개, 앨범 {}개",
				genres.size(), labels.size(), artists.size(), albums.size());
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
