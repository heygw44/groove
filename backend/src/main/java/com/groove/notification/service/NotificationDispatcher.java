package com.groove.notification.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.member.repository.MemberRepository;
import com.groove.notification.entity.Notification;
import com.groove.notification.entity.NotificationType;
import com.groove.notification.repository.AlbumWatchRepository;
import com.groove.notification.repository.NotificationRepository;
import com.groove.product.entity.Album;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.wishlist.repository.WishlistRepository;

import lombok.RequiredArgsConstructor;

/**
 * 재입고·가격 인하·새 프레싱 알림을 구독자에게 적재한다. 리스너(NotificationEventListener)와 반드시 분리된 빈이어야
 * 한다 - 리스너 자신에 {@code @Transactional} 을 걸면 프록시 self-invocation 으로 트랜잭션이 걸리지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationDispatcher {

	private final WishlistRepository wishlistRepository;
	private final AlbumWatchRepository albumWatchRepository;
	private final NotificationRepository notificationRepository;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final AlbumRepository albumRepository;

	@Transactional
	public void dispatchRestock(RestockEvent event) {
		dispatchProduct(event.productId(), event.productTitle(), NotificationType.RESTOCK);
	}

	@Transactional
	public void dispatchPriceDrop(PriceDropEvent event) {
		dispatchProduct(event.productId(), event.productTitle(), NotificationType.PRICE_DROP);
	}

	@Transactional
	public void dispatchNewPressing(NewPressingEvent event) {
		List<Long> subscriberIds = albumWatchRepository.findMemberIdsByAlbumId(event.albumId());
		if (subscriberIds.isEmpty()) {
			return;
		}

		Set<Long> alreadyNotified = new HashSet<>(notificationRepository.findMemberIdsWithUnreadAlbumNotification(
				subscriberIds, event.albumId(), NotificationType.NEW_PRESSING));
		List<Long> targetIds = excludeAlreadyNotified(subscriberIds, alreadyNotified);
		if (targetIds.isEmpty()) {
			return;
		}

		Album album = albumRepository.getReferenceById(event.albumId());
		List<Notification> notifications = targetIds.stream()
				.map(memberId -> Notification.forAlbum(memberRepository.getReferenceById(memberId), album,
						event.albumTitle()))
				.toList();
		notificationRepository.saveAll(notifications);
	}

	private void dispatchProduct(Long productId, String titleSnapshot, NotificationType type) {
		List<Long> subscriberIds = wishlistRepository.findAlertEnabledMemberIdsByProductId(productId);
		if (subscriberIds.isEmpty()) {
			return;
		}

		Set<Long> alreadyNotified = new HashSet<>(
				notificationRepository.findMemberIdsWithUnreadNotification(subscriberIds, productId, type));
		List<Long> targetIds = excludeAlreadyNotified(subscriberIds, alreadyNotified);
		if (targetIds.isEmpty()) {
			return;
		}

		Product product = productRepository.getReferenceById(productId);
		saveProductNotifications(targetIds, product, type, titleSnapshot);
	}

	// getReferenceById 는 프록시라 SELECT 없이 FK 값만으로 연관관계를 건다(ProductViewLogSaver 와 동일한 이유).
	private void saveProductNotifications(List<Long> memberIds, Product product, NotificationType type,
			String titleSnapshot) {
		List<Notification> notifications = memberIds.stream()
				.map(memberId -> Notification.forProduct(memberRepository.getReferenceById(memberId), product, type,
						titleSnapshot))
				.toList();
		notificationRepository.saveAll(notifications);
	}

	// 단일 워커 executor(notificationExecutor)가 이벤트를 순서대로 하나씩 처리하므로 같은 상품/앨범 알림이
	// 동시에 쌓일 수 없다 - 차집합 계산에 별도 락이 필요 없다.
	private List<Long> excludeAlreadyNotified(List<Long> memberIds, Set<Long> alreadyNotified) {
		return memberIds.stream().filter(id -> !alreadyNotified.contains(id)).toList();
	}
}
