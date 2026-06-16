package com.groove.common.persistence;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * MySQL FULLTEXT 검색용 커스텀 HQL 함수 등록.
 *
 * <p>fts_match(c1, c2, query) 호출을 MATCH(c1, c2) AGAINST(query IN BOOLEAN MODE) 로 렌더링하고
 * relevance score(Double)를 반환한다.
 *
 * <p>Hibernate 가 META-INF/services/org.hibernate.boot.model.FunctionContributor 의 ServiceLoader 항목으로
 * 부팅 시 자동 발견한다.
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
