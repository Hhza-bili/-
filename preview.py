#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把桌宠的几种姿态渲染成 PNG，用来肉眼验收造型。

用法：xvfb-run -a python3 preview.py
"""

import math
import os

os.environ.setdefault("KIVY_NO_ARGS", "1")
os.environ.setdefault("KIVY_NO_CONSOLELOG", "1")

from kivy.config import Config

SIDE = 360
Config.set("graphics", "width", str(SIDE))
Config.set("graphics", "height", str(SIDE))
Config.set("graphics", "resizable", "0")
Config.set("graphics", "borderless", "1")

from kivy.app import App
from kivy.clock import Clock
from kivy.core.window import Window

import whale_pet as wp

Window.clearcolor = (0, 0, 0, 0)

POSES = [
    ("idle", "待机"),
    ("poke", "被戳"),
    ("drag", "拖拽"),
    ("happy", "开心"),
]


class PreviewApp(App):
    def build(self):
        self.pet = wp.WhalePet()
        # 预览里不联网，直接塞一个假余额
        self.pet.balance_tag.set_value("\u00a5 8.50", "ok")
        self.pet.balance_tag.opacity = 1.0
        return self.pet

    def on_start(self):
        Clock.schedule_once(self._shoot, 0.6)

    def _step(self, n=1):
        for _ in range(n):
            self.pet._tick(1.0 / 60.0)

    def _shoot(self, _dt):
        out = os.path.dirname(os.path.abspath(__file__))
        for i, (name, _label) in enumerate(POSES):
            self._pose(name)
            self._step(3)
            self.pet.export_to_png(os.path.join(out, f"pose_{i}_{name}.png"))
            print("saved", name, flush=True)
        App.get_running_app().stop()

    def _pose(self, name):
        p = self.pet
        p.px, p.py = SIDE * 0.5, SIDE * 0.5
        p.vx = p.vy = 0.0
        p.squash = p.squash_v = 0.0
        p.tilt = p.tilt_v = 0.0
        p.dragging = False
        p.moved = 0.0
        p.press_time = 0.0
        p.happy_until = -1.0
        p.drag_speed = (0.0, 0.0)
        p.drag_target = (p.px, p.py)
        p.t = 0.9
        p.balance_tag.set_value("\u00a5 8.50", "ok")
        p.balance_tag.opacity = 1.0

        if name == "poke":
            p.squash = 0.40
            p.dragging = True
            p.moved = 0.0
        elif name == "drag":
            p.tilt = 14.0
            p.dragging = True
            p.moved = 40.0
            p.drag_speed = (14.0, 0.0)
        elif name == "happy":
            p.happy_until = 99.0


PreviewApp().run()
print("done")
