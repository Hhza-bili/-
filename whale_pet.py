#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DeepSeek 鲸鱼娘 · 悬浮球式桌宠（像素素材版）
==============================================

形象来自像素桌宠素材（蓝发女仆鲸鱼娘），逐帧播放待机 / 开心 / 拖拽 / 压扁动画。

素材来源
--------
https://github.com/1TRYOUT/dsh-whale-pixel-pet  (MIT)
套图版权归原作者；商用前请自行确认授权。

交互
----
* 轻点    —— 压一下 + 开心动画
* 长按    —— 下蹲压缩循环
* 拖拽    —— 悬空拖拽动画，身体倾斜、视线跟随
* 甩出    —— 惯性滑行，撞边反弹
* 点价签  —— 立即刷新 DeepSeek 余额

运行
----
    python3 whale_pet.py --key=sk-xxx
    DEEPSEEK_API_KEY=sk-xxx python3 whale_pet.py
"""

from __future__ import annotations

import json
import math
import os
import random
import sys
import threading
import urllib.request

os.environ.setdefault("KIVY_NO_ARGS", "1")

from kivy.config import Config

Config.set("graphics", "resizable", "0")
Config.set("graphics", "borderless", "1")
Config.set("input", "mouse", "mouse,multitouch_on_demand")
Config.set("kivy", "exit_on_escape", "0")

from kivy.clock import Clock
from kivy.core.window import Window
from kivy.graphics import (Color, PopMatrix, PushMatrix, Rectangle, Rotate,
                           RoundedRectangle, Scale, Translate)
from kivy.uix.widget import Widget

# --------------------------------------------------------------------------
# 常量
# --------------------------------------------------------------------------

PET_DP = 260                    # 悬浮窗逻辑边长（dp）
HERE = os.path.dirname(os.path.abspath(__file__))
ASSET_DIR = os.path.join(HERE, "assets")

BALANCE_URL = "https://api.deepseek.com/user/balance"
BALANCE_INTERVAL = 60.0
BALANCE_LOW = 5.0

# 物理
K_SQUASH = 420.0
C_SQUASH = 13.0
K_TILT = 90.0
C_TILT = 12.0
FRICTION = 0.86
BOUNCE = 0.58


def _clamp(v, lo, hi):
    return lo if v < lo else (hi if v > hi else v)


# --------------------------------------------------------------------------
# 素材
# --------------------------------------------------------------------------


def _load_texture(path):
    """PNG -> Kivy 纹理（最近邻采样，保持像素颗粒）。"""
    from PIL import Image

    from kivy.graphics.texture import Texture

    img = Image.open(path).convert("RGBA")
    img = img.transpose(Image.FLIP_TOP_BOTTOM)
    tex = Texture.create(size=img.size, colorfmt="rgba")
    tex.blit_buffer(img.tobytes(), colorfmt="rgba", bufferfmt="ubyte")
    tex.mag_filter = "nearest"
    tex.min_filter = "nearest"
    return tex


class SpriteSheet:
    """一张拼接好的精灵表 + 每帧的 UV 取景。"""

    def __init__(self, info):
        self.cols = info["cols"]
        self.rows = info["rows"]
        self.count = info["count"]
        self.fw = info["frame_w"]
        self.fh = info["frame_h"]
        self.cell_w = info.get("cell_w", info["frame_w"])
        self.cell_h = info.get("cell_h", info["frame_h"])
        self.pad = info.get("pad", 0)
        self.interval = info.get("interval", 0.08)
        self.texture = _load_texture(os.path.join(ASSET_DIR, info["file"]))
        tw, th = self.texture.size
        self.sheet_w = float(tw)
        self.sheet_h = float(th)

    def uv(self, i):
        """第 i 帧的 tex_coords（左下/右下/右上/左上）。"""
        i = max(0, min(i, self.count - 1))
        r, c = divmod(i, self.cols)
        # 精确到帧内像素，pad 保证缩放时不会吃到邻居
        x0 = (c * self.cell_w + self.pad) / self.sheet_w
        x1 = (c * self.cell_w + self.pad + self.fw) / self.sheet_w
        y1 = 1.0 - (r * self.cell_h + self.pad) / self.sheet_h
        y0 = 1.0 - (r * self.cell_h + self.pad + self.fh) / self.sheet_h
        return (x0, y0, x1, y0, x1, y1, x0, y1)

    def uv_size(self):
        return (self.fw / self.sheet_w, self.fh / self.sheet_h)


# --------------------------------------------------------------------------
# DeepSeek 余额
# --------------------------------------------------------------------------


def _app_key_path():
    try:
        from kivy.app import App
        app = App.get_running_app()
        base = app.user_data_dir if app else HERE
    except Exception:
        base = HERE
    return os.path.join(base, "deepseek_key.txt")


def read_api_key(explicit=""):
    """按优先级取 key：参数 -> 环境变量 -> 文件 -> 剪贴板。

    安卓上写不了环境变量，也没有方便落盘的位置，所以允许从剪贴板
    「领取」一次（复制 sk- 开头的 key 后打开 App 或点价签即可）。
    """
    key = (explicit or "").strip()
    if key:
        return key
    key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if key:
        return key
    for path in (_app_key_path(), os.path.join(HERE, "deepseek_key.txt")):
        try:
            if os.path.isfile(path):
                with open(path, encoding="utf-8") as fh:
                    found = fh.read().strip()
                if found:
                    return found
        except OSError:
            pass
    try:
        from kivy.core.clipboard import Clipboard
        txt = (Clipboard.paste() or "").strip()
        if txt.startswith("sk-") and len(txt) > 20:
            try:
                with open(_app_key_path(), "w", encoding="utf-8") as fh:
                    fh.write(txt)
            except OSError:
                pass
            return txt
    except Exception:
        pass
    return ""


def _http_get_json(url, api_key, timeout=10.0):
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": "Bearer " + api_key,
            "Accept": "application/json",
            "User-Agent": "WhalePet/1.0",
        },
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


# --------------------------------------------------------------------------
# 余额价签
# --------------------------------------------------------------------------

_FONT_CACHE = {}


def _kivy_font(name):
    import kivy
    return os.path.join(os.path.dirname(kivy.__file__), "data", "fonts", name)


def _text_texture(text, px, rgba):
    """Pillow 画字 -> 贴图，避开 Kivy 文本后端的环境差异。"""
    from PIL import Image, ImageDraw, ImageFont
    from kivy.graphics.texture import Texture

    key = "bold-%d" % int(px)
    font = _FONT_CACHE.get(key)
    if font is None:
        font = ImageFont.truetype(_kivy_font("Roboto-Bold.ttf"), max(8, int(px)))
        _FONT_CACHE[key] = font

    bbox = font.getbbox(text)
    tw = max(1, bbox[2] - bbox[0])
    th = max(1, bbox[3] - bbox[1])
    pad = 3
    img = Image.new("RGBA", (tw + pad * 2, th + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(img).text((pad - bbox[0], pad - bbox[1]), text, font=font, fill=rgba)
    img = img.transpose(Image.FLIP_TOP_BOTTOM)
    tex = Texture.create(size=img.size, colorfmt="rgba")
    tex.blit_buffer(img.tobytes(), colorfmt="rgba", bufferfmt="ubyte")
    tex.mag_filter = "linear"
    return tex, img.size


class BalanceTag(Widget):
    """鲸鱼头顶的小价签：白底圆角胶囊 + 余额数字。"""

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.size_hint = (None, None)
        self.opacity = 0.0
        self._tone = "ok"
        self._font_px = 14.0
        self._text = ""
        with self.canvas.before:
            Color(0.04, 0.07, 0.16, 0.14)
            self._shadow = RoundedRectangle(radius=[16])
            self._bg_color = Color(1, 1, 1, 0.95)
            self._bg = RoundedRectangle(radius=[16])
        with self.canvas:
            self._tex_rect = Rectangle()
        self.bind(pos=self._sync, size=self._sync)

    def _sync(self, *args):
        self._shadow.pos = (self.x, self.y - 2)
        self._shadow.size = self.size
        self._bg.pos = self.pos
        self._bg.size = self.size
        self._tex_rect.pos = self.pos
        self._tex_rect.size = self.size

    def set_font_px(self, px):
        if abs(px - self._font_px) >= 0.4:
            self._font_px = float(px)
            self._rebuild()

    def set_value(self, text, tone="ok"):
        if text == self._text and tone == self._tone:
            return
        self._text = text
        self._tone = tone
        self._rebuild()

    def _rebuild(self):
        if not self._text:
            return
        if self._tone == "ok":
            fill = (41, 64, 158, 255)
            self._bg_color.rgba = (1.0, 1.0, 1.0, 0.95)
        elif self._tone == "low":
            fill = (214, 86, 12, 255)
            self._bg_color.rgba = (1.0, 0.95, 0.88, 0.97)
        else:
            fill = (107, 117, 140, 255)
            self._bg_color.rgba = (0.93, 0.95, 0.98, 0.95)
        try:
            tex, (tw, th) = _text_texture(self._text, self._font_px, fill)
        except Exception as exc:
            print("[balance] 文字贴图失败:", exc)
            return
        self._tex_rect.texture = tex
        self.size = (tw + 18, th + 12)
        self._sync()


# --------------------------------------------------------------------------
# 安卓悬浮窗
# --------------------------------------------------------------------------


class OverlayBridge:
    """把窗口改成 TYPE_APPLICATION_OVERLAY 悬浮窗；失败则静默降级。"""

    def __init__(self):
        self.active = False
        self.wm = None
        self.lp = None
        self.window = None

    @staticmethod
    def _find_surface_views(view, out, depth=0):
        from jnius import autoclass

        SurfaceView = autoclass("android.view.SurfaceView")
        ViewGroup = autoclass("android.view.ViewGroup")
        if depth > 12:
            return
        if isinstance(view, SurfaceView):
            out.append(view)
            return
        if isinstance(view, ViewGroup):
            for i in range(view.getChildCount()):
                OverlayBridge._find_surface_views(view.getChildAt(i), out, depth + 1)

    _pet_w = 260
    _pet_h = 260

    def enable(self, width_px, height_px, x_px=0, y_px=0):
        """切换成悬浮窗，返回 (是否成功, 原因)。"""
        if not self._is_android():
            return False, "not android"
        try:
            from jnius import autoclass

            PythonActivity = autoclass("org.kivy.android.PythonActivity")
            Context = autoclass("android.content.Context")
            Settings = autoclass("android.provider.Settings")
            Gravity = autoclass("android.view.Gravity")
            PixelFormat = autoclass("android.graphics.PixelFormat")
            Build = autoclass("android.os.Build")

            activity = PythonActivity.mActivity
            if activity is None:
                return False, "no activity"

            if Build.VERSION.SDK_INT >= 23 and not Settings.canDrawOverlays(activity):
                try:
                    Intent = autoclass("android.content.Intent")
                    Uri = autoclass("android.net.Uri")
                    intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + activity.getPackageName()))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                except Exception:
                    pass
                return False, "no overlay permission"

            window = activity.getWindow()
            lp = window.getAttributes()
            lp.type = 2038 if Build.VERSION.SDK_INT >= 26 else 2002
            # NOT_FOCUSABLE(8) | NOT_TOUCH_MODAL(32) | LAYOUT_NO_LIMITS(512)
            # 千万别加 FLAG_NOT_TOUCHABLE(16)，那会让整个窗口收不到触摸
            lp.flags = 8 | 32 | 512
            lp.width = int(width_px)
            lp.height = int(height_px)
            lp.gravity = Gravity.TOP | Gravity.LEFT
            lp.x = int(x_px)
            lp.y = int(y_px)
            window.setAttributes(lp)

            # SurfaceView 设成半透明并置顶，画面才能浮在桌面上
            views = []
            try:
                OverlayBridge._find_surface_views(window.getDecorView(), views)
                for sv in views:
                    sv.setZOrderOnTop(True)
                    sv.getHolder().setFormat(PixelFormat.TRANSLUCENT)
            except Exception:
                pass

            self.active = True
            self.wm = activity.getSystemService(Context.WINDOW_SERVICE)
            self.lp = lp
            self.window = window
            self._pet_w = int(width_px)
            self._pet_h = int(height_px)
            return True, "ok"
        except Exception as exc:
            self.active = False
            return False, repr(exc)

    def set_panel_mode(self, on):
        """聊天面板需要输入框能获得焦点，所以临时放大窗口。"""
        if not self.active:
            return
        try:
            if on:
                self.lp.width = -1    # MATCH_PARENT
                self.lp.height = -1
                self.lp.flags = self.lp.flags & ~8
                self.lp.softInputMode = 0x10   # ADJUST_RESIZE
            else:
                self.lp.width = self._pet_w
                self.lp.height = self._pet_h
                self.lp.flags = self.lp.flags | 8
                self.lp.softInputMode = 0
            self.window.setAttributes(self.lp)
        except Exception:
            pass

    def move_to(self, x_px, y_px):
        if not self.active:
            return
        try:
            self.lp.x = int(x_px)
            self.lp.y = int(y_px)
            self.window.setAttributes(self.lp)
        except Exception:
            pass

    def vibrate(self, ms=18):
        if not self._is_android():
            return
        try:
            from jnius import autoclass

            PythonActivity = autoclass("org.kivy.android.PythonActivity")
            Context = autoclass("android.content.Context")
            activity = PythonActivity.mActivity
            vib = activity.getSystemService(Context.VIBRATOR_SERVICE)
            if vib is not None:
                vib.vibrate(int(ms))
        except Exception:
            pass

    @staticmethod
    def _is_android():
        try:
            from kivy.utils import platform
            return platform == "android"
        except Exception:
            return False


# --------------------------------------------------------------------------
# 主体
# --------------------------------------------------------------------------


class WhalePet(Widget):
    def __init__(self, bridge: OverlayBridge | None = None, api_key: str = "", **kwargs):
        super().__init__(**kwargs)
        self.bridge = bridge or OverlayBridge()
        self.api_key = read_api_key(api_key)
        self._key_hint = False

        # 位置与速度
        self.px = Window.width * 0.5
        self.py = Window.height * 0.45
        self.vx = 0.0
        self.vy = 0.0

        # 姿态
        self.squash = 0.0
        self.squash_v = 0.0
        self.tilt = 0.0
        self.tilt_v = 0.0
        self.look_x = 0.0
        self.look_y = 0.0
        self.tail_swing = 0.0

        # 动画
        self.anims = {}
        self.anim_name = "idle"
        self.anim_frame = 0
        self.anim_timer = 0.0
        self.anim_loop = True
        self._load_anims()

        # 触摸
        self.dragging = False
        self.drag_offset = (0.0, 0.0)
        self.drag_target = (self.px, self.py)
        self.drag_speed = (0.0, 0.0)
        self.down_time = 0.0
        self.press_time = 0.0
        self.moved = 0.0
        self.last_tap = -9.0
        self.happy_until = -1.0
        self.touches = {}

        # 时间
        self.t = 0.0
        self.bubbles = []

        # 余额
        self.balance_tag = BalanceTag()
        self.add_widget(self.balance_tag)
        self.balance_value = None
        self._fetching = False
        self._balance_timer = 0.6

        # 聊天面板（懒创建）
        self.chat_panel = None
        self._chat_open = False

        self._build_canvas()
        self._tick_ev = Clock.schedule_interval(self._tick, 1.0 / 60.0)

    # ------------------------------------------------------------------
    def _load_anims(self):
        meta_path = os.path.join(ASSET_DIR, "anim_meta.json")
        with open(meta_path, encoding="utf-8") as fh:
            meta = json.load(fh)
        for name, info in meta.items():
            self.anims[name] = SpriteSheet(info)
        self._frame_h = self.anims["idle"].fh
        self._frame_w = self.anims["idle"].fw

    def _build_canvas(self):
        with self.canvas:
            PushMatrix()
            self.tr = Translate(0, 0, 0)
            self.rot = Rotate(angle=0, axis=(0, 0, 1), origin=(0, 0))
            self.scl = Scale(1, 1, 1, origin=(0, 0))
            Color(1, 1, 1, 1)
            self.rect = Rectangle(texture=self.anims["idle"].texture)
            PopMatrix()

    # ------------------------------------------------------------------
    def _unit(self):
        """1 个素材像素 = 多少屏幕像素。取整数倍，像素才够锐。"""
        want = (Window.height * 0.86) / float(self._frame_h)
        return float(max(1, int(round(want))))

    def _radius(self):
        # 角色本体约占帧高的 90%，这里给一个略保守的外接半径
        return self._unit() * 62.0

    # ------------------------------------------------------------------
    def _tick(self, dt):
        dt = _clamp(dt, 1e-4, 0.05)
        self.t += dt
        self._step_physics(dt)
        self._step_state(dt)
        self._step_anim(dt)
        self._step_balance(dt)
        self._paint()

    def _step_physics(self, dt):
        h = dt * 0.5
        for _ in range(2):
            acc = -K_SQUASH * self.squash - C_SQUASH * self.squash_v
            self.squash_v += acc * h
            self.squash += self.squash_v * h
            self.squash = _clamp(self.squash, -0.5, 0.5)

            acc = -K_TILT * self.tilt - C_TILT * self.tilt_v
            self.tilt_v += acc * h
            self.tilt += self.tilt_v * h
            self.tilt = _clamp(self.tilt, -28.0, 28.0)

            if self.dragging:
                tx, ty = self.drag_target
                k = 1.0 - math.exp(-22.0 * h)
                self.px += (tx - self.px) * k
                self.py += (ty - self.py) * k
            else:
                self.px += self.vx * h
                self.py += self.vy * h
                damp = math.pow(FRICTION, h)
                self.vx *= damp
                self.vy *= damp

        if not self.dragging:
            self._bounce_bounds()
            if math.hypot(self.vx, self.vy) < 6.0:
                self.vx = self.vy = 0.0

    def _bounce_bounds(self):
        m = self._radius() * 0.92
        w, h = Window.width, Window.height
        hit = 0.0
        if self.px < m:
            self.px = m
            self.vx = abs(self.vx) * BOUNCE
            hit = 1.0
        elif self.px > w - m:
            self.px = w - m
            self.vx = -abs(self.vx) * BOUNCE
            hit = 1.0
        if self.py < m:
            self.py = m
            self.vy = abs(self.vy) * BOUNCE
            hit = 1.0
        elif self.py > h - m:
            self.py = h - m
            self.vy = -abs(self.vy) * BOUNCE
            hit = 1.0
        if hit:
            speed = math.hypot(self.vx, self.vy)
            if speed > 120:
                self.squash_v -= min(speed * 0.0016, 2.2)

    def _step_state(self, dt):
        # 视线跟随
        target_lx = target_ly = 0.0
        if self.dragging:
            target_lx = _clamp(self.drag_speed[0] * 0.05, -1.0, 1.0)
            target_ly = _clamp(self.drag_speed[1] * 0.05, -1.0, 1.0)
        elif abs(self.vx) + abs(self.vy) > 30:
            target_lx = _clamp(self.vx / 900.0, -1.0, 1.0)
            target_ly = _clamp(self.vy / 900.0, -1.0, 1.0)
        self.look_x += (target_lx - self.look_x) * min(1.0, 8.0 * dt)
        self.look_y += (target_ly - self.look_y) * min(1.0, 8.0 * dt)

        # 拖拽倾斜
        if self.dragging:
            tilt_target = _clamp(-self.drag_speed[0] * 3.2, -20.0, 20.0)
            self.tilt_v += (tilt_target - self.tilt) * 30.0 * dt
            self.drag_speed = (self.drag_speed[0] * 0.7, self.drag_speed[1] * 0.7)

        # 长按累积
        if self.dragging and self.moved < 12:
            self.press_time += dt
        else:
            self.press_time = 0.0

        self._idle_fart = getattr(self, "_idle_fart", 2.5) - dt
        if self._idle_fart <= 0.0:
            self._spawn_bubble(1)
            self._idle_fart = random.uniform(3.0, 7.0)

        self._step_bubbles(dt)
        self._follow_window()

    _win_x = 0.0
    _win_y = 80.0

    def _follow_window(self):
        if not getattr(self.bridge, "active", False):
            return
        cx, cy = Window.width * 0.5, Window.height * 0.5
        dx, dy = self.px - cx, self.py - cy
        if abs(dx) < 2 and abs(dy) < 2:
            return
        self._win_x += dx
        self._win_y -= dy
        self.bridge.move_to(self._win_x, self._win_y)
        self.px, self.py = cx, cy

    # ------------------------------------------------------------------
    def _step_bubbles(self, dt):
        alive = []
        for b in self.bubbles:
            b[0] += b[2] * dt
            b[1] += b[3] * dt
            b[2] *= math.pow(0.2, dt)
            b[3] *= math.pow(0.6, dt)
            b[4] -= dt
            if b[4] > 0:
                alive.append(b)
        self.bubbles = alive

    def _spawn_bubble(self, count=1):
        for _ in range(count):
            self.bubbles.append([0.0, 1.0, random.uniform(-40, 40),
                                 random.uniform(40, 90), random.uniform(0.5, 1.1)])
            if len(self.bubbles) > 6:
                self.bubbles.pop(0)

    # ------------------------------------------------------------------
    def _desired_anim(self):
        if self.dragging:
            return "drag" if self.moved > 16 else "squash"
        if self.happy_until > self.t:
            return "happy"
        return "idle"

    def _set_anim(self, name, loop=True):
        if name not in self.anims:
            name = "idle"
        if name == self.anim_name and loop == self.anim_loop:
            return
        self.anim_name = name
        self.anim_frame = 0
        self.anim_timer = 0.0
        self.anim_loop = loop

    def _step_anim(self, dt):
        want = self._desired_anim()
        self._set_anim(want, loop=(want != "happy"))

        anim = self.anims[self.anim_name]
        self.anim_timer += dt
        guard = 0
        while self.anim_timer >= anim.interval and guard < 8:
            guard += 1
            self.anim_timer -= anim.interval
            self.anim_frame += 1
            if self.anim_frame >= anim.count:
                if self.anim_loop:
                    self.anim_frame = 0
                else:
                    self.anim_frame = anim.count - 1
                    self.happy_until = -1.0
                    self._set_anim("idle", True)
                    break

    # ------------------------------------------------------------------
    def _paint(self):
        anim = self.anims[self.anim_name]
        self.rect.texture = anim.texture
        self.rect.tex_coords = anim.uv(self.anim_frame)
        self.rect.pos = (-anim.fw * 0.5, -anim.fh * 0.5)
        self.rect.size = (anim.fw, anim.fh)

        sq = self.squash
        sy = 1.0 - sq * 0.42
        sx = 1.0 + sq * 0.30
        unit = self._unit()

        self.tr.x, self.tr.y = self.px, self.py
        self.rot.angle = self.tilt
        self.scl.x = unit * sx
        self.scl.y = unit * sy
        self.scl.z = 1.0

        # 余额价签
        tag = self.balance_tag
        if tag.opacity > 0:
            tag.set_font_px(max(9.0, unit * 6.2))
            tw, th = tag.size
            bx = _clamp(self.px - tw * 0.5, 3.0, max(3.0, Window.width - tw - 3.0))
            by = _clamp(self.py + unit * 58.0, 3.0, max(3.0, Window.height - th - 3.0))
            tag.pos = (bx, by)

    # ------------------------------------------------------------------
    # 触摸
    # ------------------------------------------------------------------
    def _hit(self, wx, wy):
        dx = wx - self.px
        dy = wy - self.py
        r = self._radius() * 1.10
        return (dx * dx + dy * dy) <= r * r

    def on_touch_down(self, touch):
        if self._chat_open:
            return super().on_touch_down(touch)
        if not self.collide_point(*touch.pos):
            return False

        tag = self.balance_tag
        if tag.opacity > 0 and tag.collide_point(*touch.pos):
            self.refresh_balance()
            self.squash_v += 0.9
            return True

        if not self._hit(touch.x, touch.y):
            return False

        touch.grab(self)
        self.touches[touch.uid] = touch

        if len(self.touches) == 1:
            self.dragging = True
            self.drag_offset = (self.px - touch.x, self.py - touch.y)
            self.drag_target = (self.px, self.py)
            self.down_time = self.t
            self.moved = 0.0
            self.press_time = 0.0
            self.squash_v += 1.6
            self.bridge.vibrate(12)
        else:
            self.squash_v += 1.2 / len(self.touches)
        return True

    def on_touch_move(self, touch):
        if self._chat_open:
            return super().on_touch_move(touch)
        if touch.grab_current is not self:
            return False
        if touch.uid not in self.touches:
            return False
        if len(self.touches) == 1:
            vx = touch.x - touch.px
            vy = touch.y - touch.py
            self.moved += math.hypot(vx, vy)
            self.drag_target = (touch.x + self.drag_offset[0], touch.y + self.drag_offset[1])
            self.drag_speed = (vx, vy)
        return True

    def on_touch_up(self, touch):
        if self._chat_open:
            return super().on_touch_up(touch)
        if touch.grab_current is not self:
            return False
        touch.ungrab(self)
        self.touches.pop(touch.uid, None)
        if self.touches:
            return True

        self.dragging = False
        dt = max(1.0 / 240.0, self.t - max(self.down_time, self.t - 0.05))
        dx = touch.x - touch.px
        dy = touch.y - touch.py

        tap = self.moved < 14.0 and (self.t - self.down_time) < 0.42
        if tap:
            self.vx += dx * 200.0
            self.vy += dy * 200.0
            self.squash_v += 1.4
            self.bridge.vibrate(16)
            if self.t - self.last_tap < 0.34:
                # 双击 -> 打开聊天
                self.last_tap = -9.0
                self._open_chat()
            else:
                self.happy_until = self.t + 2.2
                self.last_tap = self.t
        else:
            self.vx = _clamp(dx / dt * 1.8, -1500, 1500)
            self.vy = _clamp(dy / dt * 1.8, -1500, 1500)
            self.tilt_v += _clamp(dx * 0.4, -8, 8)
        return True

    def on_size(self, *args):
        self.px = _clamp(self.px, 0, self.width)
        self.py = _clamp(self.py, 0, self.height)
        if self.chat_panel is not None:
            self.chat_panel.size = (self.width, self.height)
            self.chat_panel.pos = (0, 0)

    # ------------------------------------------------------------------
    # 余额
    # ------------------------------------------------------------------
    def _step_balance(self, dt):
        if not self.api_key:
            if not self._key_hint:
                self._key_hint = True
                self.balance_tag.set_value("key?", "muted")
                self.balance_tag.opacity = 1.0
            return
        self._balance_timer -= dt
        if self._balance_timer <= 0.0:
            self._balance_timer = BALANCE_INTERVAL
            self.refresh_balance()

    def refresh_balance(self):
        if not self.api_key:
            # 再试一次：用户可能刚把 key 复制到剪贴板
            self.api_key = read_api_key("")
            if not self.api_key:
                self.balance_tag.set_value("key?", "muted")
                self.balance_tag.opacity = 1.0
                return
        if self._fetching:
            return
        self._fetching = True
        self.balance_tag.set_value("···", "muted")
        self.balance_tag.opacity = 1.0
        threading.Thread(target=self._balance_worker, daemon=True).start()

    def _balance_worker(self):
        try:
            data = _http_get_json(BALANCE_URL, self.api_key)
            Clock.schedule_once(lambda dt: self._balance_ok(data), 0)
        except Exception as exc:
            Clock.schedule_once(lambda dt: self._balance_err(str(exc)), 0)

    def _balance_ok(self, data):
        self._fetching = False
        infos = data.get("balance_infos") or []
        if not infos:
            self.balance_tag.set_value("\u2014", "muted")
            self.balance_tag.opacity = 1.0
            return
        info = next((i for i in infos if i.get("currency") == "CNY"), infos[0])
        try:
            value = float(info.get("total_balance") or 0)
        except (TypeError, ValueError):
            value = 0.0
        self.balance_value = value
        tone = "low" if value < BALANCE_LOW else "ok"
        self.balance_tag.set_value("\u00a5%.2f" % value, tone)
        self.balance_tag.opacity = 1.0

    # ------------------------------------------------------------------
    # 聊天
    # ------------------------------------------------------------------
    def _open_chat(self):
        if self._chat_open:
            return
        if self.chat_panel is None:
            from chat import ChatPanel
            self.chat_panel = ChatPanel(api_key=self.api_key, on_close=self._close_chat)
            self.add_widget(self.chat_panel)
        self.chat_panel.size = (self.width, self.height)
        self.chat_panel.pos = (0, 0)
        self.chat_panel.opacity = 1
        self.chat_panel.disabled = False
        self._chat_open = True
        self.bridge.set_panel_mode(True)

    def _close_chat(self):
        if not self._chat_open:
            return
        self._chat_open = False
        if self.chat_panel is not None:
            self.chat_panel.opacity = 0
            self.chat_panel.disabled = True
        self.bridge.set_panel_mode(False)

    def _balance_err(self, msg):
        self._fetching = False
        self.balance_tag.set_value("\u2014", "muted")
        self.balance_tag.opacity = 1.0
        print("[balance] 查询失败:", msg)


# --------------------------------------------------------------------------
# 启动
# --------------------------------------------------------------------------


def _dp_to_px(dp):
    try:
        d = Window.density or 1.0
    except Exception:
        d = 1.0
    return int(dp * d)


def _parse_key():
    explicit = ""
    for a in sys.argv[1:]:
        if a.startswith("--key="):
            explicit = a.split("=", 1)[1].strip()
    return read_api_key(explicit)


def _platform():
    try:
        from kivy.utils import platform
        return platform
    except Exception:
        return "linux"


def main():
    from kivy.app import App

    px = _dp_to_px(PET_DP)
    api_key = _parse_key()

    Window.clearcolor = (0, 0, 0, 0)

    is_android = _platform() == "android"
    if is_android:
        # 安卓上 Window.position 只接受 'auto'/'custom'，不能赋坐标；
        # 悬浮窗的实际位置由 OverlayBridge 的 LayoutParams 决定。
        for attr, value in (("fullscreen", False), ("size", (px, px))):
            try:
                setattr(Window, attr, value)
            except Exception:
                pass
    else:
        Window.size = (px, px)

    bridge = OverlayBridge()
    if not api_key:
        print("[whale] 未找到 DEEPSEEK_API_KEY，余额显示已关闭。"
              "可设置环境变量或写进 deepseek_key.txt。", flush=True)

    class WhalePetApp(App):
        def build(self):
            root = Widget()
            self.pet = WhalePet(bridge=bridge, api_key=api_key)
            root.add_widget(self.pet)
            return root

        def on_start(self):
            self._ensure_overlay()

        def on_resume(self):
            # 用户从「悬浮窗权限」设置页回来时重试
            self._ensure_overlay()

        def _ensure_overlay(self):
            if not is_android or bridge.active:
                return
            ok, reason = bridge.enable(px, px, 60, 300)
            print("[overlay]", ok, reason, flush=True)

    WhalePetApp().run()


if __name__ == "__main__":
    main()
