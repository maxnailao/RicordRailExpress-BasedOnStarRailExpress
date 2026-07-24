# CS2 皮肤系统 — 添加新皮肤完整指南

本文档说明在系统大调整后，添加一个新皮肤需要完成的**所有步骤**。缺少任一步骤都会导致皮肤显示乱码（棋盘格）、无法开箱获得、或商店出售价格异常。

---

## 📋 总览：添加新皮肤的 6 步检查清单

| # | 步骤 | 文件位置 | 必须 |
|---|------|----------|------|
| 1 | 添加贴图文件 (PNG) | `assets/starrailexpress/textures/item/skins/{type}/` | ✅ |
| 2 | 添加模型文件 (JSON) ×2 | `assets/starrailexpress/models/item/skins/{type}/` | ✅ |
| 3 | 注册皮肤到 SRESkinRegistry | `src/main/java/.../SRESkinRegistry.java` | ✅ |
| 4 | 添加中文名和介绍到 CS2SkinInfo | `src/main/java/.../CS2SkinInfo.java` | ✅ |
| 5 | 将皮肤加入箱子奖池配置 | `CS2_box/{box_id}.json` | ✅ |
| 6 | （可选）商店出售价格配置 | `CS2_shop.json` | 可选 |

---

## 第一步：添加贴图文件

**路径格式（必须包含 `skins/` 中间目录）：**
```
src/main/resources/assets/starrailexpress/textures/item/skins/{type}/{skinName}.png
```

**支持的 `{type}` 值：**
- `knife` — 刀
- `revolver` — 左轮
- `bat` — 球棒
- `grenade` — 手雷
- `hat` — 帽子（暂无资源）

**示例：**
```
textures/item/skins/knife/knife_yingfeng.png
textures/item/skins/revolver/revolver_g7.png
textures/item/skins/bat/baseball_bat_studded.png
```

> ⚠️ **关键**：路径中必须有 `skins/` 目录，否则 `LootScreenUtils.getItemResourceLocation()` 会生成错误路径导致贴图找不到（显示棋盘格乱码）。

---

## 第二步：添加模型文件

每个皮肤需要**两个**模型 JSON 文件：

### 2a. 物品模型 `{skinName}.json`

**路径：**
```
src/main/resources/assets/starrailexpress/models/item/skins/{type}/{skinName}.json
```

**内容模板：**
```json
{
    "parent": "item/generated",
    "textures": {
        "layer0": "starrailexpress:item/skins/{type}/{skinName}"
    }
}
```

### 2b. 手持模型 `{skinName}_in_hand.json`

**路径：**
```
src/main/resources/assets/starrailexpress/models/item/skins/{type}/{skinName}_in_hand.json
```

**内容模板（同上）：**
```json
{
    "parent": "item/generated",
    "textures": {
        "layer0": "starrailexpress:item/skins/{type}/{skinName}"
    }
}
```

**示例（knife_yingfeng）：**
- `models/item/skins/knife/knife_yingfeng.json`
- `models/item/skins/knife/knife_yingfeng_in_hand.json`

---

## 第三步：注册皮肤到 SRESkinRegistry

**文件：** `src/main/java/io/wifi/starrailexpress/index/SRESkinRegistry.java`

在 `register()` 方法中，找到对应类型的区块，添加一行：

```java
registerSkin(SkinTypes.KNIFE, "knife_yingfeng", QualityColor.LEGENDARY);
```

### 品质颜色枚举 `QualityColor`

| 枚举值 | 品质 | 颜色 | 出售价格（默认） |
|--------|------|------|-----------------|
| `QualityColor.COMMON` | 普通 | `#EEEEEE` 白色 | 5 货币 |
| `QualityColor.UNCOMMON` | 罕见 | `#33FF55` 绿色 | 15 货币 |
| `QualityColor.RARE` | 稀有 | `#AAAAFF` 蓝色 | 50 货币 |
| `QualityColor.EPIC` | 史诗 | `#AA55FF` 紫色 | 150 货币 |
| `QualityColor.LEGENDARY` | 传说 | `#FFAA55` 橙色 | 500 货币 |
| `QualityColor.UNBELIEVABLE` | 不可思议 | `#FF3F3F` 红色 | 2000 货币 |

### skinID 命名规范

- **全小写**，使用**下划线分隔**
- 建议格式：`{type}_{name}`，如 `knife_yingfeng`、`revolver_g7`
- 必须与贴图文件名、模型文件名**完全一致**

> ⚠️ **重要**：只注册有实际贴图和模型资源的皮肤。注册了但没有贴图/模型的皮肤会导致**乱码（棋盘格）**。

---

## 第四步：添加中文名和介绍到 CS2SkinInfo

**文件：** `src/main/java/org/agmas/noellesroles/cs2/CS2SkinInfo.java`

在 `static {}` 块中，找到对应类型的区域，添加一行：

```java
register("knife/knife_yingfeng", "影锋", "暗影中的利刃，传说级收藏品");
```

**参数说明：**
- 第一个参数：皮肤 ID，格式为 `"{type}/{skinName}"`（注意这里用的是 `/` 分隔）
- 第二个参数：中文显示名称
- 第三个参数：中文介绍/描述

