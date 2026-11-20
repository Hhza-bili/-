# DeepSeek 鲸鱼娘 · 悬浮球桌宠

蓝白配色、矢量绘制的桌面小窗桌宠，可显示 DeepSeek 账号实时余额。

## 运行（电脑 / Termux 有 X11 时）

```bash
pip install kivy pillow
python3 whale_pet.py --key=sk-your-key
```

余额也可以用环境变量：

```bash
export DEEPSEEK_API_KEY=sk-your-key
python3 whale_pet.py
```

或者把 key 写进同目录 `deepseek_key.txt`。没有 key 时余额价签自动隐藏，其它功能照常。

## 交互

| 操作 | 反应 |
| --- | --- |
| 轻点 | 压扁回弹、`>_<`、冒水花 |
| 长按 | 持续挤压、脸红、发抖 |
| 拖拽 | 身体倾斜、尾巴甩动、视线跟随 |
| 甩出 | 惯性滑行，撞边反弹并变形 |
| 双击 | 喷水、笑眼 `^^`、向上蹦 |
| 多指捏 | 按指数越多压得越扁 |
| 点价签 | 立即刷新余额 |

余额每 60 秒自动刷新；低于 ¥5 变橙色。

## 打包 APK

本仓库已带 `buildozer.spec` 和 GitHub Actions。把仓库推到 GitHub，
在 **Actions → build-apk** 里等 20~40 分钟，下载 `whale-pet-apk` 压缩包里的 APK 安装即可。

首次启动后需要在系统设置里给 App 打开 **「显示在其他应用上层」**，桌宠才会变成桌面悬浮球。

本地打包（需要 x86_64 Linux + JDK17，约 20 GB 磁盘）：

```bash
pip install buildozer cython==0.29.36
buildozer -v android debug     # 产物在 bin/
```

## 文件

- `whale_pet.py` —— 全部逻辑：矢量绘制、物理、触摸、悬浮窗、余额
- `preview.py` —— 渲染四种姿态的静态图
- `demo_anim.py` —— 假触摸跑一遍交互，逐帧导出验证动画
