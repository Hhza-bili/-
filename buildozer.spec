[app]
title = DeepSeek Whale Pet
package.name = whale_pet
package.domain = org.dsh
source.dir = .
source.exclude_dirs = _src,tools,.github,__pycache__
source.exclude_patterns = preview_*.png,pose_*.png,whale_demo.gif,_preview_sheet.png
source.include_exts = py,txt,md,yml,json,png
version = 1.0.0

# Kivy + Pillow（Pillow 只用来把余额文字画成贴图）
# charset-normalizer 3.x 的 C 扩展 wheel 在 p4a 交叉编译下会被 pip 26 拒绝，
# 固定到纯 Python 的 2.1.1
requirements = python3,kivy,pillow,charset-normalizer==2.1.1

orientation = portrait
fullscreen = 0

# 悬浮窗要「显示在其他应用上层」，联网要 INTERNET，摸鱼反馈要 VIBRATE
android.permissions = INTERNET,SYSTEM_ALERT_WINDOW,VIBRATE
android.api = 33
android.minapi = 24
android.archs = arm64-v8a
android.allow_backup = True
android.accept_sdk_license = True
android.release_artifact = apk

p4a.bootstrap = sdl2
p4a.branch = master

[buildozer]
log_level = 2
warn_on_root = 1
