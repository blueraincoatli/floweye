"""
护理者通知模块 - 将患者选择推送到护理者手机。

支持两种通道，可单独或同时使用：
- Server酱: 推送到微信（方糖服务号），适合国内用户
- Telegram: 推送到 Telegram Bot，适合国际用户（无需微信）

配置文件 notify_config.json:
{
    "channel": "serverchan",       // "serverchan" | "telegram" | "both"
    "serverchan": { "send_key": "SCT..." },
    "telegram": { "bot_token": "...", "chat_id": "..." }
}
兼容旧格式：顶层有 "send_key" 字段时自动视为 serverchan 通道。
"""

import json
import logging
import threading
import urllib.parse
import urllib.request
from abc import ABC, abstractmethod
from typing import List, Optional

logger = logging.getLogger(__name__)

SERVERCHAN_API = "https://sctapi.ftqq.com"
TELEGRAM_API = "https://api.telegram.org"


# ── 抽象接口 ─────────────────────────────────────────────

class BaseNotifier(ABC):
    """通知通道基类。enabled=False 时所有操作静默跳过。"""

    def __init__(self):
        self._enabled = False

    @property
    def enabled(self) -> bool:
        return self._enabled

    @abstractmethod
    def _send(self, title: str, body: str) -> bool: ...

    @abstractmethod
    def name(self) -> str: ...

    def notify(self, title: str, body: str) -> None:
        if not self._enabled:
            return
        threading.Thread(target=self._post, args=(title, body), daemon=True).start()

    def _post(self, title: str, body: str) -> None:
        try:
            ok = self._send(title, body)
            logger.info("%s notify: %s", self.name(), "OK" if ok else "FAIL")
        except Exception as e:
            logger.warning("%s notify failed: %s", self.name(), e)

    def send_emergency(self, label: str) -> None:
        self.notify(
            "患者紧急呼叫！",
            f"## 紧急呼叫\n\n患者请求：**{label}**\n\n请尽快前往查看。",
        )

    def send_important(self, label: str) -> None:
        self.notify(
            "患者请求帮助",
            f"### 患者需求\n\n**{label}**\n\n请及时处理。",
        )

    def send_normal(self, label: str) -> None:
        self.notify("患者消息", f"患者选择了「{label}」")

    def send_test(self) -> bool:
        if not self._enabled:
            return False
        try:
            return self._send("Floweye 连接测试", "通知功能正常工作")
        except Exception:
            return False


# ── Server酱 ─────────────────────────────────────────────

class ServerChanNotifier(BaseNotifier):
    """推送到微信（方糖服务号）。"""

    def __init__(self, send_key: Optional[str] = None):
        super().__init__()
        if send_key:
            self._send_key = send_key
            self._enabled = True

    def name(self) -> str:
        return "ServerChan"

    def _send(self, title: str, body: str) -> bool:
        url = f"{SERVERCHAN_API}/{self._send_key}.send"
        data = urllib.parse.urlencode({"title": title, "desp": body}).encode("utf-8")
        req = urllib.request.Request(url, data=data)
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status == 200


# ── Telegram ──────────────────────────────────────────────

class TelegramNotifier(BaseNotifier):
    """推送到 Telegram Bot。"""

    def __init__(self, bot_token: Optional[str] = None, chat_id: Optional[str] = None):
        super().__init__()
        if bot_token and chat_id:
            self._bot_token = bot_token
            self._chat_id = chat_id
            self._enabled = True

    def name(self) -> str:
        return "Telegram"

    def _send(self, title: str, body: str) -> bool:
        url = f"{TELEGRAM_API}/bot{self._bot_token}/sendMessage"
        text = f"*{title}*\n{body}"
        payload = json.dumps({
            "chat_id": self._chat_id,
            "text": text,
            "parse_mode": "Markdown",
        }, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(
            url, data=payload,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status == 200


# ── 复合调度器 ────────────────────────────────────────────

class CompositeNotifier:
    """将消息同时分发给多个通道。"""

    def __init__(self, channels: List[BaseNotifier]):
        self._channels = channels

    @property
    def enabled(self) -> bool:
        return any(c.enabled for c in self._channels)

    def send_emergency(self, label: str) -> None:
        for c in self._channels:
            c.send_emergency(label)

    def send_important(self, label: str) -> None:
        for c in self._channels:
            c.send_important(label)

    def send_normal(self, label: str) -> None:
        for c in self._channels:
            c.send_normal(label)

    def send_test(self) -> bool:
        results = []
        for c in self._channels:
            results.append(c.send_test())
        return any(results)


# ── 工厂函数 ──────────────────────────────────────────────

def create_notifier(notify_config_path: str = "notify_config.json") -> CompositeNotifier:
    """从配置文件创建通知调度器。"""
    try:
        with open(notify_config_path, "r", encoding="utf-8") as f:
            cfg = json.load(f)
    except FileNotFoundError:
        logger.info("notify_config.json not found, notifier disabled")
        return CompositeNotifier([])
    except Exception as e:
        logger.warning("Failed to load notify_config.json: %s", e)
        return CompositeNotifier([])

    channels: list = []

    # 兼容旧格式：顶层有 send_key 字段
    legacy_key = cfg.get("send_key", "").strip()
    if legacy_key:
        channels.append(ServerChanNotifier(legacy_key))
        logger.info("Notifier: ServerChan enabled (legacy config)")

    # 新格式：channel 字段驱动的显式配置
    channel_mode = cfg.get("channel", "").strip().lower()
    if channel_mode in ("serverchan", "both"):
        sc = cfg.get("serverchan", {})
        key = sc.get("send_key", "").strip()
        if key and not legacy_key:  # 避免重复添加
            channels.append(ServerChanNotifier(key))
            logger.info("Notifier: ServerChan enabled")

    if channel_mode in ("telegram", "both"):
        tg = cfg.get("telegram", {})
        token = tg.get("bot_token", "").strip()
        chat_id = tg.get("chat_id", "").strip()
        if token and chat_id:
            channels.append(TelegramNotifier(token, chat_id))
            logger.info("Notifier: Telegram enabled")

    if not channels:
        logger.info("Notifier: no channels configured, all disabled")

    return CompositeNotifier(channels)
