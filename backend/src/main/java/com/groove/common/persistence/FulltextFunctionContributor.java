package com.groove.common.persistence;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * MySQL FULLTEXT 검색용 커스텀 HQL 함수 등록 (#204).
 *
 * <p>{@code fts_match(c1, c2, query)} 호출을 {@code MATCH(c1, c2) AGAINST(query IN BOOLEAN MODE)}
 * 로 렌더링하고 relevance score({@link Double})를 반환한다. JPA Criteria/Specification
 * ({@link com.groove.catalog.album.domain.AlbumSpecs#keyword})에서
 * {@code cb.function("fts_match", Double.class, title, artistName, query)} 로 호출한다.
 *
 * <p>BOOLEAN MODE 를 쓰는 이유: NATURAL LANGUAGE MODE 의 "50% 임계"(한 토큰이 전체 행의 절반
 * 초과 등장 시 무시) 규칙을 피해 소량 데이터/테스트에서도 결정적으로 매칭되도록 한다. ngram 파서와
 * 결합해 따옴표 구문(phrase) 으로 넘기면 부분일치(substring) 의미가 보존된다.
 *
 * <p>Hibernate 가 {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}
 * 의 ServiceLoader 항목으로 부팅 시 자동 발견한다(별도 설정 불필요).
 */
public class FulltextFunctionContributor implements FunctionContributor {

    /** Criteria 에서 참조하는 함수명. */
    public static final String FUNCTION_NAME = "fts_match";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(
                FUNCTION_NAME,
                "match (?1, ?2) against (?3 in boolean mode)",
                functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.DOUBLE));
    }
}
