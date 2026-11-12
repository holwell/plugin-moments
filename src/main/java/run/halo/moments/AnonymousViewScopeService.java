package run.halo.moments;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.app.infra.AnonymousUserConst;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.moments.vo.ViewScopeVo;

/**
 * 未登录访客的展示范围。
 *
 * <p>范围策略只存在于配置中，在读取时派生，绝不改写瞬间的 {@code spec.visible}。
 * 因此把「近半年」改成「近一年」时，之前看不到的瞬间会立刻恢复，不存在数据被污染的问题。</p>
 */
@Component
@RequiredArgsConstructor
public class AnonymousViewScopeService {

    static final String SETTING_GROUP = "viewScope";

    static final String MODE_NONE = "NONE";
    static final String MODE_COUNT = "COUNT";
    static final String MODE_SINCE = "SINCE";
    static final String MODE_RANGE = "RANGE";

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final ReactiveSettingFetcher settingFetcher;

    private final ReactiveExtensionClient client;

    /**
     * 获取当前请求者视角下的展示范围。登录用户恒为不受限。
     */
    public Mono<ViewScopeVo> getViewScope() {
        return getSetting()
            .flatMap(setting -> {
                if (!setting.enabled()) {
                    return Mono.just(buildVo(setting, false, null));
                }
                return isAnonymous()
                    .flatMap(anonymous -> {
                        if (!anonymous) {
                            return Mono.just(buildVo(setting, false, null));
                        }
                        return resolveStartTime(setting)
                            .map(startTime -> buildVo(setting, true, startTime))
                            .defaultIfEmpty(buildVo(setting, true, null));
                    });
            });
    }

    /**
     * 解析匿名访客可见范围的起始时间。
     *
     * <p>未配置限制、当前请求者不受限，或按数量模式下瞬间总数不足 N 条时返回空，
     * 表示无需追加任何时间条件。</p>
     */
    public Mono<Instant> resolveStartTime() {
        return getSetting()
            .filter(ViewScopeSetting::enabled)
            .flatMap(setting -> isAnonymous()
                .filter(Boolean::booleanValue)
                .flatMap(anonymous -> resolveStartTime(setting)));
    }

    /**
     * 当前请求者是否为未登录访客。
     */
    public Mono<Boolean> isAnonymous() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Principal::getName)
            .map(AnonymousUserConst::isAnonymousUser)
            .defaultIfEmpty(true);
    }

    private Mono<Instant> resolveStartTime(ViewScopeSetting setting) {
        return switch (setting.mode()) {
            case MODE_SINCE -> Mono.justOrEmpty(setting.since());
            case MODE_RANGE -> Mono.justOrEmpty(setting.rangeStartTime());
            case MODE_COUNT -> countCutoff(setting.count());
            default -> Mono.empty();
        };
    }

    /**
     * 取第 N 条最新公开瞬间的发布时间作为截止点。
     *
     * <p>用「截止时间」而不是「限制页数」，可以让列表的 total 与分页自然收敛到 N 条，
     * 不会出现翻到后面几页全为空的情况。</p>
     */
    private Mono<Instant> countCutoff(Integer count) {
        if (count == null || count <= 0) {
            return Mono.empty();
        }
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(FieldSelector.of(and(
            and(isNull("metadata.deletionTimestamp"), equal("spec.approved", Boolean.TRUE)),
            equal("spec.visible", Moment.MomentVisible.PUBLIC.name())
        )));
        var pageRequest = PageRequestImpl.of(1, count,
            Sort.by("spec.releaseTime").descending());
        return client.listBy(Moment.class, listOptions, pageRequest)
            .map(result -> result.get().toList())
            .filter(moments -> moments.size() >= count)
            .map(moments -> moments.get(moments.size() - 1))
            .map(moment -> moment.getSpec().getReleaseTime())
            .filter(Objects::nonNull);
    }

    private ViewScopeVo buildVo(ViewScopeSetting setting, boolean restricted, Instant startTime) {
        return ViewScopeVo.builder()
            .enabled(setting.enabled())
            .mode(setting.mode())
            .count(setting.count())
            .since(setting.since())
            .rangeValue(setting.rangeValue())
            .rangeUnit(setting.rangeUnit())
            .startTime(startTime)
            .restricted(restricted)
            .text(setting.text())
            .build();
    }

    Mono<ViewScopeSetting> getSetting() {
        return settingFetcher.get(SETTING_GROUP)
            .map(ViewScopeSetting::from)
            .defaultIfEmpty(ViewScopeSetting.disabled());
    }

    /**
     * 展示范围配置。只按 {@code mode} 取对应字段，其余字段忽略。
     */
    record ViewScopeSetting(String mode, Integer count, Instant since, Integer rangeValue,
                            String rangeUnit) {

        static ViewScopeSetting disabled() {
            return new ViewScopeSetting(MODE_NONE, null, null, null, null);
        }

        static ViewScopeSetting from(JsonNode node) {
            var setting = new ViewScopeSetting(
                node.path("mode").asText(MODE_NONE).toUpperCase(Locale.ROOT),
                positiveOrNull(node.path("count").asInt(0)),
                parseInstant(node.path("since").asText(null)),
                positiveOrNull(node.path("rangeValue").asInt(0)),
                node.path("rangeUnit").asText("MONTH").toUpperCase(Locale.ROOT)
            );
            return setting.enabled() ? setting : disabled();
        }

        boolean enabled() {
            return switch (mode) {
                case MODE_COUNT -> count != null;
                case MODE_SINCE -> since != null;
                case MODE_RANGE -> rangeValue != null;
                default -> false;
            };
        }

        Instant rangeStartTime() {
            if (rangeValue == null) {
                return null;
            }
            var unit = switch (rangeUnit) {
                case "DAY" -> ChronoUnit.DAYS;
                case "YEAR" -> ChronoUnit.YEARS;
                default -> ChronoUnit.MONTHS;
            };
            return Instant.now().minus(rangeValue, unit);
        }

        String text() {
            return switch (mode) {
                case MODE_COUNT -> "作者只展示最近 " + count + " 条瞬间";
                case MODE_SINCE -> "作者只展示 " + DATE_FORMATTER.format(since) + " 之后的瞬间";
                case MODE_RANGE -> "作者只展示最近 " + rangeValue + unitLabel() + "内的瞬间";
                default -> "";
            };
        }

        private String unitLabel() {
            return switch (rangeUnit) {
                case "DAY" -> " 天";
                case "YEAR" -> " 年";
                default -> " 个月";
            };
        }

        private static Integer positiveOrNull(int value) {
            return value > 0 ? value : null;
        }

        private static Instant parseInstant(String text) {
            if (StringUtils.isBlank(text)) {
                return null;
            }
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException ignored) {
                // 继续尝试其它格式
            }
            var zone = ZoneId.systemDefault();
            try {
                return LocalDateTime.parse(text).atZone(zone).toInstant();
            } catch (DateTimeParseException ignored) {
                // 继续尝试其它格式
            }
            try {
                return LocalDate.parse(text).atStartOfDay(zone).toInstant();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
