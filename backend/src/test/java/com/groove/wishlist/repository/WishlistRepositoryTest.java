package com.groove.wishlist.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.WishlistFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;
import com.groove.wishlist.entity.Wishlist;

class WishlistRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private WishlistRepository wishlistRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 회원이 같은 상품을 두 번 담으면 유니크 제약 위반이 발생한다")
		void throwsWhenMemberAndProductDuplicated() {
			// given
			Member member = memberRepository.save(MemberFixture.create("wishlist-repo-uk@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			wishlistRepository.saveAndFlush(WishlistFixture.create(member, product));

			// when & then
			assertThatThrownBy(() -> wishlistRepository.saveAndFlush(WishlistFixture.create(member, product)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("existsByMemberIdAndProductId() / findByMemberIdAndProductId()")
	class ExistsAndFind {

		@Test
		@DisplayName("등록된 조합이면 true 와 값을 반환한다")
		void returnsTrueAndValueWhenPresent() {
			// given
			Member member = memberRepository.save(MemberFixture.create("wishlist-repo-present@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			Wishlist wishlist = wishlistRepository.save(WishlistFixture.create(member, product));

			// when
			boolean exists = wishlistRepository.existsByMemberIdAndProductId(member.getId(), product.getId());
			Optional<Wishlist> found = wishlistRepository.findByMemberIdAndProductId(member.getId(),
					product.getId());

			// then
			assertThat(exists).isTrue();
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(wishlist.getId());
		}

		@Test
		@DisplayName("등록되지 않은 조합이면 false 와 empty 를 반환한다")
		void returnsFalseAndEmptyWhenAbsent() {
			// given
			Member member = memberRepository.save(MemberFixture.create("wishlist-repo-absent@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));

			// when
			boolean exists = wishlistRepository.existsByMemberIdAndProductId(member.getId(), product.getId());
			Optional<Wishlist> found = wishlistRepository.findByMemberIdAndProductId(member.getId(),
					product.getId());

			// then
			assertThat(exists).isFalse();
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("findAllByMemberIdAndProductStatusNot()")
	class FindAllByMemberIdAndProductStatusNot {

		@Test
		@DisplayName("숨김 상품과 타 회원의 위시리스트는 제외하고 등록일 내림차순으로 반환한다")
		void excludesHiddenAndOtherMemberSortedByIdDesc() {
			// given
			Member member = memberRepository.save(MemberFixture.create("wishlist-repo-sort@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("wishlist-repo-sort-other@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product visibleFirst = productRepository.save(ProductFixture.create(artist, "상품1"));
			Product visibleSecond = productRepository.save(ProductFixture.create(artist, "상품2"));
			Product hiddenProduct = productRepository.save(ProductFixture.create(artist, "상품3"));
			hiddenProduct.hide();
			productRepository.save(hiddenProduct);

			Wishlist firstWishlist = wishlistRepository.save(WishlistFixture.create(member, visibleFirst));
			Wishlist secondWishlist = wishlistRepository.save(WishlistFixture.create(member, visibleSecond));
			wishlistRepository.save(WishlistFixture.create(member, hiddenProduct));
			wishlistRepository.save(WishlistFixture.create(other, visibleFirst));

			// when
			Page<Wishlist> page = wishlistRepository.findAllByMemberIdAndProductStatusNot(member.getId(),
					ProductStatus.HIDDEN, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

			// then
			assertThat(page.getContent()).extracting(Wishlist::getId)
					.containsExactly(secondWishlist.getId(), firstWishlist.getId());
		}
	}
}
