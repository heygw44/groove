package com.groove.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

class ProductTest {

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("생성하면 status 는 ON_SALE 이고 장르·이미지 목록은 비어 있다")
		void createsWithOnSaleStatusAndEmptyCollections() {
			// when
			Product product = ProductFixture.create(ArtistFixture.create());

			// then
			assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
			assertThat(product.getProductGenres()).isEmpty();
			assertThat(product.getImages()).isEmpty();
		}
	}

	@Nested
	@DisplayName("hide() / markSoldOut() / resume()")
	class StatusTransition {

		@Test
		@DisplayName("hide() 를 호출하면 상태가 HIDDEN 으로 바뀐다")
		void changesStatusToHidden() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());

			// when
			product.hide();

			// then
			assertThat(product.isHidden()).isTrue();
		}

		@Test
		@DisplayName("markSoldOut() 을 호출하면 상태가 SOLD_OUT 으로 바뀐다")
		void changesStatusToSoldOut() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());

			// when
			product.markSoldOut();

			// then
			assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		}

		@Test
		@DisplayName("resume() 을 호출하면 상태가 ON_SALE 로 바뀐다")
		void changesStatusToOnSale() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			product.hide();

			// when
			product.resume();

			// then
			assertThat(product.isOnSale()).isTrue();
		}
	}

	@Nested
	@DisplayName("restore()")
	class Restore {

		@Test
		@DisplayName("재고가 남아 있으면 ON_SALE 로 복구한다")
		void restoresToOnSaleWhenStockRemains() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			product.hide();

			// when
			product.restore(5);

			// then
			assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
		}

		@Test
		@DisplayName("재고가 0 이면 SOLD_OUT 으로 복구한다")
		void restoresToSoldOutWhenStockIsZero() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			product.hide();

			// when
			product.restore(0);

			// then
			assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		}

		@Test
		@DisplayName("HIDDEN 상태가 아니면 PRODUCT_NOT_HIDDEN 예외를 던진다")
		void throwsWhenNotHidden() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());

			// when & then
			assertThatThrownBy(() -> product.restore(5))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_HIDDEN);
		}
	}

	@Nested
	@DisplayName("updateInfo()")
	class UpdateInfo {

		@Test
		@DisplayName("정보를 변경하면 필드에 반영된다")
		void updatesFields() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Artist newArtist = ArtistFixture.create("John Coltrane");
			Label newLabel = LabelFixture.create();

			// when
			product.updateInfo("A Love Supreme", newArtist, newLabel, LocalDate.of(2025, 3, 1), "리마스터",
					"Blue", "JP", 2025, "IMP-1234", "4988005123456", EditionType.REMASTER,
					new BigDecimal("52000.00"), "변경된 설명");

			// then
			assertThat(product.getTitle()).isEqualTo("A Love Supreme");
			assertThat(product.getArtist()).isEqualTo(newArtist);
			assertThat(product.getLabel()).isEqualTo(newLabel);
			assertThat(product.getReleaseDate()).isEqualTo(LocalDate.of(2025, 3, 1));
			assertThat(product.getPressingInfo()).isEqualTo("리마스터");
			assertThat(product.getColorVariant()).isEqualTo("Blue");
			assertThat(product.getCountry()).isEqualTo("JP");
			assertThat(product.getPressingYear()).isEqualTo(2025);
			assertThat(product.getCatalogNo()).isEqualTo("IMP-1234");
			assertThat(product.getCatalogNoNormalized()).isEqualTo("IMP1234");
			assertThat(product.getBarcode()).isEqualTo("4988005123456");
			assertThat(product.getEditionType()).isEqualTo(EditionType.REMASTER);
			assertThat(product.getPrice()).isEqualByComparingTo("52000.00");
			assertThat(product.getDescription()).isEqualTo("변경된 설명");
		}
	}

	@Nested
	@DisplayName("create() - 프레싱 필드")
	class CreatePressingFields {

		@Test
		@DisplayName("catalogNo 는 대문자화하고 공백/하이픈을 제거해 catalogNoNormalized 에 저장한다")
		void normalizesCatalogNo() {
			// given
			Artist artist = ArtistFixture.create();
			Album album = AlbumFixture.create(artist);

			// when
			Product product = Product.create(album, "Kind of Blue", artist, null, LocalDate.of(1959, 8, 17),
					"180g", "Black", "US", 1959, "cs 8163-a", "0888430123", EditionType.ORIGINAL,
					new BigDecimal("45000"), "설명");

			// then
			assertThat(product.getCatalogNo()).isEqualTo("cs 8163-a");
			assertThat(product.getCatalogNoNormalized()).isEqualTo("CS8163A");
		}

		@Test
		@DisplayName("catalogNo 가 null 이면 catalogNoNormalized 도 null 이다")
		void keepsNullNormalizedWhenCatalogNoNull() {
			// given
			Artist artist = ArtistFixture.create();
			Album album = AlbumFixture.create(artist);

			// when
			Product product = Product.create(album, "Kind of Blue", artist, null, LocalDate.of(1959, 8, 17),
					"180g", "Black", "US", 1959, null, null, EditionType.ORIGINAL, new BigDecimal("45000"), "설명");

			// then
			assertThat(product.getCatalogNoNormalized()).isNull();
		}

		@Test
		@DisplayName("editionType 을 지정하지 않으면 STANDARD 로 기본값이 채워진다")
		void defaultsEditionTypeToStandard() {
			// given
			Artist artist = ArtistFixture.create();
			Album album = AlbumFixture.create(artist);

			// when
			Product product = Product.create(album, "Kind of Blue", artist, null, LocalDate.of(1959, 8, 17),
					"180g", "Black", null, null, null, null, null, new BigDecimal("45000"), "설명");

			// then
			assertThat(product.getEditionType()).isEqualTo(EditionType.STANDARD);
		}
	}

	@Nested
	@DisplayName("addGenre()")
	class AddGenre {

		@Test
		@DisplayName("같은 장르를 두 번 추가해도 1건만 등록된다")
		void ignoresDuplicateGenre() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Genre jazz = GenreFixture.create("Jazz");

			// when
			product.addGenre(jazz);
			product.addGenre(jazz);

			// then
			assertThat(product.getProductGenres()).hasSize(1);
		}
	}

	@Nested
	@DisplayName("replaceGenres()")
	class ReplaceGenres {

		@Test
		@DisplayName("호출하면 기존 매핑이 새 목록으로 교체된다")
		void replacesExistingGenres() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Genre jazz = GenreFixture.create("Jazz");
			Genre rock = GenreFixture.create("Rock");
			product.addGenre(jazz);

			// when
			product.replaceGenres(List.of(rock));

			// then
			assertThat(product.getProductGenres()).hasSize(1);
			assertThat(product.getProductGenres().get(0).getGenre()).isEqualTo(rock);
		}

		@Test
		@DisplayName("새 목록에 남아 있는 장르는 기존 매핑 인스턴스를 그대로 유지한다")
		void keepsExistingLinkForRemainingGenre() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Genre jazz = GenreFixture.create("Jazz");
			Genre rock = GenreFixture.create("Rock");
			product.addGenre(jazz);
			ProductGenre existingLink = product.getProductGenres().get(0);

			// when
			product.replaceGenres(List.of(jazz, rock));

			// then
			assertThat(product.getProductGenres()).hasSize(2);
			assertThat(product.getProductGenres().get(0)).isSameAs(existingLink);
			assertThat(product.getProductGenres().get(1).getGenre()).isEqualTo(rock);
		}
	}

	@Nested
	@DisplayName("addImage()")
	class AddImage {

		@Test
		@DisplayName("호출하면 이미지가 추가되고 product 역참조가 설정된다")
		void addsImageWithBackReference() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());

			// when
			product.addImage("https://cdn.groove.com/cover.jpg", 0);

			// then
			assertThat(product.getImages()).hasSize(1);
			assertThat(product.getImages().get(0).getProduct()).isEqualTo(product);
		}
	}
}
