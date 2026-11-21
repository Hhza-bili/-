#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""鲸鱼娘对话面板 + DeepSeek 聊天接口。"""

import json
import threading
import urllib.request

from kivy.clock import Clock
from kivy.graphics import Color, Rectangle
from kivy.metrics import dp
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.uix.textinput import TextInput

CHAT_URL = "https://api.deepseek.com/chat/completions"
SYSTEM_PROMPT = (
    "你是 DeepSeek 的鲸鱼娘：蓝色双马尾、白色女仆装、围裙上有一个小鲸鱼图案。"
    "说话可爱、简短、有元气，自称「本鲸」，偶尔用「呜」「呀」这类语气词。"
    "每次回答控制在 80 字以内，不要说教。"
)


def chat_request(api_key, messages, model="deepseek-chat", timeout=60):
    """同步请求一次对话（调用方负责放在线程里）。"""
    payload = {
        "model": model,
        "messages": [{"role": "system", "content": SYSTEM_PROMPT}] + list(messages),
        "stream": False,
        "max_tokens": 300,
    }
    req = urllib.request.Request(
        CHAT_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
            "User-Agent": "WhalePet/1.0",
        },
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return data["choices"][0]["message"]["content"].strip()


class ChatPanel(BoxLayout):
    """覆盖在桌宠上方的小对话窗口。"""

    def __init__(self, api_key="", on_close=None, **kwargs):
        kwargs.setdefault("orientation", "vertical")
        kwargs.setdefault("padding", [dp(12)] * 4)
        kwargs.setdefault("spacing", dp(8))
        kwargs.setdefault("size_hint", (1, 1))
        super().__init__(**kwargs)
        self.api_key = api_key
        self.on_close = on_close
        self.messages = []
        self._busy = False
        self._pending = None
        self.opacity = 0
        self.disabled = True

        with self.canvas.before:
            self._bgc = Color(0.055, 0.065, 0.10, 0.97)
            self._bg = Rectangle()
        self.bind(pos=self._sync, size=self._sync)
        self._build()

    def _sync(self, *args):
        self._bg.pos = self.pos
        self._bg.size = self.size

    # ------------------------------------------------------------------
    def _build(self):
        head = BoxLayout(size_hint_y=None, height=dp(34), spacing=dp(6))
        title = Label(text="鲸鱼娘", halign="left", valign="middle",
                      font_size=dp(16), bold=True, color=(0.72, 0.82, 1, 1))
        title.bind(size=lambda *a: setattr(title, "text_size", title.size))
        close = Button(text="×", size_hint=(None, 1), width=dp(34),
                       font_size=dp(22), background_normal="",
                       background_color=(0, 0, 0, 0), color=(0.8, 0.85, 1, 1))
        close.bind(on_release=lambda *a: self._close())
        head.add_widget(title)
        head.add_widget(close)
        self.add_widget(head)

        self._scroll = ScrollView(do_scroll_x=False)
        self._msgs = BoxLayout(orientation="vertical", size_hint_y=None, spacing=dp(6))
        self._msgs.bind(minimum_height=self._msgs.setter("height"))
        self._scroll.add_widget(self._msgs)
        self.add_widget(self._scroll)

        row = BoxLayout(size_hint_y=None, height=dp(44), spacing=dp(8))
        self._input = TextInput(
            hint_text="和本鲸说点什么…", multiline=False, font_size=dp(14),
            background_color=(0.13, 0.15, 0.21, 1), foreground_color=(1, 1, 1, 1),
            cursor_color=(1, 1, 1, 1), padding=[dp(10), dp(11)])
        self._input.bind(on_text_validate=self._send)
        send = Button(text="发送", size_hint=(None, 1), width=dp(62),
                      font_size=dp(14), background_normal="",
                      background_color=(0.24, 0.34, 0.86, 1))
        send.bind(on_release=self._send)
        row.add_widget(self._input)
        row.add_widget(send)
        self.add_widget(row)

        self._add_bubble("呜，本鲸在的！有什么想聊的？", mine=False)

    # ------------------------------------------------------------------
    def _add_bubble(self, text, mine=False):
        lbl = Label(
            text=text, size_hint_y=None, halign="right" if mine else "left",
            valign="top", font_size=dp(14),
            color=(0.62, 0.76, 1, 1) if mine else (0.95, 0.96, 1, 1))
        lbl.bind(width=lambda *a: setattr(lbl, "text_size", (max(1, lbl.width - dp(8)), None)))
        lbl.bind(texture_size=lambda *a: setattr(
            lbl, "height", max(dp(22), lbl.texture_size[1] + dp(6))))
        self._msgs.add_widget(lbl)
        Clock.schedule_once(lambda dt: setattr(self._scroll, "scroll_y", 0), 0.05)
        return lbl

    def _close(self):
        if self.on_close:
            self.on_close()

    # ------------------------------------------------------------------
    def _send(self, *args):
        text = self._input.text.strip()
        if not text or self._busy:
            return
        if not self.api_key:
            self._add_bubble("还没配置 API Key 哦，把 key 复制到剪贴板再点一下价签。")
            return
        self._input.text = ""
        self._add_bubble(text, mine=True)
        self.messages.append({"role": "user", "content": text})
        self._busy = True
        self._pending = self._add_bubble("…", mine=False)
        history = list(self.messages[-12:])
        threading.Thread(target=self._worker, args=(history,), daemon=True).start()

    def _worker(self, history):
        try:
            reply = chat_request(self.api_key, history)
            Clock.schedule_once(lambda dt: self._done(reply), 0)
        except Exception as exc:
            Clock.schedule_once(lambda dt: self._fail(str(exc)), 0)

    def _done(self, reply):
        self._busy = False
        if self._pending is not None:
            self._pending.text = reply
            self._pending = None
        else:
            self._add_bubble(reply)
        self.messages.append({"role": "assistant", "content": reply})

    def _fail(self, msg):
        self._busy = False
        short = msg if len(msg) < 120 else msg[:120] + "…"
        if self._pending is not None:
            self._pending.text = "呜…出错了：" + short
            self._pending = None
        else:
            self._add_bubble("呜…出错了：" + short)
