# REST API 文档

本文档介绍 plugin-moments 提供的公共 REST API。

> **在线查看完整 Swagger 文档**：访问 [Swagger Editor](https://editor.swagger.io/)，点击左上角 **File → Import URL**，输入以下地址即可：

```
https://raw.githubusercontent.com/halo-sigs/plugin-moments/refs/heads/main/api-docs/openapi/v3_0/momentsApi.json
```

## 公共 API（匿名可访问）

此插件提供了一组公共、匿名、只读的 JSON API，位于 `api.moment.halo.run/v1alpha1`，方便使用 React / Vue / Svelte 等前端框架构建客户端渲染瞬间列表的主题使用。

### 端点列表

| 端点 | 方法 | 说明 |
| ---- | ---- | ---- |
| `/apis/api.moment.halo.run/v1alpha1/moments` | `GET` | 分页列出瞬间，支持 `page`、`size`、`tag`、`ownerName`、`startDate`、`endDate`、`sort` 查询参数 |
| `/apis/api.moment.halo.run/v1alpha1/moments/{name}` | `GET` | 根据 `metadata.name` 获取单个瞬间详情 |
| `/apis/api.moment.halo.run/v1alpha1/view-scope` | `GET` | 获取未登录访客的展示范围与提示文案 |

> 前两个端点对**未登录访客**会自动应用「未登录展示范围」：列表只返回范围内的瞬间，详情若超出范围则返回 `404`。登录用户不受影响。

### 查询瞬间列表

`/apis/api.moment.halo.run/v1alpha1/moments`

**参数**：

| 参数 | 类型 | 说明 |
| ---- | ---- | ---- |
| `page` | `int` | 分页页码，从 1 开始 |
| `size` | `int` | 分页条数 |
| `tag` | `string` | 标签名称，用于筛选 |
| `ownerName` | `string` | 创建者用户名 name |
| `startDate` | `string` | 开始时间，通过时间区间筛选发布时间 |
| `endDate` | `string` | 结束时间 |
| `sort` | `string[]` | 排序字段，格式为 `字段名,排序方式`，排序方式可选值为 `asc` 或 `desc`，如 `spec.releaseTime,desc` |

**返回值类型**：`ListResult<MomentVo>`

### 查询瞬间详情

`/apis/api.moment.halo.run/v1alpha1/moments/{name}`

**参数**：

| 参数 | 类型 | 说明 |
| ---- | ---- | ---- |
| `name` | `string` | 瞬间的唯一标识 name |

**返回值类型**：`MomentVo`

超出未登录展示范围时返回 `404`：

```json
{
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "Moment not found"
}
```

### 查询展示范围

`/apis/api.moment.halo.run/v1alpha1/view-scope`

无需任何参数，返回站点当前配置的「未登录展示范围」。前端可据此决定是否展示「作者只展示近 N 条 / 近 x 个月」这类提示。

**返回值类型**：`ViewScope`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `enabled` | `boolean` | 是否配置了展示范围，与请求者身份无关 |
| `mode` | `string` | `NONE` / `COUNT` / `SINCE` / `RANGE`，只有对应模式的字段有意义 |
| `count` | `int` | `COUNT` 模式的条数 |
| `since` | `string` | `SINCE` 模式的起始节点 |
| `rangeValue` | `int` | `RANGE` 模式的数值 |
| `rangeUnit` | `string` | `RANGE` 模式的单位：`DAY` / `MONTH` / `YEAR` |
| `startTime` | `string` | 实际生效的起始时间，`COUNT` 模式下为第 N 条瞬间的发布时间 |
| `restricted` | `boolean` | **当前请求者**是否真的受限，登录用户恒为 `false` |
| `text` | `string` | 可直接展示的提示文案，`enabled` 为 `false` 时为空字符串 |

示例（未配置限制）：

```json
{
    "enabled": false,
    "mode": "NONE",
    "count": null,
    "since": null,
    "rangeValue": null,
    "rangeUnit": null,
    "startTime": null,
    "restricted": false,
    "text": ""
}
```

示例（按数量限制 20 条，未登录访客请求）：

```json
{
    "enabled": true,
    "mode": "COUNT",
    "count": 20,
    "since": null,
    "rangeValue": null,
    "rangeUnit": null,
    "startTime": "2026-10-02T08:15:30Z",
    "restricted": true,
    "text": "作者只展示最近 20 条瞬间"
}
```

> 用 `restricted` 而不是 `enabled` 判断是否展示提示：登录用户访问同一个接口时 `enabled` 仍为 `true`，但 `restricted` 为 `false`。
