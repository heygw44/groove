package com.groove.notification.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;
import com.groove.member.entity.Member;
import com.groove.product.entity.Album;
import com.groove.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원에게 발행된 알림 한 건. product/album 중 정확히 하나만 값을 가지는 건 서비스 계층이 보장한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "notification",
		indexes = {
			@Index(name = "idx_notification_member_created", columnList = "member_id, created_at"),
			@Index(name = "idx_notification_member_read", columnList = "member_id, read_at")
		})
public class Notification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notification_member"))
	private Member member;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_notification_product"))
	private Product product;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "album_id", foreignKey = @ForeignKey(name = "fk_notification_album"))
	private Album album;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private NotificationType type;

	@Column(name = "title_snapshot", nullable = false, length = 200)
	private String titleSnapshot;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Builder(access = PRIVATE)
	private Notification(Member member, Product product, Album album, NotificationType type, String titleSnapshot) {
		this.member = member;
		this.product = product;
		this.album = album;
		this.type = type;
		this.titleSnapshot = titleSnapshot;
	}

	public static Notification forProduct(Member member, Product product, NotificationType type,
			String titleSnapshot) {
		return Notification.builder()
				.member(member)
				.product(product)
				.type(type)
				.titleSnapshot(titleSnapshot)
				.build();
	}

	public static Notification forAlbum(Member member, Album album, String titleSnapshot) {
		return Notification.builder()
				.member(member)
				.album(album)
				.type(NotificationType.NEW_PRESSING)
				.titleSnapshot(titleSnapshot)
				.build();
	}

	/** 이미 읽었으면 아무것도 하지 않는다(멱등). */
	public void markRead(LocalDateTime now) {
		if (this.readAt != null) {
			return;
		}
		this.readAt = now;
	}
}
