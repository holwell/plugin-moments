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
import org.springframework.http.MediaType;
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

    /**
     * 超出展示范围的瞬间对未登录访客返回 404：状态码交给搜索引擎，正文交给访客。
     */
    private static final String OUT_OF_SCOPE_HTML = """
        <!doctype html>
        <html lang="zh-CN">
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>瞬间不可见</title>
            <style>
              body { margin: 0; min-height: 100vh; display: flex; align-items: center;
                justify-content: center; font-family: system-ui, -apple-system, sans-serif;
                color: #27272a; background: #fafafa; }
              main { max-width: 28rem; padding: 2rem; text-align: center; }
              h1 { font-size: 1.25rem; margin: 0 0 0.75rem; }
              p { margin: 0 0 0.5rem; color: #71717a; font-size: 0.875rem; line-height: 1.6; }
              a { color: #2563eb; font-size: 0.875rem; text-decoration: none; }
              @media (prefers-color-scheme: dark) {
                body { color: #f4f4f5; background: #18181b; }
                p { color: #a1a1aa; }
              }
            </style>
          </head>
          <body>
            <main>
              <h1>该瞬间不在展示范围内</h1>
              <p>作者设置了仅展示部分瞬间，这条瞬间对未登录访客不可见。</p>
              <p><a href="/moments">返回瞬间列表</a></p>
            </main>
          </body>
        </html>
        """;

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
            return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "moment")
                .flatMap(templateName -> momentFinder.get(momentName)
                    .flatMap(moment -> ServerResponse.ok().render(templateName, Map.of(
                        "moment", moment,
                        ModelConst.TEMPLATE_ID, "moment",
                        "title", getMomentTitle(),
                        "viewScope", viewScopeService.getViewScope()
                    )))
                    .switchIfEmpty(Mono.defer(MomentRouter::outOfScopeResponse)));
        };
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

    private static Mono<ServerResponse> outOfScopeResponse() {
        return ServerResponse.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(OUT_OF_SCOPE_HTML);
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
