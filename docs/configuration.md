# 配置教程

先定义“谁用什么背包”，再定义背包的每一页，最后绑定技能。运行目录是 `plugins/Accessory/`；示例文件只在对应目录第一次创建时生成。

## 1. 默认背包

```yaml
# profiles/default.yml
inventory:
  title: "<green>饰品 {page}/{max_page}"
  pages: [default, extra]
rules:
  open: true
  equip: true
  unequip: true
  attributes: true
  skills: true
  pages: []
  disabled-slots: {}
```

`inventory.pages` 是布局名称列表：第一个元素是第 1 页，第二个是第 2 页，最多 100 页。每个元素引用 `layouts/<名称>.yml`。不同页面可以使用不同大小，也可以复用同一个布局。标题可省略或填空字符串以使用各布局自己的标题。

## 2. 群体使用不同背包

```yaml
# profiles/warrior.yml
enabled: true
priority: 20
match:
  permissions: [group.warrior]
  worlds: [rpg]
inventory:
  title: "<red>战士装备 {page}/{max_page}"
  pages: [warrior, extra]
rules:
  skills: true
  disabled-slots:
    "2": [1]
```

创建 `layouts/warrior.yml` 后，此群体将使用战士布局作为第一页。

匹配顺序：API 指定的会话 Profile → 优先级最高的匹配 Profile → default。优先级相同按文件名排序。所有列出的权限都必须满足；世界列表命中其中一个即可；世界与权限条件同时满足才匹配。空列表不限制该条件。非 default 且没有匹配条件的启用 Profile 会匹配所有玩家。

其他 Profile 继承 default，只覆盖写出的字段；页面列表整体替换，`disabled-slots` 按页替换列表。不会同时叠加多个群体 Profile。示例 `vip.yml` 和 `arena.yml` 默认关闭，改为 `enabled: true` 后参与选择。

`rules.pages: []` 表示允许所有页面；`rules.pages: [1]` 表示仅可见第 1 页。页码从 1 开始，槽位从 0 开始。被隐藏的页面或被禁用的槽位不会生效，物品继续保存在原位置。切换 Profile 共用同一套玩家物品，不会凭空复制出独立仓库。

换世界立即刷新；权限变化每秒检查，也可调用 `profiles().refresh(player)` 立即重新解析。规则改变会关闭当前窗口并重新构建效果。API 指派与封禁在退出时清理，适合小游戏；长期群体关系由权限系统维护。

## 3. 定义页面布局

```yaml
# layouts/warrior.yml
size: 27
title: "<red>战士装备"
slots:
  "10":
    lore: ["[Warrior Ring]"]
    permission: accessory.slot.warrior
  "12":
    lore: ["[Necklace]"]
frame:
  next:
    slots: [26]
    type: ARROW
    name: "<gray>下一页"
    action: next_page
disabled-slot:
  item:
    type: RED_STAINED_GLASS_PANE
    name: "<red>槽位已禁用"
```

大小为 9–54，按 9 的倍数调整；省略大小、标题时使用 `config.yml → inventory.size/title`。未定义在 `slots` 的位置不能装备。Lore 列表命中任意关键词即可，空列表允许任意物品。槽位权限检查背包所有者，管理员的权限不会代替所有者通过装备限制。

`frame` 的动作是 `none / previous_page / next_page / command`。命令使用 `command.console` 与 `command.player` 字符串或列表，变量包括 `{player}`、`{uuid}`、`{page}`、`{slot}`。按钮点击在事件结束后执行。只读窗口允许翻页，禁止命令按钮与物品修改。

装饰仅填充空位，不覆盖真实饰品。批量拖拽按整批结果校验数量。上方背包双击收集、创造克隆和下方 Shift 自动塞入被禁用；手动放入、取出、快捷装备、数字键与副手交换走统一规则。

## 4. 选择属性来源

```yaml
attribute:
  provider: none
  vanilla: true
  lore:
    enable: false
    mappings:
      health:
        attribute: minecraft:max_health
        keywords: [生命, health]
```

`provider` 只能是 `none / AuraSkills / AttributePlus / BlancAttribute` 之一。多个插件同时安装也只读取指定来源；不支持列表或 auto。选定插件缺失会拒绝启动/本次重载，不自动回退。

`vanilla` 与外部来源独立：仅原版为 none + true，全部关闭为 none + false。Lore 解析额外受 `attribute.lore.enable` 控制，关闭它仍可使用物品自带的原版 AttributeModifiers。支持正负数和倍率。标签、技能有各自生命周期。

## 5. 绑定 MythicMobs 技能

