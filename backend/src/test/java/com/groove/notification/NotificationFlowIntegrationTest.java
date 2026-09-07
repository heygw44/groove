package com.groove.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.notification.entity.Notification;
import com.groove.notification.entity.NotificationType;
import com.groove.notification.repository.NotificationRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;
import com.groove.wishlist.dto.WishlistAddRequest;

@AutoConfigureMockMvc
class NotificationFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	ArtistRepository artistRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	AlbumRepository albumRepository;

	@Autowired
	StockRepository stockRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Nested
	@DisplayName("재입고 이벤트 → 알림 적재 → 목록/안읽음수 조회 → 읽음 처리 → 삭제 흐름")
	class RestockNotificationFlow {

		@Test
		@DisplayName("품절 상품이 재입고되면 위시리스트 구독자에게 알림이 쌓이고 조회·읽음 처리·삭제까지 이어진다")
		void deliversRestockNotificationThroughReadAndDelete() throws Exception {
			// given: 재고 0인 상품을 위시리스트에 담아 재입고 알림을 구독한다
			Member member = memberRepository.save(
					MemberFixture.create("notify-" + UUID.randomUUID() + "@groove.com"));
			String accessToken = "Bearer " + jwtProvider.createAccessToken(member.getId(), MemberRole.USER);
			Product product = seedProduct(0);

			mockMvc.perform(post("/api/v1/wishlist")
							.header(HttpHeaders.AUTHORIZATION, accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new WishlistAddRequest(product.getId()))))
					.andExpect(status().isCreated());

			Member admin = memberRepository.save(
					Member.create("notify-admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when: 관리자가 재고를 0에서 양수로 조정해 재입고 이벤트를 발행한다
			mockMvc.perform(patch("/api/v1/admin/products/" + product.getId() + "/stock")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									StockFixture.adjustRequest(StockChangeType.IN, 5))))
					.andExpect(status().isOk());

			// then: AFTER_COMMIT 리스너가 비동기로 돌 때까지 기다렸다가 알림이 쌓였는지 확인한다
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				List<Notification> notifications = notificationRepository
						.findAllByMemberId(member.getId(), Pageable.unpaged()).getContent();
				assertThat(notifications).hasSize(1);
				assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.RESTOCK);
			});
			Long notificationId = notificationRepository
					.findAllByMemberId(member.getId(), Pageable.unpaged()).getContent().get(0).getId();

			// then: 목록 조회에 안 읽은 알림으로 나타난다
			mockMvc.perform(get("/api/v1/members/me/notifications")
							.header(HttpHeaders.AUTHORIZATION, accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].id", is(notificationId.intValue())))
					.andExpect(jsonPath("$.data.content[0].type", is("RESTOCK")))
					.andExpect(jsonPath("$.data.content[0].productId", is(product.getId().intValue())))
					.andExpect(jsonPath("$.data.content[0].readAt").doesNotExist());

			// then: 안 읽은 개수는 1건이다
			mockMvc.perform(get("/api/v1/members/me/notifications/unread-count")
							.header(HttpHeaders.AUTHORIZATION, accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.count", is(1)));

			// when: 읽음 처리한다
			mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
							.header(HttpHeaders.AUTHORIZATION, accessToken))
					.andExpect(status().isOk());

			// then: 안 읽은 개수는 0건이고 목록에는 읽음 시각이 채워진다
			mockMvc.perform(get("/api/v1/members/me/notifications/unread-count")
							.header(HttpHeaders.AUTHORIZATION, accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.count", is(0)));
			mockMvc.perform(get("/api/v1/members/me/notifications")
							.header(HttpHeaders.AUTHORIZATION, accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].readAt").exists());

			// when: 삭제한다
			mockMvc.perform(delete("/api/v1/notifications/" + notificationId)
							.header(HttpHeaders.AUTHORIZATION, accessToken))
					.andExpect(status().isOk());

			// then: 알림이 지워진다
			assertThat(notificationRepository.findById(notificationId)).isEmpty();
		}
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}
}
