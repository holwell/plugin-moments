package run.halo.moments;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.app.infra.AnonymousUserConst;

import java.security.Principal;
import java.time.Instant;
import java.util.function.Predicate;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.greaterThan;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.or;


/**
 * The default implementation of {@link ReactiveQueryMomentPredicateResolver}.
 *
 */
@Component
public class DefaultQueryMomentPredicateResolver implements ReactiveQueryMomentPredicateResolver {

    private final AnonymousViewScopeService viewScopeService;

    public DefaultQueryMomentPredicateResolver(AnonymousViewScopeService viewScopeService) {
        this.viewScopeService = viewScopeService;
    }

    @Override
    public Mono<Predicate<Moment>> getPredicate() {
        Predicate<Moment> predicate = moment -> moment.isApproved()
            && !ExtensionUtil.isDeleted(moment);
        Predicate<Moment> visiblePredicate = Moment::isPubliclyVisible;
        return currentUserName()
            .map(username -> predicate.and(
                visiblePredicate.or(moment -> username.equals(moment.getSpec().getOwner())))
            )
            .switchIfEmpty(Mono.defer(() -> viewScopeService.resolveStartTime()
                .map(startTime -> predicate.and(visiblePredicate).and(inScope(startTime)))
                .defaultIfEmpty(predicate.and(visiblePredicate))));
    }

    @Override
    public Mono<ListOptions> getListOptions() {
        var listOptions = new ListOptions();
        var fieldQuery = and(
            isNull("metadata.deletionTimestamp"),
            equal("spec.approved", Boolean.TRUE)
        );
        var visibleQuery = equal("spec.visible", Moment.MomentVisible.PUBLIC.name());
        return currentUserName()
            .map(username -> and(fieldQuery,
                or(visibleQuery, equal("spec.owner", username)))
            )
            .switchIfEmpty(Mono.defer(() -> viewScopeService.resolveStartTime()
                .map(startTime -> and(and(fieldQuery, visibleQuery),
                    greaterThan("spec.releaseTime", startTime.toString(), true)))
                .defaultIfEmpty(and(fieldQuery, visibleQuery))))
            .map(query -> {
                listOptions.setFieldSelector(FieldSelector.of(query));
                return listOptions;
            });
    }

    /**
     * 未登录访客的范围判定：发布时间不早于范围起点。
     */
    private static Predicate<Moment> inScope(Instant startTime) {
        return moment -> {
            Instant releaseTime = moment.getSpec().getReleaseTime();
            return releaseTime != null && !releaseTime.isBefore(startTime);
        };
    }

    Mono<String> currentUserName() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Principal::getName)
            .filter(name -> !AnonymousUserConst.isAnonymousUser(name));
    }
}
