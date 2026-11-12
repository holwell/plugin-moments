# plugin-moments

Halo 2.0 的瞬间插件，本仓库是 [halo-sigs/plugin-moments](https://github.com/halo-sigs/plugin-moments) 的 fork。

![Preview](./images/plugin-moments-preview.png)

相对官方版，本 fork 只做了三件事：

| 功能 | 一句话 |
| --- | --- |
| [瞬间位置](#1-瞬间位置) | 地区与地点分开存，可按城市或具体地点筛选 |
| [列表排序](#2-列表排序) | 发布时间 / 创建时间的升序倒序，默认发布时间倒序 |
| [未登录展示范围](#3-未登录展示范围) | 限制匿名访客能看到的数量或时间范围，且不改写数据 |

## 安装

1. 从本仓库 [Releases](https://github.com/halo-sigs/plugin-moments/releases) 下载 JAR，或使用 Halo 应用市场。
2. 控制台「插件」→ 安装 → 启用，左侧出现「瞬间」菜单。
3. 前台地址 `/moments`（主题需提供 `moments.html`），RSS 为 `/moments/rss.xml`。

要求 Halo >= 2.26.0。

## 1. 瞬间位置

官方版没有位置字段。fork 在 `spec` 上加了 `location`，**地区与地点分开存**：点「杭州市」要列出这个城市下的所有瞬间，点具体地点只列那一个地方。

```yaml
spec:
  location:
    region: 杭州市         # 地区，如城市名，可空
    place: 西湖区某公司     # 具体地点，可空
    visible: false         # 是否对外展示，默认私密
```

```bash
# 只看某个城市
curl "https://example.com/apis/api.moment.halo.run/v1alpha1/moments?region=杭州市"

# 精确到某个地点
curl "https://example.com/apis/api.moment.halo.run/v1alpha1/moments?region=杭州市&place=西湖区某公司"
```

- `visible` 为 `false` 时主题端不渲染位置、也不给筛选入口；但数据仍在索引里，**查询接口本身不拦**。
- `region` 与 `place` 都空时不写 `location`，不产生空对象。
- 只填 `place` 不填 `region` 也能单独筛到（主题端点「地点」时会把 `region` 一起带上，走精确匹配）。
- 改动集中在 `Moment.java`、`MomentsPlugin.java`（建索引）、`MomentPublicQuery.java`、`MomentFinderImpl.java`。Finder 那层不能省——模板走的是 Finder，不是 HTTP API。

> 新增了索引字段，装完后若按位置查不到数据，去控制台「系统 → 索引」对 `Moment` 触发一次重建。

## 2. 列表排序

Console 瞬间列表「状态」筛选之后新增「排序」按钮：

| 选项 | 参数 |
| --- | --- |
| 发布时间倒序（默认） | `spec.releaseTime,desc` |
| 发布时间升序 | `spec.releaseTime,asc` |
| 创建时间倒序 | `metadata.creationTimestamp,desc` |
| 创建时间升序 | `metadata.creationTimestamp,asc` |

- 排序写入地址栏（`?sort=spec.releaseTime,desc`），刷新与分享都保持；切换后回到第 1 页。
- 后端零改动：`MomentQuery` 继承 `SortableRequest`，未传 `sort` 时的兜底排序本来就是 `spec.releaseTime desc`，所以默认选项与升级前行为一致。
- 排序不可清空，为此给 `FilterDropdown` 加了 `clearable` 属性（默认 `true`，既有筛选不受影响）。

## 3. 未登录展示范围

「插件设置 → 瞬间 → 未登录展示范围」，四种模式互斥，只生效一个：

| 模式 | 说明 |
| --- | --- |
| 不限制（默认） | 与官方版一致，升级后不改变现有站点 |
| 按数量 | 只展示最近发布的 N 条 |
| 按时间节点 | 只展示指定日期（00:00 起）之后发布的 |
| 按时间范围 | 只展示最近 x 天 / 月 / 年内的 |

### 不改写数据

范围**只在读取时派生**，绝不修改 `spec.visible`。所以从「近半年」改成「近一年」时，之前看不到的瞬间会立刻全部恢复；改回去又立刻收回；时间流逝自动滚动，不需要定时任务；作者在 Console 里看到的始终是自己设置的真实状态。

### 受限的出口

条件收口在 `DefaultQueryMomentPredicateResolver`，下面这些出口对匿名访客**同时**受限：

| 出口 | 匿名且超出范围时 |
| --- | --- |
| 列表 API `.../moments` | 不返回 |
| 详情 API `.../moments/{name}` | 404 |
| 详情页 `/moments/{name}` | 404，但正文渲染 `moment-hidden.html`，**保留主题布局** |
| 列表页 / 分页 / 标签页 | 不返回，标签计数同步受限 |
| RSS `/moments/rss.xml` | 不输出 |
| Finder API `momentFinder` | 不返回 |

登录用户（含作者本人）不受任何影响。「按数量」模式取第 N 条最新公开瞬间的发布时间作为截止点，而不是限制页数，这样 `total` 与分页会自然收敛，不会翻出空页。

### 前台展示提示

文案由后端统一生成，两种取法：

```html
<!-- 服务端渲染：模板 model 里直接有 viewScope -->
<p th:if="${viewScope != null and viewScope.restricted}" th:text="${viewScope.text}"></p>
<!-- 输出：作者只展示最近 20 条瞬间 / 作者只展示最近 3 个月内的瞬间 -->
```

客户端渲染则调用匿名可读接口 `GET /apis/api.moment.halo.run/v1alpha1/view-scope`，返回结构见 [REST API 文档](./dev/rest-api.md#查询展示范围)。判断是否展示提示请用 `restricted`（登录用户恒为 `false`），不要用 `enabled`。

### 超范围详情页

匿名访客打开超范围（或已删除）瞬间的链接时，HTTP 状态码是 **404**，但正文仍走主题的布局，只把内容区替换成提示文字——插件内置了 `templates/moment-hidden.html`，主题提供同名文件即可覆盖样式：

```html
<section class="moment-hidden">
  <h1>该瞬间不在展示范围内</h1>
  <p th:if="${viewScope != null and viewScope.restricted}" th:text="${viewScope.text}"></p>
</section>
```

这样搜索引擎看到 404 会自然把旧链接移出索引，而访客看到的仍是带站点导航的正常页面。

Console 列表页顶部也有一条只读提示，修改请去插件设置页。

### 已知边界

站内搜索做不到按身份过滤（Halo 搜索是静态索引 + `exposed` 标记）。本 fork 选择**不改动索引**，因此搜索结果里仍可能出现超范围瞬间的标题与摘要，但**点进去是 404**。若要彻底不泄露，只能把瞬间移出搜索索引。

## 更新日志

### 1.0.1-SNAPSHOT · 2026-11-13

- 新增「未登录展示范围」设置与 `AnonymousViewScopeService`，范围读取时派生，不改写 `spec.visible`
- 列表、详情、RSS、Finder、标签计数统一受限，超范围瞬间对匿名访客返回 404
- 超范围详情页正文渲染 `moment-hidden.html`，保留主题布局（主题可覆盖该模板）
- 新增匿名可读接口 `GET /apis/api.moment.halo.run/v1alpha1/view-scope`
- 主题模板新增 `viewScope` 变量
- Console 列表新增排序按钮（发布时间 / 创建时间，升序 / 倒序）
- `FilterDropdown` 新增 `clearable` 属性

### 1.0.1-SNAPSHOT · 2026-11-12

- 新增 `spec.location`：`region` / `place` / `visible`
- 新增 `?region=` / `?place=` 查询参数，REST API 与 Finder 同步支持
- 新增 `spec.location.region` / `spec.location.place` 索引

## 文档

- [主题 API](./dev/theme-api.md) —— 模板路由、模板变量、Finder API、类型定义
- [REST API](./dev/rest-api.md) —— 公共 API 端点
- [开发环境](./dev/development.md) —— 本地开发、构建、测试
