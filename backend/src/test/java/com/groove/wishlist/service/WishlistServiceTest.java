package com.groove.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.fixture.WishlistFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.ProductImageRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.wishlist.dto.WishlistItemResponse;
import com.groove.wishlist.entity.Wishlist;
import com.groove.wishlist.repository.WishlistRepository;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long PRODUCT_ID = 100L;
	private static final Long WISHLIST_ID = 1000L;

	@Mock
	WishlistRepository wishlistRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	ProductRepository productRepository;

	@Mock
	ProductImageRepository productImageRepository;

	@Mock
	StockRepository stockRepository;

	WishlistService wishlistService;

	Member member;
	Product product;

	@BeforeEach
	void setUp() {
		wishlistService = new WishlistService(wishlistRepository, memberRepository, productRepository,
				productImageRepository, stockRepository);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
	}

	@Nested
	@DisplayName("add()")
	class Add {

		@Test
		@DisplayName("정상 요청이면 위시리스트에 등록한다")
		void addsWishlist() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(wishlistRepository.existsByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID)).willReturn(false);
			willAnswer(invocation -> WishlistFixture.withId(invocation.getArgument(0), WISHLIST_ID))
					.given(wishlistRepository).saveAndFlush(any());
			given(stockRepository.findByProductId(PRODUCT_ID))
					.willReturn(Optional.of(StockFixture.create(product, 10)));
			given(productImageRepository.findAllByProductIdInAndSortOrder(any(), eq(0))).willReturn(List.of());

			// when
			WishlistItemResponse response = wishlistService.add(MEMBER_ID, WishlistFixture.addRequest(PRODUCT_ID));

			// then
			assertThat(response.productId()).isEqualTo(PRODUCT_ID);
			assertThat(response.title()).isEqualTo(product.getTitle());
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member withdrawn = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

			// when & then
			assertThatThrownBy(() -> wishlistService.add(MEMBER_ID, WishlistFixture.addRequest(PRODUCT_ID)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
		}

		@Test
		@DisplayName("회원이 없으면 MEMBER_NOT_FOUND 예외를 던진다")
		void throwsWhenMemberNotFound() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> wishlistService.add(MEMBER_ID, WishlistFixture.addRequest(PRODUCT_ID)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
		}

		@Test
		@DisplayName("상품이 없으면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> wishlistService.add(MEMBER_ID, WishlistFixture.addRequest(PRODUCT_ID)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}

		@Test
		@DisplayName("숨김 상품이면 PRODUCT_HIDDEN 예외를 던진다")
		void throwsWhenProductHidden() {
			// given
			product.hide();
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			// when & then
			assertThatThrownBy(() -> wishlistService.add(MEMBER_ID, WishlistFixture.addRequest(PRODUCT_ID)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_HIDDEN);
		}

		@Test
		@DisplayName("이미 등록된 상품이면 WISHLIST_ALREADY_EXISTS 예외를 던지고 저장하지 않는다")
		void throwsWhenAlreadyExists() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(wishlistRepository.existsByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> wishlistService.add(MEMBER_ID, WishlistFixture.addRequest(PRODUCT_ID)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.WISHLIST_ALREADY_EXISTS);
			verify(wishlistRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("동시 등록으로 저장 시점에 유니크 제약을 위반하면 WISHLIST_ALREADY_EXISTS 예외로 변환한다")
		void throwsWhenSaveViolatesUniqueConstraint() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(wishlistRepository.existsByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID)).willReturn(false);
			willThrow(new DataIntegrityViolationException("duplicate"))
					.given(wishlistRepository).saveAndFlush(any());

			// when & then
			assertThatThrownBy(() -> wishlistService.add(MEMBER_ID, WishlistFixture.addRequest(PRODUCT_ID)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.WISHLIST_ALREADY_EXISTS);
		}
	}

	@Nested
	@DisplayName("remove()")
	class Remove {

		@Test
		@DisplayName("등록된 상품이면 삭제한다")
		void removesWishlist() {
			// given
			Wishlist wishlist = WishlistFixture.withId(WishlistFixture.create(member, product), WISHLIST_ID);
			given(wishlistRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID))
					.willReturn(Optional.of(wishlist));

			// when
			wishlistService.remove(MEMBER_ID, PRODUCT_ID);

			// then
			verify(wishlistRepository).delete(wishlist);
		}

		@Test
		@DisplayName("등록되지 않은 상품이면 WISHLIST_NOT_FOUND 예외를 던진다")
		void throwsWhenNotFound() {
			// given
			given(wishlistRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> wishlistService.remove(MEMBER_ID, PRODUCT_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.WISHLIST_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("getWishlist()")
	class GetWishlist {

		@Test
		@DisplayName("썸네일과 재고를 매핑해 목록을 반환한다")
		void mapsThumbnailsAndStock() {
			// given
			Wishlist wishlist = WishlistFixture.withId(WishlistFixture.create(member, product), WISHLIST_ID);
			Page<Wishlist> page = new PageImpl<>(List.of(wishlist));
			given(wishlistRepository.findAllByMemberIdAndProductStatusNot(eq(MEMBER_ID), eq(ProductStatus.HIDDEN),
					any())).willReturn(page);
			given(productImageRepository.findAllByProductIdInAndSortOrder(any(), eq(0))).willReturn(List.of());
			given(stockRepository.findAllByProductIdIn(any())).willReturn(List.of());

			// when
			PageResponse<WishlistItemResponse> response = wishlistService.getWishlist(MEMBER_ID,
					WishlistFixture.searchRequest(0, 20));

			// then
			assertThat(response.content()).hasSize(1);
			assertThat(response.content().get(0).productId()).isEqualTo(PRODUCT_ID);
			assertThat(response.totalElements()).isEqualTo(1);
		}

		@Test
		@DisplayName("빈 페이지면 썸네일/재고 리포지토리를 호출하지 않는다")
		void doesNotCallRepositoriesWhenPageEmpty() {
			// given
			Page<Wishlist> emptyPage = new PageImpl<>(List.of());
			given(wishlistRepository.findAllByMemberIdAndProductStatusNot(eq(MEMBER_ID), eq(ProductStatus.HIDDEN),
					any())).willReturn(emptyPage);

			// when
			PageResponse<WishlistItemResponse> response = wishlistService.getWishlist(MEMBER_ID,
					WishlistFixture.searchRequest(0, 20));

			// then
			assertThat(response.content()).isEmpty();
			verify(productImageRepository, never()).findAllByProductIdInAndSortOrder(any(), any(Integer.class));
			verify(stockRepository, never()).findAllByProductIdIn(any());
		}
	}
}
