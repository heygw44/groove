package com.groove.notification.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import com.groove.global.common.BaseTimeEntity;
import com.groove.member.entity.Member;
import com.groove.product.entity.Album;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원이 새 프레싱 알림을 받기 위해 구독하는 앨범 한 줄. 회원-앨범 조합당 행 하나만 존재한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "album_watch",
		uniqueConstraints = @UniqueConstraint(name = "uk_album_watch_member_album",
				columnNames = {"member_id", "album_id"}),
		indexes = @Index(name = "idx_album_watch_member_created", columnList = "member_id, created_at"))
public class AlbumWatch extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_album_watch_member"))
	private Member member;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "album_id", nullable = false, foreignKey = @ForeignKey(name = "fk_album_watch_album"))
	private Album album;

	@Builder(access = PRIVATE)
	private AlbumWatch(Member member, Album album) {
		this.member = member;
		this.album = album;
	}

	public static AlbumWatch create(Member member, Album album) {
		return AlbumWatch.builder()
				.member(member)
				.album(album)
				.build();
	}
}
