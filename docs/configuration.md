# 配置关系

运行时配置目录为 `plugins/BlancAccessory/`。示例布局、技能文件仅在对应目录首次创建时生成，删除或重命名后不会在下次启动重新出现。

## 页面与布局

采用 ExcellentShop 的布局注册和页码映射方式，配置键沿用本项目的小写风格：

```yaml
# config.yml
pages: 3
layout:
  paginated: true
  by-page:
    "0": default
    "2": extra
```

- `pages` 是唯一页数来源，范围 1–100。布局文件不会自动扩展页数。
- `layouts/default.yml` 注册为 `default`，`layouts/extra.yml` 注册为 `extra`，名称不区分大小写。
- `by-page.0` 是默认布局；示例中第 1、3 页使用 `default`，第 2 页使用 `extra`。
- `paginated: false` 时所有页面使用默认布局，其他映射暂不生效。
- 页内槽位从 0 开始；页码从 1 开始，映射中的 0 只表示默认布局。
- 多页可以复用布局，饰品内容仍按页独立存储。布局大小可不同，存储偏移按前面各页的实际大小累计。
- 指定页的布局不存在时记录警告并采用默认布局；默认布局缺失会阻止本次配置应用。重复的布局 ID 会报错。

布局可以独立覆盖 `title`、`size`；省略时使用 `config.yml` 的对应值。`size` 限制为 9–54，非 9 的倍数会向下调整并记录警告。

```yaml
# layouts/default.yml
size: 9
title: "<green>饰品 {page}/{max_page}"
Accessory:
  "1":
    lore: ["[Ring]"]
    permission: "accessory.slot.ring"  # 可省略
frame:
  previous:
    slots: [0]
    type: ARROW
    name: "<gray>上一页"
    action: previous_page
  next:
    slots: [8]
    type: ARROW
    name: "<gray>下一页"
    action: next_page
disabled-slot:
  item:
    type: RED_STAINED_GLASS_PANE
    name: "<red>槽位已禁用"
```

`Accessory` 配置可装备槽位以及 Lore/权限要求；未配置的槽位不能装备。`frame` 配置装饰或按钮，其 `slots` 对装备不可用，不应与 `Accessory` 槽位重叠。没有 `frame` 就没有边框，也没有隐式锁定槽位。

按钮 `action` 可为 `none`（默认）、`previous_page`、`next_page` 或 `command`。命令按钮使用 `command.console` / `command.player`，支持字符串或列表，变量为 `{player}`、`{uuid}`、`{page}`、`{slot}`。物品标题和 Lore 支持 MiniMessage、`{page}` 与 `{max_page}`。

## 饰品、技能与数量限制

三项配置独立负责不同判断：

| 配置 | 判断内容 | 关联方式 |
| --- | --- | --- |
| `layouts/*.yml → Accessory.<槽位>` | 物品是否允许放入该槽位 | Lore 关键词与权限 |
| `skill/*.yml → items.<ID>` | 装备后触发什么技能 | 饰品 PDC ID，或配置中的物品名称 |
| `config.yml → item-limits.<规则>` | 同一物品允许装备/生效多少份 | ID、名称、Lore 或材料 |

例如：槽位要求 `[Ring]`，技能 ID 为 `example_ring`，数量限制填写 `id: example_ring, max: 1`。仅创建技能配置不会自动创建可装备槽位。

`skill/` 中 `.yml`、`.yaml` 按文件名排序，各读取一次；同一饰品 ID 在多个文件中的技能列表会合并。同名识别按后加载的映射覆盖，建议为不同物品使用不同名称。最终技能签名取所有文件 `signature` 的最大值。

属性提供者按 BlancAttribute、AttributePlus、AuraSkills、原版的顺序自动选择。`vanilla-lore-attributes` 仅用于原版模式；旧的 `attribute-plus.enable` 并未参与选择，现已从示例中删除。

## 重载与存储

`/accessory reload` 先准备新配置和布局，再关闭并保存旧界面，随后应用配置并刷新在线玩家属性和技能。旧布局发起的异步打开或延迟刷新会失效。AuraSkills 的护盾脱战时间与恢复比例均从当前配置读取。

数据库配置不变时复用缓存和连接；`only-ram` 的普通配置重载会保留内存。更换存储类型或 MySQL 连接时重建存储，不自动迁移数据。`stats.yml` 的 AuraSkills 注册及插件集成初始化发生在启动阶段，改动后重启。

保存格式仍是按页拼接的槽位数组。调整已有页面大小、顺序或减少页数会改变槽位位置或可见范围；迁移布局时先保持原页数和每页大小。

## 从旧页面配置迁移

1. 把 `page/page1.yml`、`page/page2.yml` 移为 `layouts/default.yml`、`layouts/extra.yml`，或使用其他布局名称。
2. 删除布局中的 `page` 字段，在主配置显式填写 `pages` 与 `layout.by-page`。
3. 将按钮 `drag` 改为 `action`，将 `pre_page` 改为 `previous_page`；`next_page`、`command` 的动作名不变。
4. 旧主配置中的 `Accessory`、`Accessory.page_N`、`frame`、`frame.page_N`、`disabled-slot` 及独立翻页按钮配置已不再读取，需把所需设置移入布局。

没有旧格式回退或自动迁移；运行时只读取 `layouts/` 下的布局。
