package com.groove.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA(Vue Router history 모드) clean URL fallback.
 *
 * SpaRoutes.PATTERNS 의 경로를 index.html 로 forward 한다. 덕분에 브라우저가 /cart 등으로 직접 진입하거나
 * 새로고침해도 SPA 셸이 로드되고 클라이언트 라우터가 화면을 그린다.
 *
 * forward 대상은 명시적 화이트리스트라 /api/**·/actuator/**·/error 같은 백엔드 경로를 침범하지 않는다(존재하지 않는 API 도
 * HTML 이 아닌 JSON 404 로 응답). 정적 에셋(/assets/*.js 등 점 포함 경로)은 애초에 패턴에 없어 정적 리소스 핸들러가
 * 처리한다. 루트 "/" 는 Spring Boot welcome-page 가 index.html 을 서빙하므로 별도 forward 가 필요 없다.
 *
 * View controller 매핑은 @RequestMapping 컨트롤러보다 우선순위가 낮고 정적 리소스 핸들러보다 높으므로, 실제 API/정적
 * 자원이 있으면 그쪽이 먼저 처리되고 없을 때만 SPA 셸로 forward 된다.
 */
@Configuration(proxyBeanMethods = false)
public class SpaForwardConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String pattern : SpaRoutes.PATTERNS) {
            registry.addViewController(pattern).setViewName("forward:/index.html");
        }
    }
}
