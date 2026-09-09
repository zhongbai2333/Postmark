# SignMeUp 联动

## 核查版本

- Minecraft 26.1.2、NeoForge 26.1.2.75、ExhibitionPortal 1.1.12。
- 上游 [teaconmc/SignMeUp](https://github.com/teaconmc/SignMeUp)，分支 `26.1.2-neoforge`，提交 `32dcde5fd52e8b4805196b1fcf9bb2e121b64028`。
- 当前声明接受 ExhibitionPortal `[1.1.12,1.2)`，实际集成验证版本为 1.1.12。

## 章面和名称

SMU 的 `ExhibitionStamp` 包含 `id`、`item`、`location`、`rotate`，不规定圆章形状。`item` 若为注册物品，按原生 GUI 物品模型捕获；否则直接读取资源 PNG。编辑器的 `stamp_handle_rect.png` 是操作手柄，不是章面。

物品捕获使用 Minecraft `GuiItemAtlas`，边长为 `16 × GUI 缩放倍率`，GUI 2 时为 32 像素；放大采用最近邻采样，保留模型、颜色与透明度。动态图标保存捕获时单帧。超出标准 GUI 单元的特殊模型会提示不支持，不生成被裁切的替代章面。

展区名称来自 `Exhibition.metadata().name()`，服务器默认未设置值为 `@unset`。Postmark 从客户端 `GALLERY_LOOKUP` 读取，仅归档玩家已拥有的章。旧收藏在正常同步后补全名称，元数据改名时刷新本地名称并保留顺序；离线仍可使用已保存的名称。

活动约定 `stamp_id=expert` 为大师章，`visitor` 为普通章；只改变工具样式，原图案不变。收藏身份使用展区 UUID 与章 ID，跨展区相同图片不会合并。

## 地图自动盖印

1. 原盖章台照常请求服务端授章，客户端改为打开 Postmark。
2. 等待交互之后的新服务端快照，确认拥有该章。
3. 以交互时玩家 X/Z 为中心，通过 SMU 原消息更新地图印迹，旋转为 0。
4. 将原章面加入本地收藏，明信片可独立重复盖印。

坐标按 `exhibition_portal:textures/gui/map.json` 的 `full` 范围归一化。尺寸根据当前地图区域和玩家标记实际可见 alpha 边界计算，目标为其可见边长的 1.15 倍；原标记贴图下约 20.5 GUI 像素。从未打开地图时按 SMU 默认 essential 视野估算，之后随原地图机制缩放。已有大印迹需再次点击对应盖章台更新。

地图宽高比取实际地图区域，不依赖背景 PNG 的比例。边缘印迹保留真实中心，由原地图裁切；玩家在地图范围外或配置不可读时保留旧记录并提示。SMU 同展区、同章 ID 的地图记录仍只有一份；明信片印迹使用独立 UUID。

## 边界

已通过真实集成服务器的盖章台、地图位置回传、visitor/expert、原生三维/平面物品和透明 PNG 验证。Postmark 不包含 SMU 源码或发行 JAR。用户的远程活动服、专用资源包、第三方特殊渲染器与其他上游版本仍需各自验证。
