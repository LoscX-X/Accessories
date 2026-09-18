# BlancAccessory

按玩家群体定制的多页饰品背包，支持装备生命周期事件、明确选择属性来源，以及独立的 MythicMobs 技能系统。

| 配置入口 | 负责什么 |
| --- | --- |
| `config.yml` | 语言、全局装备规则、属性、技能开关、死亡策略、debug、存储 |
| `profiles/*.yml` | 哪类玩家使用哪套背包：匹配条件、标题、页面列表、规则覆盖 |
| `layouts/*.yml` | 每页大小、可装备槽位、Lore/权限、装饰和按钮 |
| `skills/*.yml` | 饰品 ID/显示名对应的 MythicMobs 技能 |
| `Language/*.yml`、`stats.yml` | 提示语言、AuraSkills 自定义特性 |

例如 VIP 群体使用两页背包：

```yaml
# profiles/vip.yml
enabled: true
priority: 10
match:
  permissions: [accessory.profile.vip]
inventory:
  title: "<gold>VIP 饰品 {page}/{max_page}"
  pages: [default, extra]
```

每个名称引用一个布局文件。玩家只使用优先级最高的匹配 Profile；未匹配时使用 default。物品按固定页号/槽号保存，换组后暂时隐藏的物品继续保留。

属性配置只选择一个外部来源，原版属性独立控制：

```yaml
attribute:
  provider: none # none / AuraSkills / AttributePlus / BlancAttribute
  vanilla: true
```

只启用原版就是 `none + true`。不会自动替换缺失的属性插件。RAM 专供小游戏，退出即清空。

- [配置教程](docs/configuration.md)
- [API 与生命周期事件](docs/lifecycle-api.md)
- [职责与架构](docs/architecture.md)
- [生命周期审计、市场对比与剩余限制](docs/lifecycle-audit-2026-09-17.md)

| 命令 | 权限 | 用途 |
| --- | --- | --- |
| `/accessory` | — | 打开自己的背包 |
| `/accessory reload` | `accessory.reload` | 验证并重载配置 |
| `/accessory clear <玩家>` | `accessory.clear` | 清空饰品 |
| `/accessory view <玩家>` | `accessory.view` | 只读查看在线玩家背包 |
| `/shield`、`/magicshield` | `accessory.shield`、`accessory.magicshield` | AuraSkills 护盾控制 |

快捷装备使用潜行 + 主手右键；多个槽位匹配时显示选择选项。管理员需要编辑时可通过 API 显式使用 EDIT 模式。

Java 25、Gradle 9.2.1；外部插件编译依赖位于 `libs/`。执行 `gradle check shadowJar`，产物为 `build/libs/Accessory.jar`。存储故障回归另运行 `tests/lifecycle-probes/run.ps1`。

本次版本不再支持旧配置别名、旧内部加载器和混合在库存服务中的技能 API；请按当前文档配置。已有饰品物品数据保留格式校验和迁移能力。
