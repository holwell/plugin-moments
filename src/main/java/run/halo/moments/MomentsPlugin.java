package run.halo.moments;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class MomentsPlugin extends BasePlugin {

    private final SchemeManager schemeManager;
    private final ApplicationEventPublisher eventPublisher;

    public MomentsPlugin(PluginContext pluginContext, SchemeManager schemeManager,
        ApplicationEventPublisher eventPublisher) {
        super(pluginContext);
        this.schemeManager = schemeManager;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void start() {
        schemeManager.unregister(Scheme.buildFromType(Moment.class));
        schemeManager.register(Moment.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<Moment, String>multi("spec.tags", String.class)
                .indexFunc(moment -> {
                    var tags = moment.getSpec().getTags();
                    return tags == null ? Set.of() : tags;
                }));
            indexSpecs.add(IndexSpecs.<Moment, String>single("spec.owner", String.class)
                .indexFunc(moment -> moment.getSpec().getOwner()));
            indexSpecs.add(IndexSpecs.<Moment, Instant>single("spec.releaseTime", Instant.class)
                .indexFunc(moment -> moment.getSpec().getReleaseTime()));
            indexSpecs.add(IndexSpecs.<Moment, String>single("spec.visible", String.class)
                .indexFunc(moment -> {
                    var visible = moment.getSpec().getVisible();
                    return visible == null ? null : visible.name();
                }));
            indexSpecs.add(IndexSpecs.<Moment, Boolean>single("spec.approved", Boolean.class)
                .indexFunc(moment -> moment.getSpec().getApproved()));
            // 位置的两个字段各自建索引：地区用于「这个城市的所有瞬间」，
            // 地点用于「这一个地方的所有瞬间」。两者都取不到时返回 null，
            // Halo 的索引会跳过 null 值，所以没填位置的瞬间不会占用索引项。
            indexSpecs.add(IndexSpecs.<Moment, String>single("spec.location.region", String.class)
                .indexFunc(moment -> Optional.ofNullable(moment.getSpec().getLocation())
                    .map(Moment.MomentLocation::getRegion)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<Moment, String>single("spec.location.place", String.class)
                .indexFunc(moment -> Optional.ofNullable(moment.getSpec().getLocation())
                    .map(Moment.MomentLocation::getPlace)
                    .orElse(null)));
            // 位置的对外可见性：匿名按位置筛选时要靠它把「设成私密」的位置挡在外面，
            // 否则筛选结果的条数就能反推位置。没填位置的瞬间取不到值，索引会跳过。
            indexSpecs.add(
                IndexSpecs.<Moment, Boolean>single("spec.location.visible", Boolean.class)
                    .indexFunc(moment -> Optional.ofNullable(moment.getSpec().getLocation())
                        .map(Moment.MomentLocation::getVisible)
                        .orElse(null)));
            indexSpecs.add(
                IndexSpecs.<Moment, Boolean>single(
                        Moment.REQUIRE_SYNC_ON_STARTUP_INDEX_NAME, Boolean.class)
                    .indexFunc(moment -> {
                        var observedVersion = Optional.ofNullable(moment.getStatus())
                            .map(Moment.Status::getObservedVersion)
                            .orElse(-1L);
                        if (observedVersion < moment.getMetadata().getVersion()) {
                            return Boolean.TRUE;
                        }
                        // don't care about the false case
                        return null;
                    }));
        });
        eventPublisher.publishEvent(new SchemeRegistered(this));
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(Moment.class));
    }
}
