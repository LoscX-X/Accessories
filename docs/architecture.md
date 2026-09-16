# 架构与总流程

## 启动

`Accessory.onEnable()` 负责组装：默认文件 → 配置快照/布局/语言 → 存储 → 共用服务 → 监听器、保存任务与可选集成。

| 组件 | 职责 |
| --- | --- |
| `ConfigFiles` | 首次资源初始化、YAML 文件发现、读取错误日志 |
| `AccessorySettings` | 主配置常用设置的只读快照 |
| `PageSettings` | 显式页数、默认布局、逐页映射 |
| `AccessoryLayout` | 解析一个布局的槽位、图标、按钮动作 |
| `AccessoryPageManager` | 按名称注册布局，解析页码映射并计算存储偏移 |
| `AccessoryMenuController` | 打开/翻页入口与按钮动作分发 |
| `AccessoryInventoryMenu` / `Item` | 渲染库存和图标 |
| `AccessoryInventoryHolder` | 当前查看的所有者、页码和总页数 |
| `AccessoryStorage` | 管理缓存、I/O 执行器与 MySQL 连接池的生命周期 |
| `AccessoryStore` | 饰品数组的缓存、切片及读写 |
| `Accessory.refreshPlayerEffects` | 共用属性加载器，再刷新饰品技能 |

## 菜单流程

命令或翻页动作 → `AccessoryMenuController` → 关闭旧界面并保存 → 解析页码对应布局 → 异步读取该页物品 → 渲染新界面。

布局与查看状态分开：布局注册逻辑参考 ExcellentShop 的 `VirtualShopDefinition`、`VirtualShopModule.loadLayouts`，菜单切页参考 `ShopMenu` 的命名动作与统一打开入口；保留本项目的 Bukkit UI，不新增 NightCore 依赖。

点击/拖拽继续由 `AccessoryListener` 校验 Lore、权限、数量限制和禁止卸下标记；通过后更新缓存并进入统一效果刷新流程。关闭、清空、快捷装备、配置重载也复用同一刷新入口。

## 重载

先读取新配置、完整解析布局映射并准备必要的新存储；再按旧布局保存打开中的库存，应用新配置并刷新在线玩家。存储设置未改变时复用原存储。异步菜单请求保留发起时的页面管理器快照，重载后不会应用旧请求。

配置解析不依赖菜单事件，页码管理不执行命令，菜单控制器不负责读写数据库，主类不展开 MySQL 参数与资源文件细节。
