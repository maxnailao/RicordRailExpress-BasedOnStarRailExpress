# CS2 开箱系统完整技术文档

## 一、系统概览

CS2 开箱系统是一套仿 CS2 风格的完整物品经济系统，包含以下核心模块：

| 模块 | 说明 |
|------|------|
| 仓库系统 | 存储箱子/钥匙/皮肤/音乐盒，支持装备/卸下 |
| 开箱系统 | 横向滚动动画，品质光效，中央指针指示 |
| 掉落系统 | 游戏结束掉落箱子+货币，MVP 额外加成 |
| MVP 积分制 | 击杀/停电/存活积分，决定 MVP 归属 |
| 商店系统 | 购买/出售/黑市三标签页 |
| 黑市交易 | 玩家间物品交易，含税机制，手动领取 |

---

## 二、核心文件清单

### 服务端管理器
| 文件 | 路径 | 职责 |
|------|------|------|
| CS2BoxManager | `cs2/CS2BoxManager.java` | 箱子配置管理、开箱逻辑、皮肤品质判定 |
| CS2BoxConfig | `cs2/CS2BoxConfig.java` | 箱子数据结构（奖池、概率、名称） |
| CS2BoxConfigParser | `cs2/CS2BoxConfigParser.java` | 箱子配置 JSON 解析 |
| CS2BoxDropManager | `cs2/CS2BoxDropManager.java` | 游戏结束箱子掉落 + 货币发放 |
| CS2MvpScoreManager | `cs2/CS2MvpScoreManager.java` | MVP 积分累计与 MVP 判定 |
| CS2BlackMarketManager | `cs2/CS2BlackMarketManager.java` | 黑市上架/购买/下架/领取/持久化 |
| CS2SkinInfo | `cs2/CS2SkinInfo.java` | 皮肤名称/品质信息查询 |
| ShopConfig | `cs2/ShopConfig.java` | 商店商品配置 + 出售价格表 |

### 客户端界面
| 文件 | 路径 | 职责 |
|------|------|------|
| CS2CaseOpeningScreen | `client/screen/CS2CaseOpeningScreen.java` | 开箱动画界面 |
| CS2WarehouseScreen | `client/screen/CS2WarehouseScreen.java` | 仓库界面（物品浏览/装备/开箱） |
| CS2ShopScreen | `client/screen/CS2ShopScreen.java` | 商店界面（购买/出售/黑市） |
| CS2BoxPreviewScreen | `client/screen/CS2BoxPreviewScreen.java` | 箱子奖池预览界面 |

### 网络包
| 文件 | 方向 | 用途 |
|------|------|------|
| OpenBoxC2SPayload | C2S | 请求开箱 |
| OpenBoxResultS2CPayload | S2C | 开箱结果（卡片滚动数据） |
| BoxDropS2CPayload | S2C | 游戏结束掉落通知 |
| ShopBuyC2SPayload | C2S | 商店购买请求 |
| ShopSellC2SPayload | C2S | 商店出售请求 |
| BlackMarketListC2SPayload | C2S | 黑市上架请求 |
| BlackMarketBuyC2SPayload | C2S | 黑市购买请求 |
| BlackMarketCancelC2SPayload | C2S | 黑市下架请求 |
| BlackMarketClaimC2SPayload | C2S | 黑市领取收入请求 |
| BlackMarketSyncRequestC2SPayload | C2S | 请求同步黑市数据 |
| BlackMarketSyncS2CPayload | S2C | 黑市挂单列表 + 待领金额同步 |
| EquipSkinC2SPayload | C2S | 装备/卸下皮肤 |
| EquipMusicBoxC2SPayload | C2S | 装备/卸下音乐盒 |
| CS2ServerReceiverRegister | — | 所有 C2S 包的服务端接收器注册 |

### 注册/初始化
| 文件 | 位置 | 职责 |
|------|------|------|
| SRE.java | `io/wifi/starrailexpress/SRE.java` | 系统初始化入口（黑市/商店/掉落/JOIN事件） |
| SREPayloadRegister | `io/wifi/starrailexpress/register/SREPayloadRegister.java` | 所有网络包类型注册 |
| SREClient | `io/wifi/starrailexpress/client/SREClient.java` | S2C 接收器注册 |

---

## 三、MVP 积分制

