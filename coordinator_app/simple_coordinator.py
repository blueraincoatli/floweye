#!/usr/bin/env python3
"""
简单的中央协调器 - Python版本
用于快速测试和演示多设备协同视线交互系统

功能：
1. 连接MQTT Broker（支持自动重连）
2. 接收移动设备的视线状态
3. 显示实时状态
4. 做出协同决策（含防抖和置信度阈值）
"""

import json
import time
import threading
import argparse
import logging
from datetime import datetime
from typing import Dict, Any, Optional
import paho.mqtt.client as mqtt

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger("Coordinator")


class SimpleCoordinator:
    # 决策参数
    DEVICE_VALID_WINDOW = 30       # 设备有效时间窗口（秒）
    DEVICE_OFFLINE_TIMEOUT = 60    # 设备离线清理超时（秒）
    MIN_CONFIDENCE = 0.4           # 最低置信度阈值
    CONFIRM_CONSECUTIVE = 2        # 连续一致确认次数
    DECISION_CHANGE_DELTA = 0.1    # 置信度变化最小差值

    def __init__(self, broker_host="192.168.1.100", broker_port=1883):
        self.broker_host = broker_host
        self.broker_port = broker_port
        self.mqtt_client = None
        self.devices = {}  # 存储设备状态
        self.lock = threading.Lock()  # 保护 self.devices

        self.last_decision = "none"
        self.decision_confidence = 0.0
        self.consecutive_count = 0   # 防抖计数器
        self.running = False

        # MQTT主题
        self.topics = {
            "gaze_status": "gazecontrol/device/+/gaze_status",
            "device_status": "gazecontrol/device/+/status",
            "coordination": "gazecontrol/coordination/decision"
        }

    def connect_mqtt(self):
        """连接MQTT Broker"""
        try:
            self.mqtt_client = mqtt.Client(client_id="Coordinator_Central")
            self.mqtt_client.on_connect = self.on_connect
            self.mqtt_client.on_message = self.on_message
            self.mqtt_client.on_disconnect = self.on_disconnect

            # 配置自动重连
            self.mqtt_client.reconnect_delay_set(min_delay=1, max_delay=30)

            logger.info("正在连接MQTT Broker: %s:%d", self.broker_host, self.broker_port)
            self.mqtt_client.connect(self.broker_host, self.broker_port, 60)
            self.mqtt_client.loop_start()

        except Exception as e:
            logger.error("MQTT连接失败: %s", e)

    def on_connect(self, client, userdata, flags, rc):
        """MQTT连接回调"""
        if rc == 0:
            logger.info("[OK] MQTT连接成功")
            for topic_name, topic in self.topics.items():
                if topic_name != "coordination":
                    client.subscribe(topic)
                    logger.info("[INFO] 已订阅: %s", topic)
        else:
            logger.error("[ERROR] MQTT连接失败，错误代码: %d", rc)

    def on_message(self, client, userdata, msg):
        """MQTT消息回调"""
        try:
            payload_str = msg.payload.decode()
        except (UnicodeDecodeError, AttributeError) as e:
            logger.warning("消息解码失败 (topic=%s): %s", msg.topic, e)
            return

        try:
            payload = json.loads(payload_str)
        except json.JSONDecodeError as e:
            logger.warning("JSON解析失败 (topic=%s): %s, 原始数据: %s", msg.topic, e, payload_str[:200])
            return

        try:
            topic = msg.topic
            parts = topic.split("/")

            # 精确匹配: gazecontrol/device/{deviceId}/gaze_status
            if len(parts) == 5 and parts[0] == "gazecontrol" and parts[1] == "device" and parts[3] == "gaze_status":
                self.handle_gaze_status(payload)
            # 精确匹配: gazecontrol/device/{deviceId}/status
            elif len(parts) == 5 and parts[0] == "gazecontrol" and parts[1] == "device" and parts[3] == "status":
                self.handle_device_status(payload)
        except Exception as e:
            logger.error("处理消息异常 (topic=%s): %s", msg.topic, e)

    def on_disconnect(self, client, userdata, rc):
        """MQTT断开回调"""
        if rc != 0:
            logger.warning("[WARN] MQTT非正常断开 (rc=%d)，自动重连已启用", rc)
        else:
            logger.info("[INFO] MQTT连接断开")

    def handle_gaze_status(self, data):
        """处理视线状态消息"""
        device_id = data.get("deviceId", "unknown")
        gaze_target = data.get("gazeTarget", "none")
        confidence = data.get("confidence", 0.0)
        is_looking = data.get("isLookingAtThisDevice", False)

        with self.lock:
            self.devices[device_id] = {
                "gazeTarget": gaze_target,
                "confidence": confidence,
                "isLookingAtThisDevice": is_looking,
                "lastUpdate": time.time(),
                "displayedContent": data.get("displayedContent", {})
            }

            self.make_coordination_decision()

    def handle_device_status(self, data):
        """处理设备状态消息"""
        device_id = data.get("deviceId", "unknown")
        status = data.get("status", "unknown")

        with self.lock:
            if device_id not in self.devices:
                self.devices[device_id] = {}

            self.devices[device_id]["deviceStatus"] = status
            self.devices[device_id]["lastSeen"] = time.time()

        logger.info("[INFO] 设备 %s: %s", device_id[:12], status)

    def make_coordination_decision(self):
        """协同决策算法（含防抖和最低置信度）"""
        current_time = time.time()
        valid_devices = {}

        # 过滤有效设备
        for device_id, data in self.devices.items():
            if current_time - data.get("lastUpdate", 0) < self.DEVICE_VALID_WINDOW:
                valid_devices[device_id] = data

        if not valid_devices:
            decision = "none"
            confidence = 0.0
        else:
            # 收集所有有效注视方向
            gaze_votes = {}  # target -> (total_confidence, count)
            has_conflict = False
            best_target = "none"
            best_confidence = 0.0

            for device_id, data in valid_devices.items():
                if (data.get("isLookingAtThisDevice", False) and
                    data.get("gazeTarget") in ["yes", "no"] and
                    data.get("confidence", 0) >= self.MIN_CONFIDENCE):

                    target = data.get("gazeTarget")
                    conf = data.get("confidence", 0)
                    total, count = gaze_votes.get(target, (0.0, 0))
                    gaze_votes[target] = (total + conf, count + 1)

            if gaze_votes:
                # 检测冲突（同时有"是"和"否"的高置信度投票）
                if len(gaze_votes) > 1:
                    has_conflict = True

                best_target, (total_conf, count) = max(gaze_votes.items(), key=lambda x: x[1][0])
                best_confidence = total_conf / count

                if has_conflict:
                    logger.warning("[WARN] 检测到设备冲突: %s", list(gaze_votes.keys()))
                    # 冲突时降低置信度，不立即决策
                    best_confidence *= 0.5

            decision = best_target
            confidence = best_confidence

        # 防抖：连续 N 次一致才确认决策变化
        if decision != "none" and decision == self.last_decision:
            self.consecutive_count += 1
        elif decision != "none" and decision != self.last_decision:
            self.consecutive_count = 1
        else:
            self.consecutive_count = 0

        # 检查是否满足发布条件
        should_publish = False
        if decision == "none":
            # "无注视"状态变化立即发布
            if self.last_decision != "none":
                should_publish = True
        elif self.consecutive_count >= self.CONFIRM_CONSECUTIVE:
            # 有效注视需连续确认
            if decision != self.last_decision or abs(confidence - self.decision_confidence) > self.DECISION_CHANGE_DELTA:
                should_publish = True

        if should_publish:
            self.last_decision = decision
            self.decision_confidence = confidence
            self.publish_decision(decision, confidence)
            self.display_status()

    def publish_decision(self, decision, confidence):
        """发布协同决策结果"""
        if self.mqtt_client and self.mqtt_client.is_connected():
            with self.lock:
                active_count = len([d for d in self.devices.values()
                                   if time.time() - d.get("lastUpdate", 0) < self.DEVICE_VALID_WINDOW])

            decision_data = {
                "decision": decision,
                "confidence": confidence,
                "timestamp": int(time.time() * 1000),
                "activeDevices": active_count
            }

            try:
                self.mqtt_client.publish(
                    self.topics["coordination"],
                    json.dumps(decision_data),
                    qos=1
                )
            except Exception as e:
                logger.error("发布决策失败: %s", e)

    def display_status(self):
        """显示当前状态"""
        lines = []
        lines.append("=" * 60)
        lines.append("时间: %s" % datetime.now().strftime('%H:%M:%S'))
        lines.append("当前决策: %s (置信度: %.2f)" % (self.last_decision, self.decision_confidence))

        with self.lock:
            lines.append("活跃设备: %d" % len(self.devices))
            for device_id, data in self.devices.items():
                age = time.time() - data.get("lastUpdate", 0)
                if age < 5:
                    status = "[OK]"
                elif age < 15:
                    status = "[SLOW]"
                else:
                    status = "[STALE]"

                lines.append("  %s %s... -> %s (置信度: %.2f)" % (
                    status, device_id[:12],
                    data.get('gazeTarget', 'none'),
                    data.get('confidence', 0)
                ))

        lines.append("=" * 60)
        print("\n".join(lines))

    def run(self):
        """运行协调器"""
        logger.info("[START] 启动中央协调器...")
        logger.info("[INFO] 等待移动设备连接...")

        self.running = True
        self.connect_mqtt()

        try:
            while self.running:
                time.sleep(10)  # 降低清理频率
                self.cleanup_offline_devices()

        except KeyboardInterrupt:
            logger.info("[STOP] 正在停止协调器...")
            self.running = False

        finally:
            if self.mqtt_client:
                self.mqtt_client.loop_stop()
                self.mqtt_client.disconnect()

    def cleanup_offline_devices(self):
        """清理离线设备"""
        current_time = time.time()
        with self.lock:
            offline_devices = [
                device_id for device_id, data in self.devices.items()
                if current_time - data.get("lastUpdate", 0) > self.DEVICE_OFFLINE_TIMEOUT
            ]
            for device_id in offline_devices:
                logger.info("[OFFLINE] 设备离线: %s", device_id)
                del self.devices[device_id]


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="多设备协同视线交互系统 - 中央协调器")
    parser.add_argument("host", nargs="?", default="192.168.1.100", help="MQTT Broker地址 (默认: 192.168.1.100)")
    parser.add_argument("port", nargs="?", type=int, default=1883, help="MQTT Broker端口 (默认: 1883)")
    args = parser.parse_args()

    logger.info("=" * 60)
    logger.info("多设备协同视线交互系统 - 中央协调器 v1.1.0")
    logger.info("Broker: %s:%d", args.host, args.port)
    logger.info("=" * 60)

    coordinator = SimpleCoordinator(args.host, args.port)
    coordinator.run()


if __name__ == "__main__":
    main()
