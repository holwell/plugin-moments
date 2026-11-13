package run.halo.moments;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;
import run.halo.moments.exception.NotFoundException;
import run.halo.moments.finders.MomentFinder;
import run.halo.moments.finders.MomentPublicQueryService;
import run.halo.moments.vo.MomentVo;
import run.halo.moments.vo.ViewScopeVo;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

/**
 * Endpoint for moment query.
 *
 */
@Component
@RequiredArgsConstructor
public class MomentQueryEndpoint implements CustomEndpoint {

    private final MomentFinder momentFinder;

    private final MomentPublicQueryService momentPublicQueryService;

    private final AnonymousViewScopeService viewScopeService;

    private final MomentLocationPrivacy locationPrivacy;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "api.moment.halo.run/v1alpha1/Moment";
        return SpringdocRouteBuilder.route()
            .GET("moments", this::listMoments,
                builder -> {
                    builder.operationId("queryMoments")
                        .description("Lists moments.")
                        .tag(tag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(MomentVo.class))
                        );
                    MomentPublicQuery.buildParameters(builder);
                }
            )
            .GET("moments/{name}", this::getMomentByName,
                builder -> builder.operationId("queryMomentByName")
                    .description("Gets a moment by name.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .in(ParameterIn.PATH)
                        .name("name")
                        .description("Moment name")
                        .required(true)
                    )
                    .response(responseBuilder()
                        .implementation(MomentVo.class)
                    )
            )
            .GET("view-scope", this::getViewScope,
                builder -> builder.operationId("queryMomentViewScope")
                    .description("Gets the visible scope of moments for the current requester.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementation(ViewScopeVo.class)
                    )
            )
            .build();
    }

    private Mono<ServerResponse> getViewScope(ServerRequest request) {
        return viewScopeService.getViewScope()
            .flatMap(viewScope -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(viewScope)
            );
    }


    private Mono<ServerResponse> getMomentByName(ServerRequest request) {
        final var name = request.pathVariable("name");
        return momentFinder.get(name)
            .switchIfEmpty(Mono.error(() -> new NotFoundException("Moment not found")))
            .flatMap(moment -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .bodyValue(moment)
            );
    }

    private Mono<ServerResponse> listMoments(ServerRequest request) {
        MomentPublicQuery query = new MomentPublicQuery(request.exchange());
        var listOptions = query.toListOptions();
        // 只有按位置筛的时候才追加可见条件：无条件加会把「没填位置」的瞬间也挡掉，
        // 而这里要挡的只是「位置填了但设成私密」的那些
        var options = query.hasLocationFilter()
            ? locationPrivacy.restrictLocationQuery(listOptions)
            : Mono.just(listOptions);
        return options
            .flatMap(opts -> momentPublicQueryService.list(opts, query.toPageRequest()))
            .flatMap(result -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result)
            );
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.moment.halo.run/v1alpha1");
    }
}