# BlancAccessory

可配置的多页饰品背包，支持 Lore 槽位规则、快捷装备、属性与 MythicMobs 技能。

- 打开背包：`/accessory`（别名 `/acc`、`/acre`）。
- 快捷装备：潜行 + 主手右键；多个匹配槽位会显示选择菜单。
- [配置说明与旧配置迁移](docs/configuration.md)
- [架构与运行流程](docs/architecture.md)

## 配置入口

| 文件 | 职责 |
| --- | --- |
| `config.yml` | 页数、布局映射、语言、装备限制、属性设置、存储 |
| `layouts/*.yml` | 可复用的 GUI 布局、槽位规则、按钮与禁用样式 |
| `skill/*.yml` | 饰品 ID 到 MythicMobs 技能的映射 |
| `Language/*.yml` | 提示消息 |
| `stats.yml` | AuraSkills 自定义属性定义 |

页面管理参考 ExcellentShop：布局按文件名注册，`pages` 指定总页数，`layout.by-page.0` 指定默认布局，其余页码可覆盖。布局与玩家物品分开管理，多页共用布局时物品仍独立保存。

## 可选集成

属性来源自动选择：BlancAttribute → AttributePlus → AuraSkills → 原版属性。MythicMobs 提供饰品触发技能，PlaceholderAPI 提供护盾和技能冷却变量。未安装这些插件时仍可使用饰品背包。

- 物理护盾：`%absorb_current_shield%` / `%absorb_max_shield%`
- 技能冷却：`%blacc_cd_1%` ～ `%blacc_cd_10%`

## 管理命令

| 命令 | 权限 | 作用 |
| --- | --- | --- |
| `/accessory reload` | `accessory.reload` | 重载配置、布局、语言和技能，刷新在线玩家效果 |
| `/accessory clear <玩家>` | `accessory.clear` | 清空饰品 |
| `/accessory view <玩家>` | `accessory.view` | 查看、编辑在线玩家的饰品 |
| `/shield` | `accessory.shield` | 调整物理护盾；需要 AuraSkills |
| `/magicshield` | `accessory.magicshield` | 调整魔法护盾；需要 AuraSkills |

## 外部 API

```java
Accessory plugin = (Accessory) Bukkit.getPluginManager().getPlugin("BlancAccessory");
AccessoryService service = plugin.service();
service.setSlotEnabled(3, false);
service.setSlotEnabled(3, true);
service.setDisabledSlots(List.of(1, 3, 5));
```

禁用槽位 API 使用页内槽位编号，对所有页面的同编号槽位生效；样式由各布局的 `disabled-slot.item` 决定。

## 构建

使用 Java 25、Gradle 9.2.1；本地编译依赖放在 `libs/`。执行 `gradle shadowJar`，产物为 `build/libs/Blanc-Accessory.jar`。
