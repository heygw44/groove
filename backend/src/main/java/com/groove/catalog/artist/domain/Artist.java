package com.groove.catalog.artist.domain;

import com.groove.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 아티스트 엔티티 (ERD §4.3).
 *
 * <p>Genre/Label 과 달리 {@code name} 에 UNIQUE 를 두지 않는다 — 동명이인이 실제로 존재할 수 있어
 * ID 로만 식별한다 (ERD §4.3 비즈니스 룰). {@code description} 은 자유 텍스트(NULL 허용).
 * Album (W5-3) 이 후속 이슈에서 {@code artist_id} 를 FK 로 참조한다.
 */
@Entity
@Table(name = "artist")
public class Artist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    protected Artist() {
    }

    private Artist(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 정적 팩토리. 입력 검증(공백/길이) 은 호출 측 (Bean Validation) 에서 끝낸 상태로 전달된다고 가정한다.
     */
    public static Artist create(String name, String description) {
        return new Artist(name, description);
    }

    /**
     * 이름과 설명을 동시에 갱신한다 (PUT 전체 교체 정책). {@code description} 을 {@code null} 로
     * 전달하면 명시적으로 지움으로 처리된다.
     */
    public void update(String newName, String newDescription) {
        this.name = newName;
        this.description = newDescription;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
