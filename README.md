# plugin-moments

Halo 2.0 的瞬间管理插件，提供一个轻量级的内容发布功能，支持发布图文、视频、音频等内容。

![Preview](./images/plugin-moments-preview.png)

> 本仓库是 [halo-sigs/plugin-moments](https://github.com/halo-sigs/plugin-moments) 的 fork，在官方版基础上增加了**瞬间位置**、**列表排序**与**未登录展示范围**。下面「本 fork 新增」与「更新日志」是唯一的两处差异说明，再往后的功能特性、安装、主题适配等章节沿用官方文档。

## 本 fork 新增：瞬间位置

官方版没有位置字段。fork 在 `spec` 上加了 `location`，**地区与地点分开存**，因为两者承担不同的筛选职责：点「杭州市」要列出这个城市下的所有瞬间，点具体地点只列那一个地方。

```yaml
spec:
  location:
    region: 杭州市         # 地区，如城市名，可空
    place: 西湖区某公司     # 具体地点，可空
    visible: false         # 是否对外展示，默认私密
```

- `visible` 为 `false` 时，主题端不渲染位置、也不给筛选入口；但数据仍在索引里，**查询接口本身不拦**——要严格隔离得在查询层再过滤一次。
- `region` 与 `place` 都空时不写 `location`，不产生一堆空对象。
- 只填 `place` 不填 `region` 也能单独筛到（地点可能跨城市重名，所以主题端点「地点」时会把 `region` 一起带上，走的是精确匹配）。

改动集中在四个文件：

| 文件 | 改动 |
| --- | --- |
| `Moment.java` | `MomentSpec` 加 `location`，新增内部类 `MomentLocation { region, place, visible }` |
| `MomentsPlugin.java` | 给 `spec.location.region` / `spec.location.place` 建索引 |
| `MomentPublicQuery.java` | 公共 REST API 支持 `?region=` / `?place=` |
| `finders/impl/MomentFinderImpl.java` | `MomentQuery`（Finder 的 Map 版本）支持 `region` / `place` |

**为什么 Finder 那层不能省**：`momentFinder.list(Map)` 只把 Map 映射到 `MomentFinderImpl.MomentQuery`，光在 `MomentPublicQuery` 里加字段，主题模板仍然查不到——模板走的是 Finder，不是 HTTP API。两层要一起改。

查询示例：

```bash
# 只看某个城市
curl "https://example.com/apis/api.moment.halo.run/v1alpha1/moments?region=杭州市"

# 精确到某个地点
curl "https://example.com/apis/api.moment.halo.run/v1alpha1/moments?region=杭州市&place=西湖区某公司"
```

新增了索引字段，装完后如果按位置查不到数据，去控制台「系统 → 索引」对 `Moment` 触发一次重建。

## 本 fork 新增：列表排序

Console 瞬间列表在「状态」筛选之后新增了「排序」按钮，四个选项：

| 选项 | 对应参数 |
| --- | --- |
| 发布时间倒序（默认） | `spec.releaseTime,desc` |
| 发布时间升序 | `spec.releaseTime,asc` |
| 创建时间倒序 | `metadata.creationTimestamp,desc` |
| 创建时间升序 | `metadata.creationTimestamp,asc` |

- 排序状态写入地址栏（`?sort=spec.releaseTime,desc`），刷新页面、复制链接都能保持；切换排序会回到第 1 页。
- 后端不需要改动：`MomentQuery` 继承 `SortableRequest`，`sort` 参数本来就支持，而且未传 `sort` 时的兜底排序就是 `spec.releaseTime desc`，所以默认选项与升级前的行为完全一致。
- 排序按钮不可清空，这与「标签」「状态」两个筛选不同——为此给 `FilterDropdown` 加了 `clearable` 属性（默认 `true`，既有用法不受影响）。

## 本 fork 新增：未登录展示范围

在「插件设置 → 瞬间 → 未登录展示范围」里可以限制**未登录访客**能看到的范围。四种模式互斥，只生效一个：

| 模式 | 说明 |
| --- | --- |
| 不限制（默认） | 与官方版行为一致，升级后不会改变现有站点 |
| 按数量 | 只展示最近发布的 N 条 |
| 按时间节点 | 只展示指定日期（00:00 起）之后发布的 |
| 按时间范围 | 只展示最近 x 天 / 月 / 年内的 |

### 关键设计：不改写数据

范围**只在读取时派生**，绝不修改 `spec.visible`。所以：

- 从「近半年」改成「近一年」，之前看不到的瞬间会**立刻全部恢复**，因为它们从来没被改过；
- 改回去又立刻收回，可以来回切；
- 时间自然流逝，范围自动滚动，不需要定时任务、不需要重建数据；
- 作者在 Console 里看到的始终是自己设置的真实状态，不会莫名发现自己半年前的瞬间变成私密。

### 受限的出口

范围条件收口在 `DefaultQueryMomentPredicateResolver` 的 `getPredicate()` 与 `getListOptions()` 里，因此下面这些出口对匿名访客**同时**受限：

| 出口 | 匿名且超出范围时 |
| --- | --- |
| 列表 REST API `.../moments` | 不返回 |
| 详情 REST API `.../moments/{name}` | 404 |
| 详情页 `/moments/{name}` | 404（附「该瞬间不在展示范围内」提示页） |
| 列表页 / 分页 / 标签页 | 不返回，标签计数同步受限 |
| RSS `/moments/rss.xml` | 不输出 |
| Finder API `momentFinder` | 不返回 |

登录用户（含作者本人）不受任何影响。

「按数量」模式取第 N 条最新公开瞬间的发布时间作为截止点，而不是限制页数——这样列表的 `total` 和分页会自然收敛到 N 条，不会翻到后面几页全是空的。

### 前台展示范围提示

范围配置和提示文案由后端统一生成，前台有两种取法：

**1. 服务端渲染（推荐）**：模板 model 里直接有 `viewScope`，`restricted` 为 `true` 表示当前访客确实受限：

```html
<p th:if="${viewScope != null and viewScope.restricted}" th:text="${viewScope.text}"></p>
<!-- 输出：作者只展示最近 20 条瞬间 / 作者只展示最近 3 个月内的瞬间 -->
```

**2. 客户端渲染**：调用匿名可读接口 `GET /apis/api.moment.halo.run/v1alpha1/view-scope`，返回结构见 [REST API 文档](./dev/rest-api.md)。

Console 列表页顶部也有一条只读提示（「未登录访客：作者只展示最近 N 条瞬间」），只展示不可编辑，修改请去插件设置页。

### 已知边界

站内搜索是唯一做不到「按身份过滤」的出口：Halo 的搜索是静态索引 + `exposed` 标记，查询时不区分访客身份。本 fork 选择**不改动索引**，因此搜索结果里仍可能出现超范围瞬间的标题与摘要，但**点进去是 404**。若需要彻底不泄露，只能把瞬间从搜索索引中移除。

## 更新日志

### 1.0.1-SNAPSHOT（fork）· 2026-11-13

- Console 瞬间列表新增排序按钮，支持发布时间 / 创建时间的升序与倒序，默认发布时间倒序，排序状态写入地址栏
- `FilterDropdown` 新增 `clearable` 属性，用于不可清空的排序场景
- 插件设置新增「未登录展示范围」分组，支持不限制 / 按数量 / 按时间节点 / 按时间范围四种互斥模式
- 新增 `AnonymousViewScopeService`：范围在读取时派生，不改写 `spec.visible`，修改设置即时生效且不污染数据
- 范围条件统一收口到 `DefaultQueryMomentPredicateResolver`，列表、详情、RSS、Finder、标签计数同时受限
- 超出范围的瞬间对匿名访客返回 404，详情页附带提示正文
- 新增匿名可读接口 `GET /apis/api.moment.halo.run/v1alpha1/view-scope`，返回范围配置与 `restricted` 标记
- 主题模板新增 `viewScope` 变量，可直接渲染「作者只展示近 N 条 / 近 x 个月」提示

### 1.0.1-SNAPSHOT（fork）· 2026-11-12

- 新增 `spec.location`：`region` / `place` / `visible` 三个字段
- 新增 `?region=` / `?place=` 查询参数，公共 REST API 与 Finder API 同步支持
- 新增 `spec.location.region` / `spec.location.place` 索引，两者都取不到时返回 `null`，Halo 的索引会跳过 `null`，没填位置的瞬间不占索引项

## 功能特性

- 支持发布图文、视频、音频等多媒体内容
- 提供标签管理功能，支持按标签筛选瞬间
- 主题端 `/moments` 列表路由和 `/moments/{name}` 详情路由
- 提供匿名可访问的公共 REST API，方便前端框架构建客户端渲染瞬间列表
- RSS 订阅支持，路由为 `/moments/rss.xml`
- 支持将瞬间数据同步至 Halo 搜索系统
- 支持通过 MCP Server 查询、获取、发布和删除瞬间

## 安装使用

1. 下载，目前提供以下两个下载方式：
    - Halo 应用市场：<https://halo.run/store/apps/app-SnwWD>
    - GitHub Releases：访问 [Releases](https://github.com/halo-sigs/plugin-moments/releases) 下载 Assets 中的 JAR 文件。
2. 安装，插件安装和更新方式可参考：<https://docs.halo.run/user-guide/plugins>
3. 安装完成之后，访问 Console 左侧的**瞬间**菜单项，即可进行管理。
4. 前台访问地址为 `/moments`，需要注意的是，此插件需要主题提供模板（`moments.html`）才能访问 `/moments`。
5. 此插件也提供了 RSS 订阅的路由，可以访问 `/moments/rss.xml`。
6. 此插件将数据同步至 Halo 搜索，type 为 `moment.moment.halo.run`。

## MCP Server 集成

安装并启用 [Halo MCP Server](https://github.com/halo-dev/plugin-mcp-server) 1.x 后，本插件会自动提供以下 MCP 工具：

| 工具 | 说明 |
| --- | --- |
| `PluginMoments__list_moments` | 分页查询瞬间，支持按作者、标签、可见性、审核状态和发布时间筛选 |
| `PluginMoments__get_moment` | 按资源名称获取瞬间详情、作者和统计信息 |
| `PluginMoments__create_moment` | 以当前 MCP 访问密钥所有者的身份发布图文瞬间 |
| `PluginMoments__delete_moment` | 按资源名称永久删除瞬间，调用前必须获得用户明确确认 |

需要在 MCP Server 的访问密钥设置中选择需要开放的工具。未安装 MCP Server 时，不影响瞬间插件的其他功能。

## 主题适配

此插件为主题端提供了：

- **列表路由** `/moments`（模板 `moments.html`）和**详情路由** `/moments/{name}`（模板 `moment.html`）
- **Finder API**（`momentFinder`）：可在主题任意位置渲染瞬间列表，无需依赖路由页面
- **公共 REST API**：供前端框架构建客户端渲染瞬间列表使用

详细的主题适配文档请参考：

- [主题 API 文档](./dev/theme-api.md) — 模板路由、模板变量、Finder API、类型定义
- [REST API 文档](./dev/rest-api.md) — 公共 API 端点说明

## 开发文档

- [开发环境搭建](./dev/development.md) — 本地开发、构建、测试
