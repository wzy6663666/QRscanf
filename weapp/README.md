# QRscanf 微信小程序版

安卓版 QRscanf 的小程序实现：扫码识别 13 种码制、生成 8 种码制、扫描填充 / 复刻，全部在手机本地完成。

## 怎么跑起来

1. 微信开发者工具 → 导入项目 → 目录选 `weapp/`。
2. AppID 填自己的小程序 AppID（`project.config.json` 里现在是 `touristappid`，只能本地预览）。
3. 直接编译即可，不需要 npm 安装、不需要服务器、不需要配置合法域名。

正式发布：个人主体注册免费（身份证 + 未注册过公众平台的邮箱），选「工具」类目 → 完成 ICP 备案 → 提交审核（1–7 个工作日）。

## 功能对照

| 能力 | 安卓版 | 小程序版 |
| --- | --- | --- |
| 单次扫码 / 结果详情 | ✅ | ✅ |
| 连续扫码 + 自动去重 | ✅ | ✅（每次识别后自动回到扫码界面） |
| 图片识别 | ✅ 一张图多个码 | ⚠️ 一次一张、单码（微信扫码只返回一个结果） |
| 13 种码制识别 | ✅ ML Kit | ✅ 微信扫码引擎（含 Data Matrix / PDF417 / Aztec / Codabar / UPC） |
| 自定义取景框 / 多摄切换 / 变焦 / 对焦 | ✅ | ❌ 无对应 API，改用微信扫码界面 |
| 反色 / 低对比增强 / 小码兜底 | ✅ | ⚠️ 由微信引擎内部处理，不可控 |
| QR 生成（版本/掩码/容错/编码/尺寸/留白/配色） | ✅ | ✅ 全参数 |
| Data Matrix 生成 | ✅ | ✅ 10×10 ~ 48×48 方形符号 |
| Code128 / Code39 / Code93 / EAN-13 / EAN-8 / ITF | ✅ | ✅ |
| PDF417 / Aztec / Codabar 生成 | ✅ | ❌ 仅识别 |
| 扫描填充 / 复刻 | ✅ | ✅ 解析版本、掩码、纠错级别、模块尺寸、静区、配色，并生成对比预览 |
| 保存到相册 | ✅ | ✅ |
| 扫码记录 / 生成记录 / 设置 | ✅ | ✅ |
| 完全离线 | ✅ | ⚠️ 识别与生成都在本地，但代码包首次需联网加载 |

## 实现要点

- **编码器全部是自研纯 JS**，没有依赖 zxing-wasm，包体小、冷启动快：
  - `utils/qr.js`：QR 1–40 版本、L/M/Q/H、数字/字母数字/字节模式、掩码 0–7 可强制、UTF-8 与 ISO-8859-1。
  - `utils/dm.js`：Data Matrix ECC200，ASCII 数字对压缩 + Base256（非 ASCII 走 UTF-8），RS 纠错 + Utah 放置。
  - `utils/oned.js`：Code128（含 Code B/C 切换）、Code39、Code93（双校验位）、EAN-13/EAN-8（自动补校验位）、ITF。
- `utils/render.js`：把符号画到 canvas 2d，按整数模块对齐，合并同色 run 减少 fillRect 调用。
- `utils/analyze.js`：复刻用的图像分析。Otsu 二值化 → 3×3 多数表决去噪 → 1:1:3:1:1 定位图案检测 + 纵向复核 → 三定位点定仿射 → 采样模块矩阵 → 读格式信息（BCH 纠错）得出版本/掩码/纠错级别，并用定位图案与定时图案做一致性校验，避免误判。

## 校验

仓库根目录 `.tools/verify/` 下有一套 Node 校验脚本，用 `@zxing/library` 做回读与矩阵比对：

```bash
cd .tools/verify
NODE_PATH=<你的 node_modules> node verify-qr.js      # 二维码：40 版本 × 4 纠错 × 9 掩码 × 8 内容
NODE_PATH=<...> node verify-dm.js                    # Data Matrix
NODE_PATH=<...> node verify-dm-sweep.js              # Data Matrix 1~174 字符全长度
NODE_PATH=<...> node verify-oned.js                  # 一维码
NODE_PATH=<...> node verify-render.js                # 生成 → 渲染 → 光栅化 → 解码 全链路
NODE_PATH=<...> node verify-analyze.js               # 复刻参数解析
NODE_PATH=<...> node sim-pages.js                    # 模拟小程序环境跑全部页面交互
```

当前结果：二维码 368/368、Data Matrix 358/358、一维码 12/12、渲染链路 34/35（仅「浅色前景 + 深色背景」的反色码失败，属解码器限制，UI 里已有提示）、复刻解析 75/75、页面冒烟 62/62。

## 已知限制

- iOS 上小程序不能添加到主屏幕，只能从微信进入。
- `wx.scanCode` 是全屏界面，拿不到自定义取景框、变焦、多摄；连续扫码靠反复调起。
- 复刻时无法把「相册里的那张图」直接交给解码器，需要再选一次同一张图取内容（页面已说明）。
- 主包 2MB 限制：当前源码约 130KB（压包后 70KB 上下），空间充裕。
- 反色码（浅底深字反过来的那种）部分扫码器识别不了，生成页会给出提示。

## 目录

```
weapp/
├── app.js / app.json / app.wxss
├── assets/tabbar/           tabBar 图标
├── pages/
│   ├── index/               首页
│   ├── scan/                单次 / 连续扫码
│   ├── image/               相册识别
│   ├── generate/            生成 + 参数 + 预览 + 保存
│   ├── clone/               扫描填充 / 复刻
│   ├── records/             扫码记录 + 生成记录
│   ├── result/              单条详情
│   └── settings/            设置
└── utils/
    ├── qr.js  dm.js  oned.js   编码器
    ├── symbol.js               统一入口
    ├── render.js               Canvas 绘制
    ├── analyze.js              复刻图像分析
    ├── meta.js  util.js  store.js
```
