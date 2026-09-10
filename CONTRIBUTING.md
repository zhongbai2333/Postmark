# 开发与贡献

## 构建

需要 JDK 25。项目附带 Gradle 9.2.1 wrapper，无需全局安装 Gradle。

```sh
./gradlew build
./gradlew runClient
```

Windows 将 `./gradlew` 替换为 `./gradlew.bat`。首次构建需要联网下载 NeoForge、Minecraft 开发依赖及素材。正式产物在 `build/libs/`，`-sources.jar` 是源码包。

## 目录

```text
src/main/java/dev/postmark/
  client/       界面、章袋、计时与滚动
  model/        不可变文档与收藏
  render/       像素绘制、原生章面捕获
  storage/      草稿、图片和 PNG 导出
  compat/       可选 SMU 桥接与 Mixin
src/test/       单元测试
src/smoke/      游戏工作台自动测试
src/compatSmoke/真实 SMU 测试夹具
docs/           操作、设计及验证说明
```

## 验证

```sh
./gradlew test
./gradlew runClient -PsmokeTest
```

`smokeTest` 使用独立的 `run-smoke/`，会启动实际游戏窗口、操作测试草稿、保存截图并自动退出。它覆盖导出打开文件夹，所以测试时也可能打开系统文件管理器。结果在 `run-smoke/smoke-result.txt`，截图在 `run-smoke/smoke-shots/`。Gradle 进程成功退出不代表游戏断言成功，须检查结果以 `PASS:` 开头。

真实 SMU 测试需自行构建或取得 [兼容说明](docs/COMPATIBILITY.md) 中的 ExhibitionPortal 1.1.12，放入忽略的 `local/exhibition_portal-1.1.12.jar`，然后运行：

```sh
./gradlew runClient -PcompatSmoke
```

使用独立的 `run-compat-smoke/` 创建新世界；检查 `compat-result.txt` 的 `PASS:`。不要对测试使用生产存档。正式打包使用不带这些属性的 `build`，构建脚本禁止带测试属性制作发行 JAR，并额外排除测试类和夹具。

漫游志专项：`./gradlew runClient -PcompatSmoke -PguideSmoke`。同一独立实例内创建新的测试世界，检查 `run-compat-smoke/guide-result.txt` 与 `guide-shots/`。夹具包含 15 个声明展馆及明确合成的测试馆图，验证原生传送、检索时序、普通/大师与第三枚章、空检索、玩家预设、画布缩放与拖动、取章盖印，以及背包/工作台鼠标入口。测试夹具会跳过该实例首次辅助功能介绍，不修改玩家的日常游戏实例。

## 提交改动

欢迎先用 issue 描述问题，附 Minecraft、NeoForge、SMU 版本和复现步骤。不要上传服务器地址、玩家收藏、私人照片、签名或完整游戏目录；日志在分享前去掉个人信息。

修改交互时验证对应鼠标、键盘及保存流程；修改文档无需增加测试。存储变更需保留旧草稿兼容性；已经盖下的印迹与已有资源快照不可静默重写。SMU 章面应保留原图，不以替代图案隐藏捕获错误。

代码和原创素材贡献按项目 MIT 许可证提供；引入第三方素材时注明来源及其许可。

仅复测底图与自适应信封（跳过章袋悬停压力流程）：`./gradlew runClient -PsmokeTest -PphotoSmoke`。同样使用独立 run-smoke 目录并检查 smoke-result.txt；适合桌面焦点被导出目录窗口抢走时复查图像流程。

像素对齐专项：`./gradlew runClient -PsmokeTest -PalignmentSmoke`，检查 `run-smoke/alignment-result.txt` 和 `alignment-shots/`。它比较真实 GPU 的虚影/实际着墨轮廓，不依赖桌面鼠标焦点。

收集册专项：`./gradlew runClient -PsmokeTest -PbookSmoke`，检查 `run-smoke/book-result.txt` 和 `book-shots/`，包含分页与删除边界。
