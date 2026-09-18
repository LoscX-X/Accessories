# 存储故障回归

运行 `tests/lifecycle-probes/run.ps1`，需要 Java 25 或更高版本。脚本在 `build/lifecycle-probes/<唯一 ID>/` 生成替身与结果，不修改插件数据目录。

直接编译当前生产 AccessoryStore、AccessoryStorage 和 InventorySlotLayout，只有 Bukkit 平台、SQL 边界和序列化编解码使用替身。文件写入、临时文件替换与路径失败是真实本地 I/O；闩锁控制异步顺序。不是 JDBC、真实 YAML、PDC/NBT 或 Paper 点击包测试。

当前 31 个断言验证三种模式的缓存/退出/清空、RAM 会话语义、克隆、异尺寸页、退出早于加载、重连排队、加载失败不覆盖、写失败传播与重试、删除失败恢复、YML 路径失败、损坏数据保留、重复退出、停服屏障、隐藏页面和物品格式迁移。

旧版诊断中的 GAP 断言已替换为修复后应成立的回归断言。测试失败会非零退出，不再以“成功复现缺陷”作为通过标准。完整配置/属性/Profile/事件/技能验证通过 `gradle check` 运行。
