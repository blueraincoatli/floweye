#!/usr/bin/env python3
"""
Scanning Coordinator - Python version for gaze-controlled patient communication system.
"""

import argparse
import concurrent.futures
import json
import logging
import os
import queue
import sys
import threading
import time
from collections import deque
from datetime import datetime
from enum import Enum, auto
from typing import Any, Dict, List, Optional, Tuple

import paho.mqtt.client as mqtt


def _configure_stdio() -> None:
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        if stream is None or not hasattr(stream, "reconfigure"):
            continue
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


_configure_stdio()

try:
    import pyttsx3
    _PYTTSX3_AVAILABLE = True
except ImportError:
    _PYTTSX3_AVAILABLE = False
    pyttsx3 = None  # type: ignore

# Windows SAPI via PowerShell - more reliable than pyttsx3.runAndWait()
import subprocess
import sys

def _sapi_speak(text: str, rate: int = 150, volume: int = 100) -> bool:
    """Speak using Windows SAPI via PowerShell. Returns True on success."""
    if sys.platform != "win32":
        return False
    # Escape single quotes in text
    escaped = text.replace("'", "''")
    ps_cmd = (
        f"Add-Type -AssemblyName System.Speech; "
        f"$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
        f"$synth.Rate = {rate}; "
        f"$synth.Volume = {volume}; "
        f"$synth.Speak('{escaped}')"
    )
    try:
        result = subprocess.run(
            ["powershell", "-Command", ps_cmd],
            capture_output=True, timeout=30
        )
        return result.returncode == 0
    except Exception:
        return False

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("ScanningCoordinator")


class InteractionMode(Enum):
    SINGLE_SWITCH = auto()
    DUAL_SWITCH = auto()


class State(Enum):
    IDLE = auto()
    SCAN = auto()
    CONFIRM = auto()
    ALERT = auto()
    WAITING = auto()


class ConfigLoader:
    def __init__(self, menu_path: str, patient_path: str):
        self.menu = self._load_json(menu_path, self._default_menu)
        self.patient = self._load_json(patient_path, self._default_patient)

    @staticmethod
    def _load_json(path: str, fallback_fn) -> dict:
        if path and os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception as e:
                logger.warning("Config load failed %s: %s, using defaults", path, e)
        return fallback_fn()

    @staticmethod
    def _default_menu() -> dict:
        return {
            "version": "1.0",
            "default_dwell_seconds": 5,
            "options": [
                {
                    "id": "uncomfortable",
                    "label": "\u4e0d\u8212\u670d",
                    "tts_prompt": "\u60a8\u4e0d\u8212\u670d\u5417\uff1f",
                    "priority": 1,
                    "submenu": [
                        {"id": "headache", "label": "\u5934\u75bc", "tts_prompt": "\u60a8\u5934\u75bc\u5417\uff1f", "priority": 1},
                        {"id": "stomachache", "label": "\u809a\u5b50\u75bc", "tts_prompt": "\u60a8\u809a\u5b50\u75bc\u5417\uff1f", "priority": 2},
                        {"id": "back", "label": "\u8fd4\u56de", "tts_prompt": "\u8fd4\u56de\u4e0a\u4e00\u7ea7", "action": "back"},
                    ],
                },
                {
                    "id": "care",
                    "label": "\u8eab\u4f53\u62a4\u7406",
                    "tts_prompt": "\u9700\u8981\u8eab\u4f53\u62a4\u7406\u5417\uff1f",
                    "priority": 2,
                    "submenu": [
                        {"id": "turn_over", "label": "\u60f3\u7ffb\u8eab", "tts_prompt": "\u60a8\u60f3\u7ffb\u8eab\u5417\uff1f", "priority": 1},
                        {"id": "back", "label": "\u8fd4\u56de", "tts_prompt": "\u8fd4\u56de\u4e0a\u4e00\u7ea7", "action": "back"},
                    ],
                },
                {
                    "id": "emergency",
                    "label": "\u7d27\u6025",
                    "tts_prompt": "\u9700\u8981\u7d27\u6025\u5e2e\u52a9\u5417\uff1f",
                    "priority": 99,
                    "urgency": "critical",
                    "submenu": [
                        {"id": "confirm_emergency", "label": "\u786e\u8ba4\u7d27\u6025\u547c\u53eb", "tts_prompt": "\u786e\u8ba4\u7d27\u6025\u547c\u53eb\u5417\uff1f\u8fd9\u5c06\u4f1a\u7acb\u5373\u901a\u77e5\u62a4\u58eb\u3002", "priority": 1, "urgency": "critical", "skip_confirm": True},
                        {"id": "back", "label": "\u8fd4\u56de", "tts_prompt": "\u8fd4\u56de\u4e0a\u4e00\u7ea7", "action": "back"},
                    ],
                },
            ],
            "night_mode_options": ["uncomfortable", "care", "emergency"],
        }

    @staticmethod
    def _default_patient() -> dict:
        return {
            "patient_id": "default",
            "name": "\u9ed8\u8ba4\u60a3\u8005",
            "max_menu_depth": 2,
            "cognitive_level": "low",
            "vision_ok": False,
            "preferred_mode": "auto",
            "single_device": {
                "wake_gaze_seconds": 3.0,
                "select_gaze_seconds": 1.5,
                "emergency_gaze_seconds": 3.0,
                "dwell_seconds": 5,
                "max_dwell_seconds": 7,
                "min_dwell_seconds": 3,
            },
            "dual_device": {
                "select_gaze_seconds": 1.5,
                "emergency_triple_window_seconds": 5.0,
                "dwell_seconds": 5,
                "max_dwell_seconds": 7,
                "min_dwell_seconds": 3,
            },
            "tts": {
                "rate": 120,
                "volume": 0.8,
                "night_volume": 0.4,
                "night_start_hour": 22,
                "night_end_hour": 6,
            },
            "adaptive": {
                "enabled": True,
                "history_window_size": 5,
                "dwell_adjust_step": 0.5,
            },
        }

    def get_param(self, path: str, default: Any = None) -> Any:
        parts = path.split(".")
        current = self.patient
        for part in parts:
            if isinstance(current, dict) and part in current:
                current = current[part]
            else:
                return default
        return current


