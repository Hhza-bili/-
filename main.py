#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""python-for-android 入口。

正常情况启动桌宠；启动期崩溃时把 traceback 直接画到屏幕上，
方便在不能抓 logcat 的手机上定位问题。
"""

import os
import traceback


def _write_log(err):
    """尽量落一份文件，写不进去就算了。"""
    here = os.path.dirname(os.path.abspath(__file__))
    for path in (os.path.join(here, "crash.log"),
                 "/sdcard/Download/DSH-Go/whale_crash.log"):
        try:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(err)
        except OSError:
            pass


def _show_error(err):
    from kivy.app import App
    from kivy.core.window import Window
    from kivy.uix.label import Label
    from kivy.uix.scrollview import ScrollView

    text = err[-6000:]

    class ErrorApp(App):
        def build(self):
            try:
                Window.clearcolor = (0.05, 0.06, 0.09, 1)
            except Exception:
                pass
            view = ScrollView()
            label = Label(text=text, font_size=10, color=(1, 0.82, 0.82, 1),
                          size_hint=(1, None), halign="left", valign="top")
            label.bind(width=lambda *_: setattr(label, "text_size", (label.width - 20, None)))
            label.bind(texture_size=lambda *_: setattr(label, "height", label.texture_size[1]))
            view.add_widget(label)
            return view

    ErrorApp().run()


def _boot():
    try:
        from whale_pet import main
        main()
    except BaseException:
        err = traceback.format_exc()
        try:
            _write_log(err)
        except BaseException:
            pass
        try:
            _show_error(err)
        except BaseException:
            # 连错误界面都起不来，交给 logcat
            print(err)


if __name__ == "__main__":
    _boot()
