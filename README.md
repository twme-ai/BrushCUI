# BrushCUI

Shows the space your WorldEdit or FastAsyncWorldEdit brush would affect, at the block you are
looking at, as a WorldEditCUI multi-region. It is drawn by the
[WorldEditCUI](https://github.com/EngineHub/WorldEditCUI) client mod, or by the
[WorldEditDisplay](https://github.com/TWME-TW/WorldEditDisplay) server plugin for players without
the mod. It is a sibling of [ClipboardCUI](https://github.com/twme-ai/ClipboardCUI).

[English](#english) | [繁體中文](#繁體中文)

---

## English

### How it works

While you hold an item with a brush bound to it, BrushCUI traces your view the same way the brush
does (`getBlockTrace(range, true, traceMask)`, so `//range` and `//tracemask` apply), works out what
that brush's `build` method would touch at the hit block, and sends it on the `worldedit:cui`
channel as a multi-region with a fixed UUID. It never touches WorldEdit's own selection.

| Brush | Preview |
|---|---|
| `sphere`, `sphere -h`, `morph`, `erode`, `dilate`, `splatter` | Sphere of the brush size |
| `cylinder` (also hollow) | Cylinder from the target block up (or down for a negative height) |
| `set`, `forest`, `deform`, `raise`, `lower`, `snow`, `biome`, `feature` (shape-based) | The brush's own shape region (sphere, cylinder or cuboid) |
| `smooth`, `snowsmooth` | Its height map box (`size` around the target, `size + 10` up) |
| `gravity` | Columns within `size`, layers within the height offset (approximate on FAWE) |
| `butcher` | Cylinder of the radius, full world height |
| `clipboard` | Bounding box of the pasted clipboard (follows `-o` and, on WorldEdit, `//rotate`) |
| `heightmap` | Box of the brush size |
| Anything else (most FAWE brushes) | Sphere of the brush size, or nothing with `unknown-brushes: none` |

- Spheres and cylinders are sent with the `+wed|outline` hint, so WorldEditDisplay 2.9.0+ draws
  only their outline (three great circles; two circles and four sides) instead of a full grid.
  WorldEditCUI ignores the hint and draws its usual rings, which cost nothing on the server.
- On FAWE, looking at the sky targets the air block at the end of the range, exactly where the
  brush would apply; on WorldEdit nothing is shown.
- Players with neither WorldEditCUI nor WorldEditDisplay receive nothing (`hasCUISupport()` gate).
- The preview is re-sent after the client's CUI handshake (world change, respawn) and cleared with
  `+s|clear|<uuid>` only, so other plugins' multi-regions are left alone.

### Packets and update rate

Every update is 5 CUI messages. On WorldEditDisplay the cost is the display entities; measured in
the end-to-end test (Paper 1.21.11, radius 5 sphere brush):

| WorldEditDisplay | Entities | Packets when the preview moves |
|---|---|---|
| 2.8.1 (full ellipsoid grid) | 1386 | ~940 |
| 2.9.0+ (outline hint) | 306 | ~215 |

A preview that only moved is teleported, not respawned (WorldEditDisplay 2.8.0+). BrushCUI only
sends when the shape or target block changes; **follow** mode (default) updates while the
crosshair moves, at most once every 4 ticks; **settle** mode waits until the target has stood still
for 6 ticks. Each player is capped at 40 CUI messages per second.

### Requirements

- Paper 1.21.4+, Java 21 — tested on Paper 1.21.11 (Folia is declared but untested)
- WorldEdit 7.3+ or FastAsyncWorldEdit 2.x — tested with WorldEdit 7.4.5 and FAWE 2.16.1
- For players: WorldEditCUI, or WorldEditDisplay on the server (2.9.0+ for outline rendering)

### Commands and permissions

| Command | Description |
|---|---|
| `/brushcui on\|off\|toggle` | Turn your preview on or off (saved on the player) |
| `/brushcui mode <follow\|settle>` | Choose how the preview follows the crosshair |
| `/brushcui status` | Show your current settings |
| `/brushcui reload` | Reload `config.yml` (`brushcui.reload`, op) |

Alias: `/brushpreview`. `brushcui.use` (default: everyone) is needed to see the preview and use the
command. The preview is also hidden when the player may not use the brush.

### Configuration

See [`config.yml`](src/main/resources/config.yml): default on/off and mode, poll interval, what to
show for unknown brushes, throttle limits and colours (`line`, `grid`, `center`, `fill` in
`#RRGGBB[AA]`; alpha `00` hides a part).

### Building and testing

```bash
mvn package                      # target/BrushCUI-<version>.jar, runs unit tests
integration/run-e2e.sh           # Paper + WorldEdit + WorldEditDisplay + mineflayer bots
BRUSHCUI_E2E_EDITOR=fawe integration/run-e2e.sh
BRUSHCUI_E2E_WED_JAR=/path/to/WorldEditDisplay.jar BRUSHCUI_E2E_EXPECT_OUTLINE=1 integration/run-e2e.sh
```

---

## 繁體中文

### 運作方式

手上拿著綁定筆刷的物品時，BrushCUI 用和筆刷相同的方式追蹤視線（`getBlockTrace(range, true, traceMask)`，
所以 `//range` 和 `//tracemask` 都有效），依該筆刷 `build` 的實際行為算出在命中方塊會影響的範圍，
以固定 UUID 的多選區訊息經 `worldedit:cui` 頻道送出，不會動到 WorldEdit 本身的選區。

| 筆刷 | 預覽 |
|---|---|
| `sphere`、`sphere -h`、`morph`、`erode`、`dilate`、`splatter` | 以筆刷大小為半徑的球 |
| `cylinder`（含空心） | 從目標方塊往上（高度為負時往下）的圓柱 |
| `set`、`forest`、`deform`、`raise`、`lower`、`snow`、`biome`、`feature` 等指定形狀的筆刷 | 筆刷本身的形狀範圍（球、圓柱或長方體） |
| `smooth`、`snowsmooth` | 高度圖範圍（目標周圍 `size`，往上 `size + 10`） |
| `gravity` | `size` 內的柱子、高度偏移內的層（FAWE 為近似值） |
| `butcher` | 該半徑、整個世界高度的圓柱 |
| `clipboard` | 貼上後剪貼簿的外框（支援 `-o`；WorldEdit 上也套用 `//rotate`） |
| `heightmap` | 筆刷大小的方框 |
| 其他（多數 FAWE 筆刷） | 筆刷大小的球；設定 `unknown-brushes: none` 則不顯示 |

- 球與圓柱會附帶 `+wed|outline` 提示，WorldEditDisplay 2.9.0+ 只畫外框（三個大圓；上下兩圈加四條邊），
  不畫整套網格。WorldEditCUI 會忽略此提示並照常畫圈，對伺服器沒有負擔。
- FAWE 上看向天空時，目標是射程盡頭的空氣方塊，也就是筆刷實際會作用的位置；WorldEdit 則不顯示。
- 沒有 WorldEditCUI 也沒有 WorldEditDisplay 的玩家不會收到任何訊息（`hasCUISupport()` 判斷）。
- 客戶端重新握手（換世界、重生）後會重送；清除時只送 `+s|clear|<uuid>`，不影響其他插件的多選區。

### 封包與更新頻率

每次更新 5 則 CUI 訊息。WorldEditDisplay 的成本在顯示實體；端對端測試量測（Paper 1.21.11，半徑 5 的球筆刷）：

| WorldEditDisplay | 實體數 | 預覽移動時的封包 |
|---|---|---|
| 2.8.1（完整橢球網格） | 1386 | 約 940 |
| 2.9.0+（外框提示） | 306 | 約 215 |

只有位移的預覽會以 teleport 移動，不重新生成（WorldEditDisplay 2.8.0+）。BrushCUI 只在形狀或目標方塊改變時送出；
**follow** 模式（預設）準星移動時更新，最多每 4 tick 一次；**settle** 模式等目標靜止 6 tick 後才更新。
每位玩家每秒最多 40 則 CUI 訊息。

### 需求

- Paper 1.21.4+、Java 21，於 Paper 1.21.11 測試（宣告支援 Folia，但未測試）
- WorldEdit 7.3+ 或 FastAsyncWorldEdit 2.x，於 WorldEdit 7.4.5 與 FAWE 2.16.1 測試
- 玩家端：WorldEditCUI，或伺服器安裝 WorldEditDisplay（外框渲染需 2.9.0+）

### 指令與權限

| 指令 | 說明 |
|---|---|
| `/brushcui on\|off\|toggle` | 開關自己的預覽（存在玩家資料中） |
| `/brushcui mode <follow\|settle>` | 選擇預覽跟隨準星的方式 |
| `/brushcui status` | 顯示目前設定 |
| `/brushcui reload` | 重新載入 `config.yml`（`brushcui.reload`，預設 op） |

別名：`/brushpreview`。看到預覽與使用指令需要 `brushcui.use`（預設所有人）。玩家沒有權限使用該筆刷時也不顯示。

### 設定

見 [`config.yml`](src/main/resources/config.yml)：預設開關與模式、輪詢間隔、未知筆刷的處理、節流與顏色
（`line`、`grid`、`center`、`fill`，格式 `#RRGGBB[AA]`；alpha `00` 表示隱藏）。

### 建置與測試

```bash
mvn package                      # target/BrushCUI-<version>.jar，會跑單元測試
integration/run-e2e.sh           # Paper + WorldEdit + WorldEditDisplay + mineflayer 機器人
BRUSHCUI_E2E_EDITOR=fawe integration/run-e2e.sh
BRUSHCUI_E2E_WED_JAR=/path/to/WorldEditDisplay.jar BRUSHCUI_E2E_EXPECT_OUTLINE=1 integration/run-e2e.sh
```