> 如果不添加，仓库和开箱界面会显示格式化后的英文 ID（如 `knife yingfeng`）。

---

## 第五步：将皮肤加入箱子奖池配置

**路径：** `CS2_box/{box_id}.json`（游戏运行目录下的 `CS2_box/` 文件夹）

### 箱子配置 JSON 格式

```json
{
  "box_name": "武器箱I",
  "key_name": "weapon_key_1",
  "common": 0.7992,
  "uncommon": 0.1598,
  "rare": 0.032,
  "epic": 0.0064,
  "legendary": 0.0026,
  "unbelievable": 0.0,
  "common_skins": [
    "knife/testofknifeskin",
    "knife/knife_stonetool"
  ],
  "uncommon_skins": [
    "knife/knife_tangdao",
    "revolver/revolver_g7"
  ],
  "rare_skins": [
    "knife/knife_cheese"
  ],
  "epic_skins": [
    "knife/knife_bunana"
  ],
  "legendary_skins": [
    "knife/knife_yingfeng"
  ],
  "unbelievable_skins": []
}
```

### 关键规则

1. **皮肤 ID 格式**：`"{type}/{skinName}"`（用 `/` 分隔）
2. **概率总和必须 = 1.0**（允许 ±0.001 误差），否则配置加载失败
3. **概率为 0 的品质**其 `_skins` 列表可以为空 `[]`
4. **新皮肤必须放入对应品质的 `_skins` 数组**中，否则无法从该箱子开出
5. 同一皮肤可以出现在多个箱子中

> 箱子配置是**运行时加载**的（非打包进 JAR），修改后重启服务器或使用 `/tmm:reload` 指令即可生效。

---

## 第六步（可选）：商店出售价格配置

**路径：** `CS2_shop.json`（游戏运行目录下）

出售价格按品质定价，默认值见上方品质表。如需自定义，在 `sellprice` 段修改：

```json
{
  "sellprice": {
    "common_skinsprice": 5,
    "uncommon_skinsprice": 15,
    "rare_skinsprice": 50,
    "epic_skinsprice": 150,
    "legendary_skinsprice": 500,
    "unbelievable_skinsprice": 2000,
    "box_price": {
      "weapon_case_1": 20
    }
  }
}
```

---

## 🔍 皮肤 ID 格式对照表

不同系统使用不同的 ID 格式，注意区分：

| 系统 | ID 格式 | 示例 |
|------|---------|------|
| SRESkinRegistry 注册 | `skinName`（仅名称） | `knife_yingfeng` |
| CS2SkinInfo 中文名 | `type/skinName` | `knife/knife_yingfeng` |
| 箱子奖池配置 JSON | `type/skinName` | `knife/knife_yingfeng` |
| 仓库存储 (CS2InventoryComponent) | `type/skinName` | `knife/knife_yingfeng` |
| 贴图路径 | `textures/item/skins/{type}/{skinName}.png` | `textures/item/skins/knife/knife_yingfeng.png` |
| 模型路径 | `models/item/skins/{type}/{skinName}.json` | `models/item/skins/knife/knife_yingfeng.json` |

---

## ❌ 常见问题排查

### 皮肤显示棋盘格/乱码

**原因（按优先级排查）：**
1. 贴图文件不存在 → 检查 `textures/item/skins/{type}/{skinName}.png` 是否存在
2. 贴图路径错误 → 确认路径中有 `skins/` 中间目录
3. 模型文件不存在 → 检查 `models/item/skins/{type}/{skinName}.json` 是否存在
4. 在 SRESkinRegistry 注册了但没有资源文件 → **删除注册行**

### 皮肤无法从箱子开出

1. 检查箱子配置 JSON 中是否包含该皮肤 ID
2. 检查皮肤 ID 格式是否正确（`type/skinName`）
3. 检查概率总和是否 = 1.0

### 出售价格为 0 或异常

1. 确认皮肤在箱子配置中有对应品质（`findSkinQuality()` 通过遍历所有箱子配置查找）
2. 如果皮肤不在任何箱子中，品质默认为 0（common），使用 common 价格

### 开箱动画显示货不对板

1. 确认 `LootScreenUtils.getItemResourceLocation()` 路径正确
2. 确认模型 JSON 中的 `layer0` 路径与贴图文件路径一致

---

## ✅ 完整添加示例：添加一把新刀 "暗影之刃"

假设新皮肤：刀类，名叫 `knife_shadow`，品质为 EPIC（史诗）

### 1. 放置资源文件
```
textures/item/skins/knife/knife_shadow.png        ← 贴图（16×16 或更大）
models/item/skins/knife/knife_shadow.json          ← 物品模型
models/item/skins/knife/knife_shadow_in_hand.json  ← 手持模型
```

### 2. SRESkinRegistry.java
```java
registerSkin(SkinTypes.KNIFE, "knife_shadow", QualityColor.EPIC);
```

### 3. CS2SkinInfo.java
```java
register("knife/knife_shadow", "暗影之刃", "被暗影笼罩的利刃，散发着不祥的光芒");
```

### 4. CS2_box/weapon_case_1.json
```json
"epic_skins": [
    "knife/knife_bunana",
    "knife/knife_shadow"
]
```

完成以上 4 步，重启即可在游戏中看到并使用新皮肤。