### 积分规则

**平民/警长阵营（isInnocent）：**
- 击杀狼方玩家：+20
- 击杀中立玩家：+10
- 停电期间击杀（非平民目标）：+10（额外）
- 击杀平民阵营：-25
- 存活到最后（仅 TIME 胜利条件）：+100

**杀手阵营（isKillerTeam，含杀手方中立）：**
- 击杀平民：+20
- 关灯期间队友击杀平民：触发关灯者 +10

### MVP 判定流程
1. 游戏结束时 `CS2MvpScoreManager.getMvp()` 被调用
2. 自动计算存活奖励（AtomicBoolean 防重复）
3. 确定胜利方集合（CustomWinnerPlayers → WinStatus判定 → 全玩家回退）
4. 在胜利方中找积分最高者作为 MVP

### 胜利方判定一致性
- `PASSENGERS/TIME`：`role.isInnocent()`
- `KILLERS`：`SREGameWorldComponent.isKillerTeamRoleStatic(role) && !role.isInnocent()`
- `LOOSE_END`：`gameComponent.getLooseEndWinner()`
- `CUSTOM`：匹配 `CustomWinnerID`

### 关联系统
- **CS2BoxDropManager**：调用 `getMvp()` 确定 MVP，给予额外掉落
- **GameUtils.playVictoryMusicBox()**：调用 `getScore()` 从胜利方中选积分最高者播放音乐盒

---

## 四、掉落系统

### 箱子掉落规则
- 基础概率：由 `inv.getBoxDropChance()` 决定（初始 10%）
- 未掉落时累加 5%（保底机制）
- MVP 额外 +25% 概率
- 掉落成功后重置保底计数器

### 货币掉落
- 基础范围：5 ~ 10 货币
- MVP 额外：+10 货币

### 事件时序
```
OnGameEnd 触发
  → processBoxDrops()
    → CS2MvpScoreManager.getMvp() (计算存活奖励)
    → 广播 MVP 信息
    → 遍历玩家：箱子掉落判定 + 货币发放 + 同步
```

---

## 五、黑市交易系统

### 上架流程
1. 客户端选择物品 → 设定价格 → 发送 `BlackMarketListC2SPayload`
2. 服务端验证物品所有权 → 从仓库移除 → 创建挂单
3. **皮肤装备联动**：若上架的皮肤正在装备中，自动卸下为 "default"
4. 保存到 JSON + 广播给所有在线玩家

### 购买流程
1. 客户端发送 `BlackMarketBuyC2SPayload`
2. 服务端验证：物品存在 + 非自己 + 货币充足
3. 扣款 → 给买家物品
4. 卖家收入 = `售价 × (1 - taxRate)` 存入 `pendingCoins`
5. 移除挂单 → 保存 + 广播

### 税率系统
- 默认税率：15%（`taxRate = 0.15`）
- 可通过 `setTaxRate(double)` 动态调整（范围 0.0 ~ 1.0）
- 卖家实际收入 = `Math.round(price × (1 - taxRate))`

### 手动领取机制
- **不论卖家在线/离线**，收入统一存入 `pendingCoins` Map
- 玩家上线时收到通知消息（"你有 X 黑市离线收入待领取"）
- 在商店-黑市界面点击"领取 XX 货币"按钮手动领取
- 领取后调用 `syncToPlayer()` 刷新客户端显示

### 下架流程
- 只能下架自己的挂单
- 物品归还仓库

### 数据持久化
- 存储路径：`config/black_market_data.json`
- 格式：`{ "listings": [...], "pendingCoins": {...} }`
- 每次操作后实时写入
- 服务重启自动加载

### 数据同步
- 打开商店时发送 `BlackMarketSyncRequestC2SPayload` 请求同步
- 任何黑市操作后 `broadcastToAll()` 推送更新
- S2C 包携带 `listingsJson` + `myPendingCoins`

---

## 六、客户端界面设计

### 商店界面（CS2ShopScreen）
三个标签页：
1. **购买**：显示 ShopConfig 中的商品列表，点击购买
2. **出售**：显示仓库中可出售物品，按品质定价
3. **黑市**：
   - 浏览模式：商品列表 + "上架物品"按钮 + "领取收入"按钮
   - 上架模式：仓库物品列表 + EditBox 价格输入 + 确认/取消

