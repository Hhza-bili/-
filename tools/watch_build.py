#!/usr/bin/env python3
"""盯 GitHub Actions 构建，成功后把 APK 取到下载目录。"""
import io, json, os, time, urllib.request, urllib.error, zipfile

TOKEN = os.environ["GH_TOKEN"]
OWNER, REPO = "Hhza-bili", "-"
RUN = int(os.environ.get("RUN_ID", "44802941263"))
API = f"https://api.github.com/repos/{OWNER}/{REPO}"
OUT = "/sdcard/Download/DSH-Go"


def api(path, tries=5):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(API + path, headers={
                "Authorization": "Bearer " + TOKEN, "User-Agent": "whale",
                "Accept": "application/vnd.github+json"})
            return json.loads(urllib.request.urlopen(req, timeout=40).read())
        except Exception as e:
            last = e
            time.sleep(4 + i * 4)
    raise last


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def download(url):
    """artifact 下载会 302 到对象存储，认证头不能带过去。"""
    opener = urllib.request.build_opener(_NoRedirect)
    req = urllib.request.Request(url, headers={
        "Authorization": "Bearer " + TOKEN, "User-Agent": "whale"})
    try:
        return opener.open(req, timeout=90).read()
    except urllib.error.HTTPError as e:
        if e.code in (301, 302, 303, 307, 308):
            return urllib.request.urlopen(e.headers["Location"], timeout=300).read()
        raise


def main():
    deadline = time.time() + 3600
    status = conclusion = None
    while time.time() < deadline:
        try:
            r = api(f"/actions/runs/{RUN}")
            status, conclusion = r["status"], r["conclusion"]
            print(time.strftime("%H:%M:%S"), status, conclusion, flush=True)
        except Exception as e:
            print(time.strftime("%H:%M:%S"), "poll error:", e, flush=True)
            time.sleep(20)
            continue
        if status == "completed":
            break
        time.sleep(45)

    if status != "completed":
        print("TIMEOUT", flush=True)
        return
    if conclusion != "success":
        print("BUILD FAILED:", conclusion, flush=True)
        try:
            logs = download(f"{API}/actions/runs/{RUN}/logs")
            os.makedirs("/tmp/build_logs", exist_ok=True)
            with zipfile.ZipFile(io.BytesIO(logs)) as z:
                z.extractall("/tmp/build_logs")
            print("logs -> /tmp/build_logs", flush=True)
        except Exception as e:
            print("log download failed:", e, flush=True)
        return

    arts = api(f"/actions/runs/{RUN}/artifacts")
    os.makedirs(OUT, exist_ok=True)
    for a in arts.get("artifacts", []):
        print("artifact", a["name"], a["size_in_bytes"], flush=True)
        data = download(a["archive_download_url"])
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            for n in z.namelist():
                if n.endswith(".apk"):
                    target = os.path.join(OUT, "whale-pet.apk")
                    with open(target, "wb") as fh:
                        fh.write(z.read(n))
                    print("APK ->", target, os.path.getsize(target) // 1024, "KB", flush=True)


main()