```yaml
# skills/rings.yml
items:
  fire_ring:
    name: "火焰戒指"
    skills:
      - id: attack
        skill: RingFire
        trigger: onAttack
        target: targeted
        cooldown: 5
        cd: 1
        cd-format: "火焰 {cd}s"
```

识别优先使用物品 `acc_item_id`，也可显式配置显示名。颜色格式在名称匹配时被去除。不同文件按文件名读取；重复饰品 ID、重复显示名、错误触发器或目标会报错，不会悄悄覆盖。

| 字段 | 含义 |
| --- | --- |
| id | 稳定技能 ID；省略时为 skill@trigger；同一饰品内必须唯一 |
| skill | MythicMobs 中技能本体的名称 |
| trigger | onAttack、onCriticalHit、onDamaged、onShoot、onKill、onDeath、onTimer、onSkillCast、onInteract |
| target | self、targeted、attacker、projectile、none；可省略，按触发器选默认目标 |
| period | onTimer 的周期，单位 tick，默认 20 |
| cooldown | 成功释放后的冷却秒数，默认 0 |
| cd、cd-format | 冷却显示位置 1–10 与格式；支持 {cd}/{max}/{skill} |
| forcesync、cancelevent | 仅 onDeath；同步释放、成功释放免死技能时取消死亡 |

技能 Conditions 写在 MythicMobs 技能本体中，饰品配置不接受未实现的 conditions 字段。饰品自身释放技能不会递归触发 onSkillCast；伤害链也有施法重入保护。

onInteract 仅主手右键，尊重物品使用禁止；快捷装备消耗的交互不会重复释放技能。onShoot 使用一次投射物发射事件。onKill 和暴击将受害者作为 Mythic trigger，保留目标占位符语义。

同种饰品的同一能力只注册一次并共用冷却。换槽、重排列表、卸下再装备和切换 Profile 不清空冷却。默认退出重置，可用 `skills.cooldowns.reset-on-quit: false` 保留当前插件运行期的剩余冷却；冷却不跨服务器重启持久化。死亡未真正取消时卸载能力，重生恢复。

主配置 `skills.enabled` 控制整个技能系统，`skills.triggers.cancelled-damage` 决定取消的伤害是否触发 onDamaged。没有 MythicMobs 时技能系统不启动，饰品背包仍可使用。系统自行管理定时器、监听器和冷却占位符注册/释放。

## 6. 数量、死亡、debug 与存储

数量限制在 `inventory.item-limits` 中，可以按 ID、名称、Lore 或材料定义最大装备份数。隐藏或不符合当前 Profile 的槽位不会消耗有效装备数量。

`lifecycle.death` 支持 keep、drop、follow-keep-inventory。死亡事件可覆盖单次策略；已经取消的死亡不清理/掉落饰品。keep 保留存储、重生恢复效果；drop 清空并加入死亡掉落列表，包含暂时隐藏的物品。

`debug.enabled` 为总开关，`debug.categories` 选择 lifecycle/inventory/skills/hooks/storage，`debug.interval-ticks` 控制同类事件节流。正常日志只保留启动摘要和错误；debug 开启时失败日志附异常堆栈，不再输出 hook 的逐步初始化刷屏。

`storage.type` 只接受 yml、mysql、only-ram。RAM 退出即清空，不保存，普通配置重载保留本局缓存。持久化模式拒绝把读取异常当成空背包；写入失败保留最新快照，在后续保存时重试。YML 使用临时文件替换，停服等待已排队的写入再关闭连接。

当前数据格式 2 使用固定页号/槽号：每页预留 54 个位置。布局缩小或删除某个 Profile 后，隐藏物品仍保留；重新开放对应页面/槽位可以取回。替换页面列表中的布局只改变该页规则，交换列表顺序不会把物品搬到另一页。

`/accessory reload` 先验证全部配置，然后关闭窗口、应用规则并刷新在线玩家。更换存储类型/连接时不自动跨库搬运数据；旧存储刷新失败会中止切换并保持旧存储可用。AuraSkills 特性注册在启动时执行，修改 stats.yml 后重启。

## 当前版本边界

旧的 pages/layout.by-page、顶层数据库/装备规则别名、skill/ 目录、dun_item_id、signature、旧加载器和混在库存服务里的技能 API 已移除。页面槽位节点现在是 `slots`。请使用当前示例配置，旧键会给出明确错误。

只有已有物品数据保留迁移：首次读旧格式前，在 `storage.legacy-page-sizes` 依次填写旧页面大小，例如 [9, 27]。不要用新布局大小代替旧值。未知形状会拒绝加载；成功保存为格式 2 后不再迁移。迁移配置与存储读写回归经过模拟，真实 Paper 的带 PDC/NBT 物品及第三方插件仍需要实服联调。
