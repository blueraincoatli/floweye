#!/usr/bin/env python3
"""
MQTT simulator for FlowEye scanning_coordinator.py manual verification.
"""

import argparse
import json
import os
import threading
import time
from typing import Dict, List, Optional

import paho.mqtt.client as mqtt


class CoordinatorFlowSimulator:
    def __init__(
        self,
        host: str,
        port: int,
        step_delay: float,
        hold_seconds: float,
        tick_seconds: float,
        action_pause: float,
        device_prefix: str,
    ):
        self.host = host
        self.port = port
        self.step_delay = step_delay
        self.hold_seconds = hold_seconds
        self.tick_seconds = tick_seconds
        self.action_pause = action_pause
        self.device_prefix = device_prefix
        self.client = mqtt.Client(client_id="FlowEyeSimulator")
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self._connected = threading.Event()
        self.decisions: List[Dict[str, object]] = []

    def device_id(self, suffix: str) -> str:
        return f"{self.device_prefix}-{suffix}"

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            client.subscribe("gazecontrol/coordination/decision")
            self._connected.set()
            print(f"[sim] connected to MQTT broker {self.host}:{self.port}")
        else:
            print(f"[sim] MQTT connect failed: rc={rc}")

    def _on_message(self, client, userdata, msg):
        payload = json.loads(msg.payload.decode("utf-8"))
        self.decisions.append(payload)
        print(f"[decision] {json.dumps(payload, ensure_ascii=False)}")

    def connect(self) -> None:
        self.client.connect(self.host, self.port, 60)
        self.client.loop_start()
        if not self._connected.wait(timeout=5):
            raise RuntimeError("MQTT connect timeout")

    def close(self) -> None:
        self.client.loop_stop()
        self.client.disconnect()

    def publish_status(self, device_id: str, status: str = "online") -> None:
        topic = f"gazecontrol/device/{device_id}/status"
        payload = {
            "deviceId": device_id,
            "status": status,
            "timestamp": int(time.time() * 1000),
        }
        self.client.publish(topic, json.dumps(payload, ensure_ascii=False), qos=1)
        print(f"[status] {device_id}: {status}")
        time.sleep(self.step_delay)

    def register_device(self, device_id: str, role: str) -> None:
        self.publish_status(device_id, "online")
        self.publish_gaze(device_id, role, False, confidence=0.0)
        time.sleep(self.step_delay)

    def publish_gaze(self, device_id: str, role: str, looking: bool, confidence: float = 1.0) -> None:
        topic = f"gazecontrol/device/{device_id}/gaze_status"
        payload = {
            "deviceId": device_id,
            "role": role,
            "lookingAtScreen": looking,
            "confidence": confidence,
            "calibrated": True,
            "timestamp": int(time.time() * 1000),
        }
        self.client.publish(topic, json.dumps(payload, ensure_ascii=False), qos=1)
        print(f"[gaze] {device_id} role={role} looking={looking} conf={confidence}")

    def hold_gaze(self, device_id: str, role: str, seconds: Optional[float] = None) -> None:
        duration = self.hold_seconds if seconds is None else seconds
        start = time.time()
        while True:
            self.publish_gaze(device_id, role, True)
            elapsed = time.time() - start
            if elapsed >= duration:
                break
            time.sleep(min(self.tick_seconds, duration - elapsed))
        self.publish_gaze(device_id, role, False, confidence=0.0)
        time.sleep(self.step_delay)

    def scenario_single_select(self) -> None:
        print("[sim] scenario=single_select")
        single_yes = self.device_id("single-yes")
        self.register_device(single_yes, "yes")
        self.hold_gaze(single_yes, "yes", seconds=3.2)
        time.sleep(self.action_pause)
        self.hold_gaze(single_yes, "yes", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(single_yes, "yes", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(single_yes, "yes", seconds=1.7)

    def scenario_dual_confirm(self) -> None:
        print("[sim] scenario=dual_confirm")
        left_yes = self.device_id("left-yes")
        right_no = self.device_id("right-no")
        self.register_device(left_yes, "yes")
        self.register_device(right_no, "no")
        self.hold_gaze(left_yes, "yes", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(left_yes, "yes", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(left_yes, "yes", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(left_yes, "yes", seconds=1.7)

    def scenario_dual_skip(self) -> None:
        print("[sim] scenario=dual_skip")
        left_yes = self.device_id("left-yes")
        right_no = self.device_id("right-no")
        self.register_device(left_yes, "yes")
        self.register_device(right_no, "no")
        self.hold_gaze(left_yes, "yes", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(right_no, "no", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(left_yes, "yes", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(right_no, "no", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(left_yes, "yes", seconds=1.7)
        time.sleep(self.action_pause)
        self.hold_gaze(left_yes, "yes", seconds=1.7)

    def scenario_dual_emergency(self) -> None:
        print("[sim] scenario=dual_emergency")
        left_yes = self.device_id("left-yes")
        right_no = self.device_id("right-no")
        self.register_device(left_yes, "yes")
        self.register_device(right_no, "no")
        for _ in range(3):
            self.hold_gaze(left_yes, "yes", seconds=1.7)
            time.sleep(self.action_pause)

    def run_scenario(self, scenario: str) -> None:
        if scenario == "single_select":
            self.scenario_single_select()
        elif scenario == "dual_confirm":
            self.scenario_dual_confirm()
        elif scenario == "dual_skip":
            self.scenario_dual_skip()
        elif scenario == "dual_emergency":
            self.scenario_dual_emergency()
        else:
            raise ValueError(f"unknown scenario: {scenario}")


def main() -> None:
    parser = argparse.ArgumentParser(description="MQTT simulator for FlowEye scanning coordinator")
    parser.add_argument("--host", default="localhost", help="MQTT broker host")
    parser.add_argument("--port", type=int, default=1883, help="MQTT broker port")
    parser.add_argument(
        "--scenario",
        choices=["single_select", "dual_confirm", "dual_skip", "dual_emergency"],
        required=True,
        help="simulation scenario to run",
    )
    parser.add_argument("--step-delay", type=float, default=0.3, help="delay between discrete simulation steps")
    parser.add_argument("--hold-seconds", type=float, default=1.7, help="default continuous gaze duration")
    parser.add_argument("--tick-seconds", type=float, default=0.2, help="interval for repeated gaze updates while holding")
    parser.add_argument("--action-pause", type=float, default=2.0, help="pause between high-level gaze actions to allow coordinator state/TTS to settle")
    parser.add_argument("--wait-after", type=float, default=3.0, help="time to wait for trailing coordinator decisions")
    parser.add_argument(
        "--device-prefix",
        default=f"sim{os.getpid()}",
        help="prefix added to every simulated device id so runs do not collide",
    )
    args = parser.parse_args()

    simulator = CoordinatorFlowSimulator(
        host=args.host,
        port=args.port,
        step_delay=args.step_delay,
        hold_seconds=args.hold_seconds,
        tick_seconds=args.tick_seconds,
        action_pause=args.action_pause,
        device_prefix=args.device_prefix,
    )
    try:
        simulator.connect()
        simulator.run_scenario(args.scenario)
        time.sleep(args.wait_after)
    finally:
        simulator.close()


if __name__ == "__main__":
    main()
