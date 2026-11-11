# plugin-moments

Halo 2.0 的瞬间管理插件，提供一个轻量级的内容发布功能，支持发布图文、视频、音频等内容。

![Preview](./images/plugin-moments-preview.png)

> 本仓库是 [halo-sigs/plugin-moments](https://github.com/halo-sigs/plugin-moments) 的 fork，在官方版基础上增加了**瞬间位置**。下面「本 fork 新增」与「更新日志」是唯一的两处差异说明，再往后的功能特性、安装、主题适配等章节沿用官方文档。

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

## 更新日志

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
