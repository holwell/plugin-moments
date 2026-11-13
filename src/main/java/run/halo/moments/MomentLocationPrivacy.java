package run.halo.moments;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.or;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.index.query.Condition;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.moments.vo.MomentVo;

/**
 * 位置隐私：只有「对外可见」的位置才允许匿名访客读到。
 *
 * <p>主题端不渲染位置只是不画，接口里的 {@code spec.location} 还在，任何人 curl 一下就拿到了。
 * 按位置筛选更是个探针——返回条数就能反推某条瞬间在不在某个城市。所以两件事都必须在插件里做：
 * 匿名读到的位置会被抹掉，匿名按位置筛选时会强制要求位置对外可见。</p>
 *
 * <p>登录用户原样返回：作者自己登录着看的时候位置永远可见，方便回头核对当时记没记对地方。</p>
 */
@Component
@RequiredArgsConstructor
public class MomentLocationPrivacy {

    private final AnonymousViewScopeService viewScopeService;

    /**
     * 匿名访客拿到的瞬间抹掉不可见的位置；登录用户原样返回。
     */
    public Mono<MomentVo> mask(MomentVo moment) {
        return viewScopeService.isAnonymous()
            .map(anonymous -> Boolean.TRUE.equals(anonymous) ? maskLocation(moment) : moment);
    }

    /**
     * 按位置筛选时追加的条件：匿名请求只筛「对外可见」的位置。
     *
     * <p>少了这一条，位置虽然不显示，但 {@code ?region=杭州} 的返回条数照样能证明
     * 某条瞬间在杭州——筛选本身就是泄露。</p>
     */
    public Mono<ListOptions> restrictLocationQuery(ListOptions options) {
        return viewScopeService.isAnonymous()
            .map(anonymous -> {
                if (!Boolean.TRUE.equals(anonymous)) {
                    return options;
                }
                var selector = options.getFieldSelector();
                var query = selector == null
                    ? locationVisibleQuery()
                    : and((Condition) selector.query(), locationVisibleQuery());
                options.setFieldSelector(FieldSelector.of(query));
                return options;
            });
    }

    /**
     * 位置对外可见。老数据没有 {@code visible} 字段，语义上等同公开，
     * 与主题模板里的 {@code loc.visible != false} 是同一个口径。
     */
    static Condition locationVisibleQuery() {
        return or(equal("spec.location.visible", Boolean.TRUE),
            isNull("spec.location.visible"));
    }

    private MomentVo maskLocation(MomentVo moment) {
        var spec = moment.getSpec();
        if (spec == null || spec.getLocation() == null) {
            return moment;
        }
        if (!Boolean.FALSE.equals(spec.getLocation().getVisible())) {
            return moment;
        }
        // 不能就地改 spec：它就是扩展缓存里的那个对象，改了会把缓存一起污染掉
        var masked = new Moment.MomentSpec();
        BeanUtils.copyProperties(spec, masked);
        masked.setLocation(null);
        moment.setSpec(masked);
        return moment;
    }
}
