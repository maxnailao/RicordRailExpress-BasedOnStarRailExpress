牛仔决斗场地 · 结构模板（NBT）
================================

把你自己搭的决斗场导出成 .nbt 放在这个目录里，代码就会在固定锚点自动生成它。
（对应 CowboyDuelManager.ARENA_MODE = 1）

本目录对应资源路径：data/noellesroles/structure/
所以文件名 cowboy_arena.nbt → 结构 id noellesroles:cowboy_arena


一、怎么导出
------------

方式 A：WorldEdit（推荐）
  1. 创造模式搭好决斗场
  2. //wand 拿木斧，左键点选区一个角、右键点对角
  3. //copy
  4. //schem save cowboy_arena
  5. 从 .minecraft/config/worldedit/schematics/cowboy_arena.schem 找到文件

  ⚠️ WorldEdit 存的是 .schem（Sponge 格式），不是 .nbt。
     需要再用结构方块转一次（见方式 B），或直接用方式 B。

方式 B：结构方块（纯原版，能得到 .nbt）
  1. 搭好场地，记下你要当"原点"的那个角（选区最小角）
  2. /give @s structure_block
  3. 放一个结构方块，右键打开界面

     ★★ 先切模式！★★
       刚放下的结构方块默认是 **DATA 模式**，这个模式下**只有"结构名称"一个输入框**，
       看不到相对坐标和尺寸 —— 这是最容易卡住的地方。
       点界面左上角的【模式按钮】循环切换：
           DATA → SAVE → LOAD → CORNER → DATA …
       切到 **SAVE** 之后才会出现：
           · 结构名称（Structure Name）
           · 相对坐标（Relative Position）X / Y / Z  ← 三格
           · **尺寸（Size）X / Y / Z**                ← 三格（你要找的就是这个）
           · 是否包含实体 / 显示空气 / 显示边界框
           · 完整性（Integrity）与种子 —— 保存时**不用管**
           · 底部按钮：DETECT（探测尺寸）/ SAVE / EXPORT

  4. 填好相对坐标与尺寸，点 **SAVE**
       · 结构名称：noellesroles:cowboy_arena   （或先填 cowboy_arena）
       · 相对坐标：把角落对准你想当原点的位置
       · 尺寸：把整个场地框进去
       · 嫌手填麻烦就点 **DETECT**：它从结构方块位置开始向 +X/+Y/+Z 自动探测包围盒，
         然后把结果填进尺寸框（探测不对的话再手改）

     ⚠️ 尺寸默认上限 48×48×48。更大的场地需要放宽：
           /gamerule structureBlockMaxSize 128

  5. .nbt 会生成在存档目录（注意这里是**复数 structures**）：
       <存档>/generated/<命名空间>/structures/cowboy_arena.nbt
     例如本项目的集成服务端存档：
       run/saves/world/generated/minecraft/structures/cowboy_arena.nbt
  6. 把它复制到本目录，重命名为：cowboy_arena.nbt
     （本目录 = data/noellesroles/structure/，注意这里是**单数 structure**）
  7. 重新构建 mod

  ★ 目录名不要搞混（已从 StructureTemplateManager 的常量确认）：
      保存（结构方块写出） → generated/<ns>/structures/   复数
      读取（数据包/模组）  → data/<ns>/structure/         单数


二、关键：两个入场点怎么定
--------------------------

不用填世界坐标。你只需要填"相对结构原点的偏移"：

    STRUCT_COWBOY_OFFSET  —— 牛仔站在结构里的哪一格
    STRUCT_TARGET_OFFSET  —— 对手站在结构里的哪一格

偏移 = 站位坐标 - 结构原点坐标
（结构原点 = 结构方块里你框选的那个最小角）

默认值是：
    STRUCT_COWBOY_OFFSET = (0, 1, 0)     牛仔在原点正上方 1 格
    STRUCT_TARGET_OFFSET = (0, 1, 24)    对手在 +Z 方向 24 格处

也就是说：**建议你搭的时候就让两个站位沿 Z 轴相隔 24 格**，
这样默认值直接可用，你一格都不用改。


三、懒得算偏移怎么办
--------------------

先按默认值 (0,1,0) / (0,1,24) 开一局。
代码会把**实际生成的世界坐标**打进日志：

    [CowboyDuel] 已在 ... 生成结构 noellesroles:cowboy_arena（原点 ...，尺寸 ...）
                 ｜牛仔入场点 Vec3[x=.., y=.., z=..]｜对手入场点 Vec3[x=.., y=.., z=..]

对着日志里的坐标和你的建筑位置比对，再回来微调这两个偏移即可。


四、其他可调项（都在 CowboyDuelManager 顶部）
--------------------------------------------

    ARENA_ORIGIN      结构生成的世界锚点（默认 0,200,-21000 的虚空）
                      建议放远处虚空，别和正常地图重叠
    BOUND_RADIUS      活动半径（越界被拉回）—— 不要超过你地面实际半径
    BOUND_HEIGHT      活动高度
    COWBOY_YAW / TARGET_YAW
                      朝向。默认由坐标自动推导成"背靠背"；
                      想去掉背靠背就是面对面：删掉那两行末尾的 + 180.0F


五、找不到结构文件时
--------------------

代码不会崩：会打一条错误日志并**回退到自动草方块平台**，
所以就算 nbt 还没放好，决斗功能也是可用的。
