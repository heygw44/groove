package com.groove.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.ProductFixture;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductGenre;
import com.groove.product.entity.ProductImage;
import com.groove.product.entity.ProductStatus;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

class ProductRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private LabelRepository labelRepository;

	@Autowired
	private GenreRepository genreRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductImageRepository productImageRepository;

	@Autowired
	private ProductGenreRepository productGenreRepository;

	@Autowired
	private EntityManager entityManager;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("artist·label 과 연관되어 저장되고 재조회하면 값이 유지된다")
		void persistsWithArtistAndLabel() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Label label = labelRepository.save(LabelFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist, label));

			// when
			flushAndClear();
			Product found = productRepository.findById(product.getId()).orElseThrow();

			// then
			assertThat(found.getPrice()).isEqualByComparingTo("45000.00");
			assertThat(found.getStatus()).isEqualTo(ProductStatus.ON_SALE);
			assertThat(found.getArtist().getId()).isEqualTo(artist.getId());
			assertThat(found.getLabel().getId()).isEqualTo(label.getId());
		}

		@Test
		@DisplayName("장르 2개를 추가하고 저장하면 두 건 모두 조회된다")
		void persistsGenres() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Genre jazz = genreRepository.save(GenreFixture.create("Jazz-product-save"));
			Genre soul = genreRepository.save(GenreFixture.create("Soul-product-save"));
			Product product = ProductFixture.create(artist);
			product.addGenre(jazz);
			product.addGenre(soul);

			// when
			Product saved = productRepository.save(product);
			flushAndClear();

			// then
			List<Genre> genres = productGenreRepository.findAllByProductId(saved.getId()).stream()
					.map(ProductGenre::getGenre)
					.toList();
			assertThat(genres).extracting(Genre::getName)
					.containsExactlyInAnyOrder("Jazz-product-save", "Soul-product-save");
		}

		@Test
		@DisplayName("replaceGenres() 로 교체 후 저장하면 새 장르만 조회된다")
		void replacesGenresOnSave() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Genre jazz = genreRepository.save(GenreFixture.create("Jazz-product-replace"));
			Genre rock = genreRepository.save(GenreFixture.create("Rock-product-replace"));
			Product product = ProductFixture.create(artist);
			product.addGenre(jazz);
			Product saved = productRepository.save(product);
			flushAndClear();

			// when
			Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
			reloaded.replaceGenres(List.of(rock));
			flushAndClear();

			// then
			List<Genre> genres = productGenreRepository.findAllByProductId(saved.getId()).stream()
					.map(ProductGenre::getGenre)
					.toList();
			assertThat(genres).extracting(Genre::getName).containsExactly("Rock-product-replace");
		}

		@Test
		@DisplayName("이미지를 순서 없이 추가해도 sortOrder 순으로 조회되고 첫 이미지만 대표다")
		void persistsImagesOrderedBySortOrder() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = ProductFixture.create(artist);
			product.addImage("https://cdn.groove.com/2.jpg", 2);
			product.addImage("https://cdn.groove.com/0.jpg", 0);
			product.addImage("https://cdn.groove.com/1.jpg", 1);
			Product saved = productRepository.save(product);

			// when
			flushAndClear();
			List<ProductImage> images = productImageRepository.findAllByProductIdOrderBySortOrderAsc(saved.getId());

			// then
			assertThat(images).extracting(ProductImage::getSortOrder).containsExactly(0, 1, 2);
			assertThat(images.get(0).isThumbnail()).isTrue();
			assertThat(images.get(1).isThumbnail()).isFalse();
			assertThat(images.get(2).isThumbnail()).isFalse();
		}
	}

	@Nested
	@DisplayName("findWithArtistAndLabelById()")
	class FindWithArtistAndLabelById {

		@Test
		@DisplayName("label 없이 저장해도 조회되고 artist 이름이 일치한다")
		void findsProductWithoutLabel() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create("Bill Evans"));
			Product saved = productRepository.save(ProductFixture.create(artist));
			flushAndClear();

			// when
			Optional<Product> found = productRepository.findWithArtistAndLabelById(saved.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getArtist().getName()).isEqualTo("Bill Evans");
			assertThat(found.get().getLabel()).isNull();
		}
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
