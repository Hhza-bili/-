#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用假触摸事件跑一遍完整交互，逐帧导出，验证物理与动画。

    xvfb-run -a python3 demo_anim.py
"""

import math
import os

os.environ.setdefault("KIVY_NO_ARGS", "1")
os.environ.setdefault("KIVY_NO_CONSOLELOG", "1")

from kivy.config import Config

SIDE = 340
Config.set("graphics", "width", str(SIDE))
Config.set("graphics", "height", str(SIDE))
Config.set("graphics", "resizable", "0")
Config.set("graphics", "borderless", "1")

from kivy.app import App
from kivy.clock import Clock
from kivy.core.window import Window

import whale_pet as wp

Window.clearcolor = (0, 0, 0, 0)
HERE = os.path.dirname(os.path.abspath(__file__))


class FakeTouch:
    """够用的 MotionEvent 替身。"""

    def __init__(self, x, y, uid=1):
        self.uid = uid
        self.x, self.y = x, y
        self.px, self.py = x, y
        self.pos = (x, y)
        self._grab = None

    @property
    def grab_current(self):
        return self._grab

    def grab(self, widget):
        self._grab = widget

    def ungrab(self, widget):
        self._grab = None

    def move_to(self, x, y):
        self.px, self.py = self.x, self.y
        self.x, self.y = x, y
        self.pos = (x, y)


class Demo(App):
    def build(self):
        self.pet = wp.WhalePet()
        self.pet.balance_tag.set_value("\u00a5 8.50", "ok")
        self.pet.balance_tag.opacity = 1.0
        self.frames = []
        self.n = 0
        return self.pet

    def on_start(self):
        Clock.schedule_once(self._run, 0.5)

    def _snap(self):
        name = os.path.join(HERE, "_frames", "f%03d.png" % self.n)
        self.pet.export_to_png(name)
        self.n += 1

    def _advance(self, frames=1):
        for _ in range(frames):
            self.pet._tick(1.0 / 60.0)
            self._snap()

    def _run(self, _dt):
        os.makedirs(os.path.join(HERE, "_frames"), exist_ok=True)
        pet = self.pet
        # 摆到左下，留出向右上飞的余地
        pet.px, pet.py = SIDE * 0.32, SIDE * 0.30
        pet.vx = pet.vy = 0.0
        for _ in range(20):
            pet._tick(1.0 / 60.0)

        # ---- 1. 轻点一下 ----
        t = FakeTouch(pet.px, pet.py)
        pet.on_touch_down(t)
        self._advance(2)
        pet.on_touch_up(t)          # 原地松手 -> tap
        self._advance(10)

        # ---- 2. 按住拖到右上 ----
        t2 = FakeTouch(pet.px, pet.py, uid=2)
        pet.on_touch_down(t2)
        start = (pet.px, pet.py)
        end = (SIDE * 0.78, SIDE * 0.74)
        steps = 14
        for i in range(1, steps + 1):
            k = i / steps
            # 稍微带一点弧线
            x = start[0] + (end[0] - start[0]) * k
            y = start[1] + (end[1] - start[1]) * k + math.sin(k * math.pi) * 34.0
            t2.move_to(x, y)
            pet.on_touch_move(t2)
            self._advance(1)

        # ---- 3. 甩出去 ----
        t2.move_to(end[0] + 26, end[1] + 20)
        pet.on_touch_move(t2)
        self._advance(2)
        pet.on_touch_up(t2)

        # ---- 4. 惯性飞行 + 撞墙反弹 ----
        self._advance(46)

        print("frames:", self.n, flush=True)
        App.get_running_app().stop()


Demo().run()
