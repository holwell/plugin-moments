package run.halo.moments.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/**
 * 未登录访客的展示范围。
 *
 * <p>{@code enabled}、{@code mode} 等字段与请求者身份无关，供控制台展示当前配置；
 * {@code restricted} 表示当前请求者是否真的受限，登录用户恒为 {@code false}，
 * 前台可据此决定是否展示范围提示。</p>
 */
@Data
@Builder
@Schema(name = "ViewScope")
public class ViewScopeVo {

    @Schema(description = "是否配置了展示范围限制，与请求者身份无关")
    private boolean enabled;

    @Schema(description = "限制方式：NONE / COUNT / SINCE / RANGE")
    private String mode;

    @Schema(description = "按数量限制时的条数")
    private Integer count;

    @Schema(description = "按时间节点限制时的节点")
    private Instant since;

    @Schema(description = "按时间范围限制时的数值")
    private Integer rangeValue;

    @Schema(description = "按时间范围限制时的单位：DAY / MONTH / YEAR")
    private String rangeUnit;

    @Schema(description = "实际生效的起始时间，按数量模式下为第 N 条瞬间的发布时间")
    private Instant startTime;

    @Schema(description = "当前请求者是否受该限制约束，登录用户恒为 false")
    private boolean restricted;

    @Schema(description = "供前台展示的提示文案")
    private String text;
}
