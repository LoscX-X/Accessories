# API 与事件

`api` 只包含接口、枚举、不可变配置/结果对象；具体实现位于各 module。库存修改、打开窗口、Profile 和技能控制在服务器主线程调用。

```java
Accessory accessory = (Accessory) Bukkit.getPluginManager().getPlugin("Accessory");

// 显式选择只读或编辑；/accessory view 默认只读。
accessory.service().open(admin, owner, AccessoryViewMode.READ_ONLY);
accessory.service().open(admin, owner, AccessoryViewMode.EDIT);

// 单项封禁，不改变保存的物品。
accessory.service().setBlocked(owner.getUniqueId(), AccessoryAction.EQUIP, true);
accessory.service().setBlocked(owner.getUniqueId(), AccessoryAction.SKILLS, true);

// 会话指定背包，或恢复权限/世界匹配。
accessory.profiles().assign(owner.getUniqueId(), "vip");
accessory.profiles().clearAssignment(owner.getUniqueId());
AccessoryProfile profile = accessory.profiles().resolve(owner);

// 全局每页相同槽位的启用状态；每玩家差异通过 Profile 配置。
accessory.service().setSlotEnabled(3, false);
accessory.service().setDisabledSlots(Set.of(3, 5));

// 技能 API 与库存 API 分开。
List<AccessorySkillInfo> skills = accessory.skills().getSkills(owner);
accessory.skills().resetCooldowns(owner);

// 读取独立快照；可以读取离线持久化数据。异常通过 CompletionStage 传播。
accessory.service().read(owner.getUniqueId()).thenAccept(items -> {
    ItemStack pageTwoSlotThree = items.length > 57 ? items[57] : null;
    // 此回调可能在 I/O 线程执行，操作 Bukkit 玩家/UI 前切回服务器线程。
});
```

OPEN、EQUIP、UNEQUIP、ATTRIBUTES、SKILLS 是独立控制项，适用于背包所有者。Profile 分配和运行时封禁在退出时清理，普通配置重载保留有效的指派；被删除的 Profile 指派回到自动匹配。允许恢复后，原本隐藏的物品重新参与有效装备筛选。

读取返回的是物品克隆，数组索引为 `(page - 1) * 54 + slot`；不通过修改返回数组写库存。clear 表示内存清空已接受，持久化异步完成；失败会记录并保留重试快照。API 不自动授予管理员编辑权限，调用方自行控制其命令/权限入口。

| 事件 | 时机与用途 |
| --- | --- |
| AccessoryPlaceEvent | 放入/替换前，可取消；包含 ownerId、操作者 getPlayer、page、slot、item、replaced、cause |
| AccessoryEquipEvent | 物品成为有效装备并完成效果刷新后的通知，不可取消 |
| AccessoryUnequipEvent | 有效装备移除或失效后的通知，不可取消 |
| AccessoryDeathEvent | 未取消死亡的饰品策略执行前，可 setPolicy 修改此次策略 |
| AccessorySlotStateChangeEvent | 全局槽位启用/禁用前，可取消；批量任一项取消则整批不变 |

装备通知包含所有者、可空操作者、页/槽、物品副本、替换另一侧物品和原因。替换依次通知“卸下旧物品 → 装备新物品”。事件 getter 返回克隆；修改通知中的物品不会写库存。

JOIN、RESPAWN、RELOAD、PROFILE、GUI、QUICK_EQUIP、CLEAR、DEATH、QUIT、PLUGIN_DISABLE、SLOT_STATE、API 用于区分变化原因。退出卸下通知表示效果清理；YML/MySQL 中保存的物品仍然存在。相同状态重复刷新不产生重复装备通知。Profile 切换使物品失效时通知卸下，重新启用时通知装备。

```java
@EventHandler
public void onDeath(AccessoryDeathEvent event) {
    if (isInArena(event.getPlayer())) event.setPolicy(AccessoryDeathPolicy.KEEP);
}
@EventHandler
public void onEquip(AccessoryEquipEvent event) {
    logEquipment(event.getOwner(), event.getPage(), event.getSlot(), event.getCause());
}
```

槽位事件与 Profile 提供解锁接入基础；没有实现每玩家永久解锁表、经济扣费或购买界面。不要在槽位状态事件内再次修改全局槽位，重入会被拒绝。

旧的 concrete AccessoryService 构造方法、apply/removeSlotDisable、库存服务中的技能查询/冷却方法、无页号旧事件构造器及 module.attribute.loader 不再提供。调用当前接口，不依赖内部实现类。