class MenuEngine:
    def __init__(self, menu_config: dict):
        self._menu = menu_config
        self._path: List[int] = []
        self._current_index = 0
        self._history: deque = deque(maxlen=50)

    def _get_current_list(self) -> List[dict]:
        if not self._path:
            return self._menu.get("options", [])
        node = self._menu.get("options", [])
        for idx in self._path:
            node = node[idx].get("submenu", [])
        return node

    @staticmethod
    def _is_night_mode() -> bool:
        hour = datetime.now().hour
        return hour >= 22 or hour < 6

    def get_current_options(self) -> List[dict]:
        options = list(self._get_current_list())
        if self._is_night_mode():
            night_ids = set(self._menu.get("night_mode_options", []))
            options.sort(key=lambda o: (0 if o.get("id") in night_ids else 1, o.get("priority", 99)))
        else:
            options.sort(key=lambda o: o.get("priority", 99))
        return options

    def get_current_option(self) -> Optional[dict]:
        options = self.get_current_options()
        if not options:
            return None
        if self._current_index >= len(options):
            self._current_index = 0
        return options[self._current_index]

    def next_option(self) -> Optional[dict]:
        options = self.get_current_options()
        if not options:
            return None
        self._current_index = (self._current_index + 1) % len(options)
        return self.get_current_option()

    def select_current(self) -> Tuple[Optional[dict], bool]:
        option = self.get_current_option()
        if option is None:
            return None, False
        action = option.get("action")
        if action == "back":
            self.go_back()
            return option, False
        submenu = option.get("submenu")
        if submenu:
            options = self.get_current_options()
            try:
                idx = options.index(option)
            except ValueError:
                idx = 0
            self._path.append(idx)
            self._current_index = 0
            return option, False
        return option, True

    def go_back(self) -> None:
        if self._path:
            self._path.pop()
        self._current_index = 0

    def reset(self) -> None:
        self._path = []
        self._current_index = 0

    def record_selection(self, option_id: str) -> None:
        self._history.append({"option_id": option_id, "timestamp": int(time.time() * 1000)})

    @property
    def is_at_root(self) -> bool:
        return not self._path


