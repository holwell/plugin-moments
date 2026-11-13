package run.halo.moments;

import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.empty;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.greaterThan;
import static run.halo.app.extension.index.query.Queries.lessThan;
import static run.halo.app.extension.router.QueryParamBuildUtil.sortParameter;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToListOptions;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.router.IListRequest;
import run.halo.app.extension.router.SortableRequest;
import run.halo.app.extension.index.query.Condition;
import run.halo.app.extension.router.selector.FieldSelector;

/**
 * Query parameters for moment public APIs.
 *
 */

public class MomentPublicQuery extends SortableRequest {
    private final MultiValueMap<String, String> queryParams;

    public MomentPublicQuery(ServerWebExchange exchange) {
        super(exchange);
        this.queryParams = exchange.getRequest().getQueryParams();
    }

    @Schema(description = "Owner name.")
    public String getOwnerName() {
        return StringUtils.defaultIfBlank(queryParams.getFirst("ownerName"), null);
    }

    @Schema(description = "Moment tag.")
    public String getTag() {
        return StringUtils.defaultIfBlank(queryParams.getFirst("tag"), null);
    }

    @Schema(description = "Region of the moment location, e.g. a city.")
    public String getRegion() {
        return StringUtils.defaultIfBlank(queryParams.getFirst("region"), null);
    }

    /**
     * 地点单独查询时可能跨城市重名，所以主题端点「地点」时会把 region 一起带上；
     * 只填了地点、没填地区的瞬间也能靠它单独筛到。
     */
    @Schema(description = "Specific place name of the moment location.")
    public String getPlace() {
        return StringUtils.defaultIfBlank(queryParams.getFirst("place"), null);
    }

    /**
     * 这次查询是否按位置筛选。匿名请求要据此追加「位置对外可见」的条件，
     * 否则筛选结果本身就成了一把尺子。
     */
    public boolean hasLocationFilter() {
        return StringUtils.isNotBlank(getRegion()) || StringUtils.isNotBlank(getPlace());
    }

    @Schema
    public Instant getStartDate() {
        String startDate = queryParams.getFirst("startDate");
        return convertInstantOrNull(startDate);
    }

    @Schema
    public Instant getEndDate() {
        String endDate = queryParams.getFirst("endDate");
        return convertInstantOrNull(endDate);
    }

    /**
     * Build {@link ListOptions} from query params.
     *
     * @return a list options.
     */
    public ListOptions toListOptions() {
        var listOptions =
            labelAndFieldSelectorToListOptions(getLabelSelector(), getFieldSelector());
        Condition query = empty();
        if (StringUtils.isNotBlank(getOwnerName())) {
            query = and(query, equal("spec.owner", getOwnerName()));
        }
        if (StringUtils.isNotBlank(getTag())) {
            query = and(query, equal("spec.tags", getTag()));
        }
        if (StringUtils.isNotBlank(getRegion())) {
            query = and(query, equal("spec.location.region", getRegion()));
        }
        if (StringUtils.isNotBlank(getPlace())) {
            query = and(query, equal("spec.location.place", getPlace()));
        }

        if (getStartDate() != null) {
            query = and(query, greaterThan("spec.releaseTime", getStartDate().toString(), true));
        }
        if (getEndDate() != null) {
            query = and(query, lessThan("spec.releaseTime", getEndDate().toString(), true));
        }

        if (listOptions.getFieldSelector() != null) {
            query = and(query, (Condition) listOptions.getFieldSelector().query());
        }
        listOptions.setFieldSelector(FieldSelector.of(query));
        return listOptions;
    }

    public PageRequest toPageRequest() {
        var sort = getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by("spec.releaseTime").descending();
        }
        return PageRequestImpl.of(getPage(), getSize(), sort);
    }

    private Instant convertInstantOrNull(String timeStr) {
        return StringUtils.isBlank(timeStr) ? null : Instant.parse(timeStr);
    }

    public static void buildParameters(Builder builder) {
        IListRequest.buildParameters(builder);
        builder.parameter(sortParameter())
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("ownerName")
                .description("Owner name.")
                .implementation(String.class)
                .required(false))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("tag")
                .description("Moment tag.")
                .implementation(String.class)
                .required(false))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("region")
                .description("Region of the moment location.")
                .implementation(String.class)
                .required(false))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("place")
                .description("Specific place name of the moment location.")
                .implementation(String.class)
                .required(false))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("startDate")
                .implementation(Instant.class)
                .description("Moment start date.")
                .required(false))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("endDate")
                .implementation(Instant.class)
                .description("Moment end date.")
                .required(false))
        ;
    }

}
