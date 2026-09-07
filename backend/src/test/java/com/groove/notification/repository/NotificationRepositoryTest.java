package com.groove.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.NotificationFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.notification.entity.Notification;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

class NotificationRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	private Product createProduct(String suffix) {
		Artist artist = artistRepository.save(ArtistFixture.create("Artist-" + suffix));
		Product created = ProductFixture.create(artist, "Product-" + suffix);
		albumRepository.save(created.getAlbum());
		return productRepository.save(created);
	}

	@Nested
	@DisplayName("findAllByMemberId()")
	class FindAllByMemberId {

		@Test
		@DisplayName("생성일 내림차순으로 내 알림만 반환한다")
		void returnsMyNotificationsSortedByCreatedAtDesc() {
			// given
			Member member = memberRepository.save(MemberFixture.create("notification-repo-sort@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("notification-repo-sort-other@groove.com"));
			Product product = createProduct("sort");

			Notification first = notificationRepository.save(NotificationFixture.forProduct(member, product));
			Notification second = notificationRepository.save(NotificationFixture.forProduct(member, product));
			notificationRepository.save(NotificationFixture.forProduct(other, product));

			// when
			Page<Notification> page = notificationRepository.findAllByMemberId(member.getId(),
					PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

			// then
			assertThat(page.getContent()).extracting(Notification::getId)
					.containsExactly(second.getId(), first.getId());
		}
	}

	@Nested
	@DisplayName("findAllByMemberIdAndReadAtIsNull()")
	class FindAllByMemberIdAndReadAtIsNull {

		@Test
		@DisplayName("안 읽은 알림만 반환한다")
		void returnsOnlyUnread() {
			// given
			Member member = memberRepository.save(MemberFixture.create("notification-repo-unread@groove.com"));
			Product product = createProduct("unread");

			Notification unread = notificationRepository.save(NotificationFixture.forProduct(member, product));
			Notification read = notificationRepository.save(NotificationFixture.forProduct(member, product));
			read.markRead(LocalDateTime.now());
			notificationRepository.save(read);

			// when
			Page<Notification> page = notificationRepository.findAllByMemberIdAndReadAtIsNull(member.getId(),
					PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

			// then
			assertThat(page.getContent()).extracting(Notification::getId).containsExactly(unread.getId());
		}
	}

	@Nested
	@DisplayName("countByMemberIdAndReadAtIsNull()")
	class CountByMemberIdAndReadAtIsNull {

		@Test
		@DisplayName("내 안 읽은 알림 개수만 센다")
		void countsOnlyMyUnread() {
			// given
			Member member = memberRepository.save(MemberFixture.create("notification-repo-count@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("notification-repo-count-other@groove.com"));
			Product product = createProduct("count");

			notificationRepository.save(NotificationFixture.forProduct(member, product));
			notificationRepository.save(NotificationFixture.forProduct(member, product));
			notificationRepository.save(NotificationFixture.forProduct(other, product));

			// when
			long count = notificationRepository.countByMemberIdAndReadAtIsNull(member.getId());

			// then
			assertThat(count).isEqualTo(2L);
		}
	}

	@Nested
	@DisplayName("markAllRead()")
	class MarkAllRead {

		@Test
		@DisplayName("내 안 읽은 알림만 일괄 읽음 처리하고 처리 건수를 반환한다")
		void updatesOnlyMyUnreadNotifications() {
			// given
			Member member = memberRepository.save(MemberFixture.create("notification-repo-markall@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("notification-repo-markall-other@groove.com"));
			Product product = createProduct("markall");

			Notification first = notificationRepository.save(NotificationFixture.forProduct(member, product));
			Notification second = notificationRepository.save(NotificationFixture.forProduct(member, product));
			Notification otherNotification = notificationRepository
					.save(NotificationFixture.forProduct(other, product));

			// when
			int updated = notificationRepository.markAllRead(member.getId(), LocalDateTime.now());

			// then
			assertThat(updated).isEqualTo(2);
			assertThat(notificationRepository.findById(first.getId()).orElseThrow().getReadAt()).isNotNull();
			assertThat(notificationRepository.findById(second.getId()).orElseThrow().getReadAt()).isNotNull();
			assertThat(notificationRepository.findById(otherNotification.getId()).orElseThrow().getReadAt())
					.isNull();
		}
	}
}