class TTSEngine:
    """TTS engine using pyttsx3. All speak calls run in the caller's thread
    to avoid runAndWait() deadlock issues on Windows SAPI5."""

    def __init__(self, config_loader: ConfigLoader):
        self._config = config_loader
        self._engine: Any = None
        self._lock = threading.Lock()
        self._stopped = threading.Event()
        if _PYTTSX3_AVAILABLE and pyttsx3 is not None:
            try:
                self._engine = pyttsx3.init()
                self._engine.setProperty("rate", self._config.get_param("tts.rate", 150))
                self._engine.setProperty("volume", self._config.get_param("tts.volume", 1.0))
                voices = self._engine.getProperty("voices")
                logger.info("[TTS] Available voices: %s", [v.id for v in voices])
                for v in voices:
                    if "CN" in v.id or "ZH" in v.id or "Chinese" in v.name:
                        self._engine.setProperty("voice", v.id)
                        logger.info("[TTS] Selected Chinese voice: %s", v.id)
                        break
                else:
                    if voices:
                        logger.warning("[TTS] No Chinese voice found, using default: %s", voices[0].id)
            except Exception as e:
                logger.warning("TTS init failed: %s", e)
                self._engine = None
        else:
            logger.warning("pyttsx3 not installed, TTS unavailable")

    @staticmethod
    def _is_night_mode() -> bool:
        hour = datetime.now().hour
        return hour >= 22 or hour < 6

    def _get_volume(self) -> float:
        if self._is_night_mode():
            return self._config.get_param("tts.night_volume", 0.4)
        return self._config.get_param("tts.volume", 0.8)

    def speak(self, text: str) -> None:
        """Speak text synchronously. Must be called from a background thread."""
        if self._stopped.is_set():
            return
        rate = self._config.get_param("tts.rate", 150)
        # pyttsx3 rate range is -10 to 10, convert from words/min
        sapi_rate = max(-10, min(10, (rate - 150) // 15))
        volume = int(self._get_volume() * 100)
        logger.info("[TTS] Speaking: %s", text)
        ok = _sapi_speak(text, sapi_rate, volume)
        if ok:
            logger.info("[TTS] Done: %s", text)
        else:
            logger.warning("[TTS] PowerShell SAPI failed, falling back to pyttsx3")
            # Fallback to pyttsx3
            if self._engine is not None:
                with self._lock:
                    try:
                        self._engine.say(text)
                        self._engine.runAndWait()
                    except Exception:
                        try:
                            self._engine.stop()
                        except Exception:
                            pass

    @property
    def is_available(self) -> bool:
        return self._engine is not None and not self._stopped.is_set()

    def stop(self) -> None:
        self._stopped.set()
        if self._engine is not None:
            with self._lock:
                try:
                    self._engine.stop()
                except Exception as e:
                    logger.warning("TTS stop failed: %s", e)


class GazeInterpreter:
    def __init__(self, config_loader: ConfigLoader):
        self._config = config_loader
        self._device_gaze_start: Dict[str, float] = {}
        self._device_action_latched: Dict[str, bool] = {}
        self._device_hesitate_latched: Dict[str, bool] = {}
        self._yes_history: deque = deque(maxlen=10)
        self._lock = threading.Lock()

    def _get_gaze_duration(self, device_id: str, is_looking: bool) -> float:
        now = time.time()
        if not is_looking:
            self._device_gaze_start.pop(device_id, None)
            self._device_action_latched.pop(device_id, None)
            self._device_hesitate_latched.pop(device_id, None)
            return 0.0
        start = self._device_gaze_start.setdefault(device_id, now)
        return now - start

    def _emit_once(self, device_id: str, action: str, param: Any = None) -> Tuple[Optional[str], Any]:
        if self._device_action_latched.get(device_id, False):
            return None, None
        self._device_action_latched[device_id] = True
        return action, param

    def _record_yes(self) -> None:
        self._yes_history.append(time.time())

    def _check_triple_yes(self, window: float) -> bool:
        if len(self._yes_history) < 3:
            return False
        recent = list(self._yes_history)[-3:]
        return (recent[-1] - recent[0]) <= window

    def process_gaze_event(
        self,
        device_id: str,
        role: str,
        is_looking: bool,
        confidence: float,
        mode: InteractionMode,
        current_state: State,
        current_option: Optional[dict],
    ) -> Tuple[Optional[str], Any]:
        with self._lock:
            duration = self._get_gaze_duration(device_id, is_looking)
            if not is_looking:
                return None, None

            if mode == InteractionMode.SINGLE_SWITCH:
                wake_sec = self._config.get_param("single_device.wake_gaze_seconds", 3.0)
                select_sec = self._config.get_param("single_device.select_gaze_seconds", 1.5)
                emergency_sec = self._config.get_param("single_device.emergency_gaze_seconds", 3.0)

                if current_state == State.IDLE and duration >= wake_sec:
                    return self._emit_once(device_id, "wake")
                if current_state == State.SCAN:
                    if duration >= emergency_sec:
                        return self._emit_once(device_id, "emergency")
                    if duration >= select_sec and current_option is not None:
                        return self._emit_once(device_id, "select", current_option)
                if current_state == State.CONFIRM and duration >= select_sec:
                    return self._emit_once(device_id, "confirm")

            elif mode == InteractionMode.DUAL_SWITCH:
                select_sec = self._config.get_param("dual_device.select_gaze_seconds", 1.5)
                skip_sec = self._config.get_param("dual_device.skip_gaze_seconds", 0.5)
                hesitation_sec = self._config.get_param("dual_device.hesitation_seconds", 0.3)
                triple_window = self._config.get_param("dual_device.emergency_triple_window_seconds", 5.0)

                if role == "yes" and duration >= select_sec:
                    if self._device_action_latched.get(device_id, False):
                        return None, None
                    self._record_yes()
                    if self._check_triple_yes(triple_window):
                        return self._emit_once(device_id, "emergency")
                    if current_state == State.IDLE:
                        return self._emit_once(device_id, "wake")
                    if current_state == State.SCAN and current_option is not None:
                        return self._emit_once(device_id, "select", current_option)
                    if current_state == State.CONFIRM:
                        return self._emit_once(device_id, "confirm")

                if role == "yes" and current_state == State.SCAN and hesitation_sec <= duration < select_sec:
                    if not self._device_hesitate_latched.get(device_id, False):
                        self._device_hesitate_latched[device_id] = True
                        return "hesitate", None

                if role == "no" and duration >= skip_sec:
                    if current_state == State.SCAN:
                        return self._emit_once(device_id, "skip")
                    if current_state == State.CONFIRM:
                        return self._emit_once(device_id, "cancel")

            return None, None

    def reset(self) -> None:
        with self._lock:
            self._device_gaze_start.clear()
            self._device_action_latched.clear()
            self._yes_history.clear()

    def clear_latch(self, device_id: str) -> None:
        """清除指定设备的 action latch，不重置注视时长"""
        with self._lock:
            self._device_action_latched.pop(device_id, None)
            self._device_hesitate_latched.pop(device_id, None)

    def reset_gaze_start(self) -> None:
        """重置所有设备的注视计时起点（选项切换时使用）"""
        with self._lock:
            self._device_gaze_start.clear()


class DeviceManager:
    def __init__(self):
        self._devices: Dict[str, dict] = {}
        self._lock = threading.Lock()
        self.valid_window = 30
        self.offline_timeout = 60

    def update_gaze(self, device_id: str, data: dict) -> None:
        with self._lock:
            if device_id not in self._devices:
                self._devices[device_id] = {}
            self._devices[device_id].update({
                "role": data.get("role", "unknown"),
                "gazeStatus": data.get("lookingAtScreen", False),
                "lastUpdate": time.time(),
            })

    def update_status(self, device_id: str, status: str) -> None:
        with self._lock:
            if device_id not in self._devices:
                self._devices[device_id] = {}
            self._devices[device_id]["deviceStatus"] = status
            self._devices[device_id]["lastSeen"] = time.time()

    def get_active_devices(self) -> Dict[str, dict]:
        now = time.time()
        with self._lock:
            return {
                did: d.copy()
                for did, d in self._devices.items()
                if now - d.get("lastUpdate", 0) < self.valid_window
            }

    def get_online_count(self) -> int:
        return len(self.get_active_devices())

    def determine_mode(self) -> InteractionMode:
        return InteractionMode.DUAL_SWITCH if self.get_online_count() >= 2 else InteractionMode.SINGLE_SWITCH

    def cleanup_offline(self) -> List[str]:
        now = time.time()
        removed = []
        with self._lock:
            offline = [
                did for did, d in self._devices.items()
                if now - d.get("lastUpdate", 0) > self.offline_timeout
            ]
            for did in offline:
                removed.append(did)
                del self._devices[did]
        return removed


class ScanningCoordinator:
    def __init__(
        self,
        broker_host: str = "192.168.1.100",
        broker_port: int = 1883,
        menu_path: str = "menu_config.json",
        patient_path: str = "patient_config.json",
    ):
        self.broker_host = broker_host
        self.broker_port = broker_port
        self.mqtt_client: Optional[mqtt.Client] = None
        self.running = False

        self.config = ConfigLoader(menu_path, patient_path)
        self.menu_engine = MenuEngine(self.config.menu)
        self.tts = TTSEngine(self.config)
        self.gaze = GazeInterpreter(self.config)
        self.device_mgr = DeviceManager()

        self._state = State.IDLE
        self._state_lock = threading.Lock()
        self._confirm_option: Optional[dict] = None

        self._scan_thread: Optional[threading.Thread] = None
        self._scan_event = threading.Event()

        self._last_activity = time.time()
        self._dwell_start = time.time()
        self._round_count = 0
        self._option_started = time.time()

        self._response_times: deque = deque(maxlen=5)
        self._current_dwell = float(self.config.get_param("single_device.dwell_seconds", 5.0))

        # Scan phase: "announce" = TTS播报中(只显示选项文字), "select" = 等待用户选择(显示是/否)
        self._scan_phase = "select"
        self._announce_start = 0.0
        self._announce_duration = 0.0
        self._current_announce_option: Optional[dict] = None

        self.topics = {
            "gaze_status": "gazecontrol/device/+/gaze_status",
            "device_status": "gazecontrol/device/+/status",
            "coordination": "gazecontrol/coordination/decision",
        }

    def _get_dwell_key(self) -> str:
        mode = self.device_mgr.determine_mode()
        return "single_device" if mode == InteractionMode.SINGLE_SWITCH else "dual_device"

    def _get_current_dwell(self) -> float:
        return self._current_dwell

    def _update_dwell(self, response_time: float) -> None:
        if not self.config.get_param("adaptive.enabled", True):
            return
        self._response_times.append(response_time)
        if len(self._response_times) < 2:
            return
        avg = sum(self._response_times) / len(self._response_times)
        step = self.config.get_param("adaptive.dwell_adjust_step", 0.5)
        prefix = self._get_dwell_key()
        min_d = float(self.config.get_param(f"{prefix}.min_dwell_seconds", 3.0))
        max_d = float(self.config.get_param(f"{prefix}.max_dwell_seconds", 7.0))
        if avg < self._current_dwell:
            self._current_dwell = max(min_d, self._current_dwell - step)
        else:
            self._current_dwell = min(max_d, self._current_dwell + step)
        logger.info("[ADAPTIVE] avg_response=%.2fs, new_dwell=%.2fs", avg, self._current_dwell)

    @property
    def state(self) -> State:
        with self._state_lock:
            return self._state

    @state.setter
    def state(self, value: State) -> None:
        with self._state_lock:
            old = self._state
            self._state = value
            if old != value:
                logger.info("[STATE] %s -> %s", old.name, value.name)

    def connect_mqtt(self) -> None:
        try:
            self.mqtt_client = mqtt.Client(client_id="ScanningCoordinator", clean_session=True)
            self.mqtt_client.on_connect = self._on_connect
            self.mqtt_client.on_disconnect = self._on_disconnect
            self.mqtt_client.on_message = self._on_message
            self.mqtt_client.reconnect_delay_set(min_delay=3, max_delay=15)
            logger.info("Connecting to MQTT Broker: %s:%d", self.broker_host, self.broker_port)
            self.mqtt_client.connect(self.broker_host, self.broker_port, 60)
            self.mqtt_client.loop_start()
        except Exception as e:
            logger.error("MQTT connection failed: %s", e)

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            logger.info("[OK] MQTT connected")
            for name, topic in self.topics.items():
                if name != "coordination":
                    client.subscribe(topic)
                    logger.info("[INFO] Subscribed: %s", topic)
        else:
            logger.error("[ERROR] MQTT connection failed, rc=%d", rc)

    def _on_disconnect(self, client, userdata, rc):
        if rc == 7:
            logger.info("[INFO] MQTT session taken over by newer connection, stopping reconnect")
            client.loop_stop()
        elif rc != 0:
            logger.warning("[WARN] MQTT unexpected disconnect (rc=%d), auto-reconnect enabled", rc)
        else:
            logger.info("[INFO] MQTT disconnected")

    def _on_message(self, client, userdata, msg):
        try:
            payload_str = msg.payload.decode()
        except Exception as e:
            logger.warning("Message decode failed (topic=%s): %s", msg.topic, e)
            return
        try:
            payload = json.loads(payload_str)
        except json.JSONDecodeError as e:
            logger.warning("JSON parse failed (topic=%s): %s", msg.topic, e)
            return
        try:
            parts = msg.topic.split("/")
            if len(parts) == 4 and parts[0] == "gazecontrol" and parts[1] == "device" and parts[3] == "gaze_status":
                self._handle_gaze_status(payload)
            elif len(parts) == 4 and parts[0] == "gazecontrol" and parts[1] == "device" and parts[3] == "status":
                self._handle_device_status(payload)
        except Exception as e:
            logger.error("Message handler error (topic=%s): %s", msg.topic, e)

    def _handle_gaze_status(self, data: dict) -> None:
        device_id = data.get("deviceId", "unknown")
        is_looking = data.get("lookingAtScreen", False)
        confidence = data.get("confidence", 0.0)
        role = data.get("role", "unknown")

        self.device_mgr.update_gaze(device_id, data)
        self._last_activity = time.time()

        mode = self.device_mgr.determine_mode()
        option = self.menu_engine.get_current_option()
        action, param = self.gaze.process_gaze_event(
            device_id=device_id,
            role=role,
            is_looking=is_looking,
            confidence=confidence,
            mode=mode,
            current_state=self.state,
            current_option=option,
        )
        if is_looking or action:
            logger.info("GAZE %s role=%s looking=%s conf=%.2f state=%s scan_phase=%s action=%s",
                         device_id[:12], role, is_looking, confidence,
                         self.state.name if hasattr(self.state, 'name') else self.state,
                         self._scan_phase,
                         action)
        # 播报阶段不处理任何注视动作，避免误触发
        # 同时清除 announce 期间误设的 latch，防止阻断后续 select
        if action and self._scan_phase == "announce":
            self.gaze.clear_latch(device_id)
        elif action:
            self._handle_action(action, param)

    def _handle_device_status(self, data: dict) -> None:
        device_id = data.get("deviceId", "unknown")
        status = data.get("status", "unknown")
        self.device_mgr.update_status(device_id, status)
        logger.info("[INFO] Device %s: %s", device_id[:12], status)
        # 设备重连时，同步当前协调器状态到该设备
        if status == "online":
            self._sync_state_to_device()

    def _sync_state_to_device(self) -> None:
        """设备重连时，将当前协调器状态同步到所有设备"""
        try:
            if self.state == State.IDLE:
                self._publish_idle()
            elif self.state == State.SCAN:
                option = self.menu_engine.get_current_option()
                if option:
                    if self._scan_phase == "announce":
                        self._publish_announce(option)
                    else:
                        self._publish_scan_progress(option)
            elif self.state == State.CONFIRM and self._confirm_option:
                self.publish_decision("confirm", self._confirm_option)
        except Exception as e:
            logger.warning("[SYNC] State sync failed: %s", e)

    def publish_decision(self, decision_type: str, option: Optional[dict], urgency: str = "normal") -> None:
        if self.mqtt_client is None or not self.mqtt_client.is_connected():
            return
        active_count = self.device_mgr.get_online_count()
        payload = {
            "type": decision_type,
            "timestamp": int(time.time() * 1000),
            "activeDevices": active_count,
            "urgency": urgency,
            "menuDepth": len(self.menu_engine._path),
        }
        if option:
            payload["optionId"] = option.get("id")
            payload["optionLabel"] = option.get("label")
            payload["ttsPrompt"] = option.get("tts_prompt")
        try:
            self.mqtt_client.publish(self.topics["coordination"], json.dumps(payload, ensure_ascii=False), qos=1)
        except Exception as e:
            logger.error("Publish decision failed: %s", e)

    def _publish_action_feedback(self, action_type: str) -> None:
        """发布操作反馈消息，触发手机端音效和视觉反馈"""
        if self.mqtt_client is None or not self.mqtt_client.is_connected():
            return
        payload = {
            "type": "action_feedback",
            "action": action_type,
            "timestamp": int(time.time() * 1000),
        }
        try:
            self.mqtt_client.publish(self.topics["coordination"], json.dumps(payload, ensure_ascii=False), qos=1)
        except Exception as e:
            logger.error("Publish action feedback failed: %s", e)

    # --- State Machine ---

    def _handle_action(self, action: str, param: Any) -> None:
        logger.info("[ACTION] %s param=%s", action, param)
        self._last_activity = time.time()

        if action == "wake":
            if self.state == State.IDLE:
                self._enter_scan()
            return

        if action == "emergency":
            self._enter_alert()
            return

        if action == "hesitate":
            if self.state == State.SCAN:
                self._dwell_start = time.time()
            return

        if action == "select":
            option = param
            if option is None:
                return
            # Navigate menu: if leaf, confirm/execute; if submenu, enter it directly
            selected, is_leaf = self.menu_engine.select_current()
            if selected is None:
                return
            self._publish_action_feedback("select")
            if not is_leaf:
                # Entered submenu, announce first option
                self.gaze.reset()  # 清除 latch，允许子菜单中的新选择
                self._option_started = time.time()
                self._dwell_start = time.time()
                self._round_count = 0
                first = self.menu_engine.get_current_option()
                if first:
                    self._start_announce(first)
                return
            if option.get("skip_confirm"):
                self._execute_option(option)
            else:
                self._enter_confirm(option)
            return

        if action == "confirm":
            self._publish_action_feedback("confirm")
            if self._confirm_option:
                self._execute_option(self._confirm_option)
            return

        if action == "skip":
            if self.state == State.SCAN:
                self._publish_action_feedback("skip")
                self._skip_current()
            return

        if action == "cancel":
            if self.state == State.CONFIRM:
                self._publish_action_feedback("cancel")
                self._cancel_confirm()
            return

    def _enter_alert(self) -> None:
        self.state = State.ALERT
        self.publish_decision("emergency", None, urgency="critical")
        threading.Thread(target=lambda: self.tts.speak("紧急呼叫"), daemon=True).start()
        # After alert, could stay in ALERT or return to IDLE after a delay
        # For simplicity, return to IDLE after TTS finishes
        self._reset_to_idle()

    def _publish_scan_progress(self, option: Optional[dict]) -> None:
        """发布当前扫描的选项到手机端显示"""
        if self.mqtt_client is None or not self.mqtt_client.is_connected():
            return
        payload = {
            "type": "scan_progress",
            "timestamp": int(time.time() * 1000),
            "activeDevices": self.device_mgr.get_online_count(),
            "menuDepth": len(self.menu_engine._path),
        }
        if option:
            payload["optionId"] = option.get("id")
            payload["optionLabel"] = option.get("label")
            payload["ttsPrompt"] = option.get("tts_prompt", "")
        try:
            self.mqtt_client.publish(self.topics["coordination"], json.dumps(payload, ensure_ascii=False), qos=1)
        except Exception as e:
            logger.error("Publish scan progress failed: %s", e)

    def _publish_idle(self) -> None:
        """发布空闲状态，通知手机回到待机界面"""
        if self.mqtt_client is None or not self.mqtt_client.is_connected():
            return
        payload = {
            "type": "idle",
            "timestamp": int(time.time() * 1000),
            "activeDevices": self.device_mgr.get_online_count(),
            "menuDepth": 0,
        }
        try:
            self.mqtt_client.publish(self.topics["coordination"], json.dumps(payload, ensure_ascii=False), qos=1)
        except Exception as e:
            logger.error("Publish idle failed: %s", e)

    def _publish_executed(self, option: dict) -> None:
        """发布执行结果，手机显示反馈"""
        if self.mqtt_client is None or not self.mqtt_client.is_connected():
            return
        payload = {
            "type": "executed",
            "timestamp": int(time.time() * 1000),
            "activeDevices": self.device_mgr.get_online_count(),
            "menuDepth": len(self.menu_engine._path),
            "optionId": option.get("id"),
            "optionLabel": option.get("label"),
        }
        try:
            self.mqtt_client.publish(self.topics["coordination"], json.dumps(payload, ensure_ascii=False), qos=1)
        except Exception as e:
            logger.error("Publish executed failed: %s", e)

    def _publish_transition(self) -> None:
        """\u53d1\u5e03\u8fc7\u6e21\u72b6\u6001\uff0c\u624b\u673a\u6e05\u5c4f"""
        if self.mqtt_client is None or not self.mqtt_client.is_connected():
            return
        payload = {
            "type": "transition",
            "timestamp": int(time.time() * 1000),
            "activeDevices": self.device_mgr.get_online_count(),
            "menuDepth": len(self.menu_engine._path),
        }
        try:
            self.mqtt_client.publish(
                self.topics["coordination"],
                json.dumps(payload, ensure_ascii=False), qos=1)
        except Exception as e:
            logger.error("Publish transition failed: %s", e)

    def _enter_scan(self) -> None:
        self.state = State.SCAN
        self.menu_engine.reset()
        self.gaze.reset()
        self._round_count = 0
        self._dwell_start = time.time()
        self._option_started = time.time()
        self._scan_phase = "announce"  # 进入播报阶段，阻止 gaze 事件
        self._scan_generation = getattr(self, '_scan_generation', 0) + 1
        # 过渡：清空屏幕
        self._publish_transition()
        # 在后台线程中播报过渡语 + 第一个选项
        threading.Thread(target=self._scan_intro, daemon=True).start()

    def _scan_intro(self) -> None:
        """后台线程：播报过渡语，然后播报第一个选项"""
        # 用一个递增的 generation 标记防止旧线程干扰新状态
        gen = self._scan_generation
        self.tts.speak("即将播报选项")
        # 播报完后如果已经不在 SCAN 或 generation 已变，说明被中断了
        if self.state != State.SCAN or gen != self._scan_generation:
            logger.info("[SCAN_INTRO] Aborted after intro TTS (state=%s, gen_changed=%s)",
                        self.state.name if hasattr(self.state, 'name') else self.state,
                        gen != self._scan_generation)
            return
        option = self.menu_engine.get_current_option()
        if option:
            self._announce_and_select(option)
        else:
            self._publish_scan_progress(None)

    def _enter_confirm(self, option: dict) -> None:
        self.state = State.CONFIRM
        self._confirm_option = option
        self._dwell_start = time.time()
        self.gaze.reset()  # 清除 latch，允许 CONFIRM 阶段的新选择
        self.publish_decision("confirm", option)
        # 阻塞式 TTS：用户需要听完确认提示才能做决定
        self.tts.speak("确认" + option.get("tts_prompt", option.get("label", "")))

    def _execute_selection(self) -> None:
        if self._confirm_option:
            self._execute_option(self._confirm_option)

    def _execute_option(self, option: dict) -> None:
        self.menu_engine.record_selection(option.get("id", "unknown"))
        urgency = option.get("urgency", "normal")
        self.publish_decision("selection", option, urgency=urgency)
        self._publish_executed(option)
        self.state = State.WAITING
        self._dwell_start = time.time()
        # Record response time for adaptive dwell
        response_time = time.time() - self._option_started
        self._update_dwell(response_time)
        label = option.get("label", "")
        threading.Thread(target=lambda: self.tts.speak(f"已通知{label}"), daemon=True).start()

    def _skip_current(self) -> None:
        logger.info("[SKIP] Skipped current option by 'no' device gaze")
        self.publish_decision("skip_feedback", self.menu_engine.get_current_option())
        option = self.menu_engine.next_option()
        self._option_started = time.time()
        self._dwell_start = time.time()
        if option:
            self._start_announce(option)
        else:
            self._publish_scan_progress(None)

    def _cancel_confirm(self) -> None:
        self._confirm_option = None
        self.state = State.SCAN
        self._dwell_start = time.time()
        self.gaze.reset()
        option = self.menu_engine.get_current_option()
        if option:
            self._start_announce(option)
        else:
            self._publish_scan_progress(None)

    def _reset_to_idle(self) -> None:
        old_state = self.state
        self.state = State.IDLE
        self.menu_engine.reset()
        self.gaze.reset()
        self._confirm_option = None
        self._round_count = 0
        self._scan_phase = "select"
        self._current_announce_option = None
        self._publish_idle()
        logger.info("[RESET] Returned to IDLE from %s", old_state.name if hasattr(old_state, 'name') else old_state)

    def _start_announce(self, option: dict) -> None:
        """在后台线程中播报选项：发 announce -> TTS 播报 -> 发 scan_progress"""
        self._scan_phase = "announce"  # 立即标记为播报阶段，阻止 gaze 事件
        threading.Thread(target=self._announce_and_select, args=(option,), daemon=True).start()

    def _announce_and_select(self, option: dict) -> None:
        """同步播报：announce(只显示文字) -> TTS 播报 -> scan_progress(显示是/否按钮)"""
        if self.state != State.SCAN:
            return

        gen = self._scan_generation
        self._scan_phase = "announce"
        self._current_announce_option = option
        self._announce_start = time.time()
        self._publish_announce(option)

        # TTS 播报
        text = option.get("tts_prompt", option.get("label", ""))
        self.tts.speak(text)

        # 播报完毕，检查是否被中断（状态变了或 generation 变了）
        if self.state != State.SCAN or gen != self._scan_generation:
            logger.info("[ANNOUNCE] Aborted after TTS for '%s'", option.get("label", ""))
            return

        if self._scan_phase == "announce":
            self._scan_phase = "select"
            self.gaze.reset_gaze_start()  # 重置注视计时，确保需要重新注视1.5秒
            now = time.time()
            self._dwell_start = now
            self._option_started = now
            self._publish_scan_progress(option)
            logger.info("[SELECT] %s", option.get("label", ""))

    def _publish_announce(self, option: dict) -> None:
        """发布播报阶段消息：手机端只显示选项文字，不显示是/否按钮"""
        if self.mqtt_client is None or not self.mqtt_client.is_connected():
            return
        payload = {
            "type": "announce",
            "timestamp": int(time.time() * 1000),
            "activeDevices": self.device_mgr.get_online_count(),
            "menuDepth": len(self.menu_engine._path),
            "optionId": option.get("id"),
            "optionLabel": option.get("label"),
            "ttsPrompt": option.get("tts_prompt", ""),
        }
        try:
            self.mqtt_client.publish(self.topics["coordination"], json.dumps(payload, ensure_ascii=False), qos=1)
        except Exception as e:
            logger.error("Publish announce failed: %s", e)

    # --- Scan Loop ---

    def _scan_loop(self) -> None:
        while self.running:
            time.sleep(0.1)
            if not self.running:
                break

            state = self.state
            now = time.time()

            if state == State.SCAN:
                # ANNOUNCE 阶段由 _announce_and_switch 后台线程处理
                # 这里只处理 SELECT 阶段的 dwell 计时
                if self._scan_phase == "announce":
                    continue

                # SELECT 阶段：dwell 计时，等待用户选择
                dwell = self._get_current_dwell()
                if now - self._dwell_start >= dwell:
                    # Dwell expired, advance
                    prev_index = self.menu_engine._current_index
                    option = self.menu_engine.next_option()
                    self._dwell_start = now
                    self._option_started = now

                    if option is None:
                        self._reset_to_idle()
                        continue

                    # Check if we wrapped around (index went from end back to 0)
                    wrapped = (
                        (prev_index != 0 and self.menu_engine._current_index == 0)
                        or (prev_index == 0 and self.menu_engine._current_index == 0 and len(self.menu_engine.get_current_options()) == 1)
                    )
                    if wrapped:
                        self._round_count += 1
                        if self._round_count >= 1:
                            # Full round with no selection -> idle
                            self._reset_to_idle()
                            continue

                    self._start_announce(option)

            elif state == State.CONFIRM:
                if now - self._dwell_start >= self._get_confirm_timeout():
                    self._cancel_confirm()

            elif state == State.WAITING:
                if now - self._dwell_start >= self._get_wait_timeout():
                    self._reset_to_idle()

            elif state == State.IDLE:
                if now - self._last_activity >= self._get_auto_idle_timeout():
                    # Stay idle, optionally auto-start scan after longer interval
                    pass

    def _get_confirm_timeout(self) -> float:
        return 10.0

    def _get_wait_timeout(self) -> float:
        return 10.0

    def _get_auto_idle_timeout(self) -> float:
        return 60.0

    def _get_idle_repeat_interval(self) -> float:
        return 120.0

    # --- Main Loop ---

    def run(self) -> None:
        logger.info("=" * 60)
        logger.info("Scanning Coordinator Starting...")
        logger.info("Broker: %s:%d", self.broker_host, self.broker_port)
        logger.info("=" * 60)

        self.running = True
        self.connect_mqtt()

        self._scan_thread = threading.Thread(target=self._scan_loop, daemon=True)
        self._scan_thread.start()

        try:
            while self.running:
                time.sleep(10)
                offline = self.device_mgr.cleanup_offline()
                for did in offline:
                    logger.info("[OFFLINE] Device removed: %s", did)
        except KeyboardInterrupt:
            logger.info("[STOP] KeyboardInterrupt received, stopping...")
        finally:
            self.stop()

    def stop(self) -> None:
        self.running = False
        self._scan_event.set()
        self.tts.stop()
        if self.mqtt_client:
            self.mqtt_client.loop_stop()
            self.mqtt_client.disconnect()
        logger.info("[STOP] Coordinator stopped")


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------


def main():
    parser = argparse.ArgumentParser(description="Scanning Coordinator for gaze-controlled patient communication")
    parser.add_argument("--host", default="192.168.1.100", help="MQTT Broker host (default: 192.168.1.100)")
    parser.add_argument("--port", type=int, default=1883, help="MQTT Broker port (default: 1883)")
    parser.add_argument("--menu", default="coordinator_app/menu_config.json", help="Menu config path")
    parser.add_argument("--patient", default="coordinator_app/patient_config.json", help="Patient config path")
    args = parser.parse_args()

    coordinator = ScanningCoordinator(
        broker_host=args.host,
        broker_port=args.port,
        menu_path=args.menu,
        patient_path=args.patient,
    )
    coordinator.run()


if __name__ == "__main__":
    main()
