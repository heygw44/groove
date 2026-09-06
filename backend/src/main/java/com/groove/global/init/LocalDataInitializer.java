package com.groove.global.init;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.init.SeedCatalog.AlbumSeed;
import com.groove.inventory.service.StockService;
import com.groove.member.entity.Member;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카탈로그(장르/레이블/아티스트/앨범/프레싱) 시더. 장르/레이블/아티스트는 이름, 앨범은 (title, artistId) 기준으로
 * 파트별 멱등하게 동작해 local/seed 양쪽에서 안전하게 반복 실행된다. 데모 계정·리뷰는 local 전용인
 * {@link LocalDemoDataSeeder}, 취향·위시·구매 신호는 local 전용인 {@link LocalSignalSeeder} 가 빈으로
 * 존재할 때만 돈다 — 운영(seed)에는 카탈로그만 적재해야 하기 때문이다.
 */
@Slf4j
@Component
@Profile({"local", "seed"})
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

	private static final String IMAGE_BASE_URL = "https://picsum.photos/seed/";

	private static final List<String> EXTRA_PRESSING_COUNTRIES = List.of("Japan", "UK", "Germany");
	private static final List<String> EXTRA_PRESSING_COLORS = List.of("Clear", "Translucent Blue", "Red");

	private final GenreRepository genreRepository;
	private final LabelRepository labelRepository;
	private final ArtistRepository artistRepository;
	private final AlbumRepository albumRepository;
	private final ProductRepository productRepository;
	private final StockService stockService;
	private final ObjectProvider<LocalDemoDataSeeder> localDemoDataSeederProvider;
	private final ObjectProvider<LocalSignalSeeder> localSignalSeederProvider;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seedCatalog();

		LocalDemoDataSeeder demoDataSeeder = localDemoDataSeederProvider.getIfAvailable();
		List<Member> demoMembers = demoDataSeeder != null ? demoDataSeeder.seed() : List.of();

		LocalSignalSeeder localSignalSeeder = localSignalSeederProvider.getIfAvailable();
		if (localSignalSeeder != null) {
			localSignalSeeder.seed(demoMembers);
		}
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
