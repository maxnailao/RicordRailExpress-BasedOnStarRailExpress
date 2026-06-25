# 如何创建扩展

> 本文档为**在现有代码库中**注册新内容的快速参考。  
> 如果你要创建一个**独立的扩展模组**（新 Gradle 项目），请先阅读 **[docs/创建模组.md](docs/创建模组.md)**。  
> 如果你要**新增或修改职业（角色）**，请先阅读 **[docs/角色开发指南.md](docs/角色开发指南.md)**（职业注册、组件、技能、事件、文案、商店速查）。

## 项目构建

在开始开发前，确保项目可以正常编译和运行：

```bash
# 编译并打包（产物在 build/libs/）
./gradlew build

# 启动测试客户端
./gradlew runClient

# 启动测试服务端
./gradlew runServer

# 清理构建缓存
./gradlew clean
```

> **环境要求：** JDK 21，并能访问 `maven.fabricmc.net`、`maven.terraformersmc.com` 等 Maven 仓库。

## 抽奖系统

### 创建新的稀有度

- 在"StarRailExpress\src\main\resources\assets\noellesroles\textures\gui\loot"中添加新的稀有度背景
- 在LootManager 的 qualityBgList 列表添加该文件路径
- 在lootpool配置文件中添加对应级别稀有度即为稀有度
- 注意：超过最大稀有度默认显示最大稀有度

## 注册新角色

### 注册方式：

在ModRoles.java文件中

- 添加自定义角色id
- 注册公有静态角色：可以在初始化函数中初始化或直接在声明时初始化

## 注册新物品

### 注册方式：

创建物品Java类继承Item类并实现功能

在ModItems.java文件中添加公有静态物品常量对象并赋予id

## 注册实体

### 注册需求：

创建实体类并注册，创建实体渲染器类并为实体注册

### 注册方式：

实体类创建java文件后在ModEntities.java件中进行注册

再在client.renderer中创建EntityRender.java渲染器类用于客户端渲染，
并在NoellesrolesClient.java中的registerEntityRenderers方法中对实体的渲染器进行注册

## 注册新商店

### 注册方式：

在Noellesroles.java文件中

- 声明新的静态商品对象列表
- 在initShops()函数中对列表进行初始化添加新物品
- 在shopRegiester()函数中为角色注册商店

## 注册网络包

### 定义网络包

在packet下创建___C2Packets.java(或S2C)类继承CustomPacketPayload作为网络包包含：

- 网络包的唯一标识符ResourcesLocatiom
- 网络包类型标识符
- 序列化/反序列化编解码
- 定义编解码器写入读取方法
- 需要传输的内容等

### 注册网络包

对于C2S网络包

- 在模组初始化调用registerPackets函数中注册网络包
- 并且对该网络包进行处理

对于S2C网络包

- 在registerPackets1中进行注册
- 在Client主类中进行处理

## 创建GUI

### 创建方式：

client下创建___Screen.java类继承Screen作为新GUI

重写init函数对GUI进行初始化：注意布局以及添加到渲染列表中

## 对列车谋杀案模组修改

### 代码混合：

- 在mixin文件夹内创建java类，使用@Minin注解进行混合
- 在noellesroles.minin.json文件中添加混合配置

## 翻译

在en_us.json 和 zh_cn.json中添加注册的id对应的汉化（根据已有汉化即可）