### 仓库界面（CS2WarehouseScreen）
- 逗号热键打开
- 支持皮肤/音乐盒右键装备/卸下
- 双击箱子打开奖池预览

### 开箱动画（CS2CaseOpeningScreen）
- 横向滚动：加速 → 匀速 → 贝塞尔减速
- 品质光效
- 中央指针指示结果

### UI 交互注意事项
- EditBox 输入：`keyPressed`/`charTyped` 手动转发，listing 模式下阻止 `super.mouseClicked` 防止焦点丢失
- 按钮优先级：确认/取消按钮检测在物品选择之前（防止布局重叠时误触）
- 每帧渲染前清空 hover 状态

---

## 七、CCA 组件依赖

| 组件 | 用途 |
|------|------|
| CS2InventoryComponent | 仓库存储（箱子/钥匙/皮肤/音乐盒/掉落保底计数） |
| SREGameWorldComponent | 游戏状态/角色分配 |
| SREGameRoundEndComponent | 回合结束状态/胜利方/CustomWinnerPlayers |
| SREWorldBlackoutComponent | 关灯状态/最后触发者 UUID |
| SREPlayerSkinsComponent | 玩家装备皮肤状态 |
| MusicBoxPlayerComponent | 玩家装备音乐盒状态 |

---

## 八、关键设计决策

### 1. MVP 存活奖励防重复
使用 `AtomicBoolean survivalBonusApplied` + `compareAndSet` 确保 `getMvp()` 被多次调用时存活奖励只加一次。

### 2. 黑市收入统一 pendingCoins
无论卖家在线与否，收入始终存入待领队列，杜绝了"在线直接给钱 + 离线存 pending"双路径导致的重复收钱风险。

### 3. EditBox 焦点保护
在 listing 模式下，`mouseClicked` 不转发给 `super`，避免 Screen 默认 widget 处理逻辑重置 EditBox 焦点。

### 4. 预缓存所有权集合
`ownListingIds` 在 `refreshData()` 中一次性计算，渲染时使用 O(1) Set 查询，避免每帧 JSON 反序列化。

### 5. 上架皮肤装备联动
上架皮肤时自动检查并卸下装备状态，防止玩家装备一个不在仓库中的皮肤。

---

## 九、事件注册链

```
SRE.onInitialize()
  └── initCS2System()
        ├── ShopConfig.getInstance().load()
        ├── CS2BlackMarketManager.getInstance().init()
        ├── ServerPlayConnectionEvents.JOIN → 通知待领金额
        ├── CS2BoxDropManager.register()
        │     └── OnGameEnd.EVENT.register()
        │     └── CS2MvpScoreManager.register()
        │           ├── OnGameStarted.EVENT.register()
        │           └── OnPlayerKilledPlayer.EVENT.register()
        └── CS2ServerReceiverRegister.registerAll()
              ├── registerOpenBox()
              ├── registerShopBuy()
              ├── registerShopSell()
              ├── registerBlackMarketList()
              ├── registerBlackMarketBuy()
              ├── registerBlackMarketCancel()
              ├── registerBlackMarketSyncRequest()
              ├── registerBlackMarketClaim()
              ├── registerEquipSkin()
              └── registerEquipMusicBox()
```

---

## 十、数据存储位置

| 数据 | 路径 | 格式 |
|------|------|------|
| 黑市挂单 + 待领货币 | `config/black_market_data.json` | `{ "listings": [...], "pendingCoins": {...} }` |
| 商店商品配置 | `config/shopprice.json` | ShopConfig 格式 |
| 箱子配置 | `config/cs2_boxes.json` | CS2BoxConfig 列表 |
| 玩家仓库 | CCA 组件持久化 | CS2InventoryComponent |
| 玩家货币 | PlayerEconomyManager | 独立存储 |

---

## 十一、已知限制与注意事项

1. **黑市不支持箱子+钥匙同时上架的捆绑交易**
2. **税率修改需通过代码调用 `setTaxRate()`，暂无命令接口**
3. **MVP 积分不跨局累计，每局游戏开始时重置**
4. **黑市挂单无过期机制，物品将永久挂单直到购买或下架**
5. **开箱并发保护：使用 `Set<UUID> openingBoxPlayers` 防止同一玩家同时多次开箱**
