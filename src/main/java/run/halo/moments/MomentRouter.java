package run.halo.moments;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static run.halo.app.theme.router.PageUrlUtils.totalPage;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.PageUrlUtils;
import run.halo.app.theme.router.UrlContextListResult;
import run.halo.moments.finders.MomentFinder;
import run.halo.moments.vo.MomentVo;


/**
 * Provides a <code>/moments</code> route for the topic end to handle routing.
 * Topic should contain a <code>moments.html</code> file.
 * <p>
 * In order to handle pagination, an additional /moments/page/{page} route has been adapted.
 * </p>
 *
 * @author LIlGG
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class MomentRouter {
    private static final String TAG_PARAM = "tag";

    private final MomentFinder momentFinder;

    private final ReactiveSettingFetcher settingFetcher;

    private final TemplateNameResolver templateNameResolver;

    private final AnonymousViewScopeService viewScopeService;

    @Bean
    RouterFunction<ServerResponse> momentRouterFunction() {
        return route(GET("/moments").or(GET("/moments/page/{page:\\d+}")), handlerFunction())
            .andRoute(GET("/moments/{momentName:\\S+}"), handlerMomentDefault());
    }

    private HandlerFunction<ServerResponse> handlerMomentDefault() {
        return request -> {
            String momentName = request.pathVariable("momentName");
            return momentFinder.get(momentName)
                .flatMap(moment -> renderMoment(request, moment))
                .switchIfEmpty(Mono.defer(() -> renderHidden(request)));
        };
    }

    private Mono<ServerResponse> renderMoment(ServerRequest request, MomentVo moment) {
        return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "moment")
            .flatMap(templateName -> ServerResponse.ok().render(templateName, Map.of(
                "moment", moment,
                ModelConst.TEMPLATE_ID, "moment",
                "title", getMomentTitle(),
                "viewScope", viewScopeService.getViewScope()
            )));
    }

    /**
     * 超出展示范围（或已删除）的瞬间：状态码仍是 404，但正文走主题的 moment-hidden 模板，
     * 从而保留站点布局，只替换内容区。主题可提供自己的 templates/moment-hidden.html 覆盖。
     */
    private Mono<ServerResponse> renderHidden(ServerRequest request) {
        return ServerResponse.status(HttpStatus.NOT_FOUND)
            .render("moment-hidden", Map.of(
                ModelConst.TEMPLATE_ID, "moment",
                "title", getMomentTitle(),
                "viewScope", viewScopeService.getViewScope()
            ));
    }

    private HandlerFunction<ServerResponse> handlerFunction() {
        return request -> {
            var model = Map.of("moments", momentList(request),
                ModelConst.TEMPLATE_ID, "moments",
                "tags", momentFinder.listAllTags(),
                "title", getMomentTitle(),
                "viewScope", viewScopeService.getViewScope());
            return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "moments")
                .flatMap(templateName -> ServerResponse.ok().render(templateName, model));
        };
    }

    Mono<String> getMomentTitle() {
        return this.settingFetcher.get("base")
            .map(setting -> setting.get("title").asText("瞬间"))
            .defaultIfEmpty("瞬间");
    }

    private Mono<UrlContextListResult<MomentVo>> momentList(ServerRequest request) {
        String path = request.path();
        String tagVal = request.queryParam(TAG_PARAM)
            .filter(StringUtils::isNotBlank)
            .orElse(null);
        int pageNum = pageNumInPathVariable(request);
        String tag = tagPathQueryParam(request);
        return this.settingFetcher.get("base")
            .map(item -> item.get("pageSize").asInt(10))
            .defaultIfEmpty(10)
            .flatMap(pageSize -> momentFinder.listByTag(pageNum, pageSize, tag)
                .map(list -> new UrlContextListResult.Builder<MomentVo>()
                    .listResult(list)
                    .nextUrl(appendTagParamIfPresent(
                        PageUrlUtils.nextPageUrl(path, totalPage(list)), tagVal)
                    )
                    .prevUrl(appendTagParamIfPresent(PageUrlUtils.prevPageUrl(path), tagVal))
                    .build()
                )
            );
    }

    String appendTagParamIfPresent(String uriString, String tagValue) {
        return UriComponentsBuilder.fromUriString(uriString)
            .queryParamIfPresent(TAG_PARAM, Optional.ofNullable(tagValue))
            .build()
            .toString();
    }

    private int pageNumInPathVariable(ServerRequest request) {
        String page = request.pathVariables().get("page");
        return NumberUtils.toInt(page, 1);
    }

    private String tagPathQueryParam(ServerRequest request) {
        return request.queryParam(TAG_PARAM).orElse(null);
    }
}
