package com.groove.catalog.domain;

import com.groove.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 장르 엔티티 (ERD §4.4).
 *
 * <p>이름만 가진 가장 단순한 카탈로그 메타. UNIQUE 는 DB 제약에 위임하고 도메인 레이어는
 * 이름 정규화 / 변경만 책임진다. Album 이 FK 로 참조하지만 본 이슈 범위에서는 Album 이 없다.
 */
@Entity
@Table(name = "genre")
public class Genre extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50, unique = true)
    private String name;

    protected Genre() {
    }

    private Genre(String name) {
        this.name = name;
    }

    /**
     * 정적 팩토리. 이름 검증 (공백·길이) 은 호출 측 (Bean Validation) 에서 끝낸 상태로 전달된다고 가정한다.
     */
    public static Genre create(String name) {
        return new Genre(name);
    }

    /**
     * 이름 변경. 동일 이름 충돌 검증은 서비스 레이어에서 {@code existsByNameAndIdNot} 으로 처리한다.
     */
    public void rename(String newName) {
        this.name = newName;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
