#!/usr/bin/env python3
"""
简化版中央协调器 - 无编码问题
"""

import json
import time
import paho.mqtt.client as mqtt

class SimpleTestCoordinator:
    def __init__(self, broker_host="localhost", broker_port=1883):
        self.broker_host = broker_host
        self.broker_port = broker_port
        self.devices = {}
        
    def on_connect(self, client, userdata, flags, rc):
        print(f"连接到MQTT Broker: {rc}")
        client.subscribe("gazecontrol/device/+/gaze_status")
        client.subscribe("gazecontrol/device/+/status")
        
    def on_message(self, client, userdata, msg):
        topic = msg.topic
        payload = json.loads(msg.payload.decode())
        
        if "gaze_status" in topic:
            device_id = topic.split("/")[2]
            self.devices[device_id] = payload
            self.make_decision()
            
    def make_decision(self):
        active_devices = [d for d in self.devices.values() if d.get("is_gazing", False)]
        
        if active_devices:
            # 选择置信度最高的设备
            best_device = max(active_devices, key=lambda x: x.get("confidence", 0))
            print(f"[决策] 患者选择了: {best_device.get('choice', '未知')} (置信度: {best_device.get('confidence', 0)})")
        else:
            print("[状态] 无设备检测到注视")
            
        print(f"[设备状态] {len(self.devices)}台设备在线")
        for device_id, status in self.devices.items():
            choice = status.get('choice', '未知')
            confidence = status.get('confidence', 0)
            is_gazing = status.get('is_gazing', False)
            print(f"  {device_id}: 选择={choice}, 置信度={confidence}, 注视={is_gazing}")
    
    def start(self):
        client = mqtt.Client()
        client.on_connect = self.on_connect
        client.on_message = self.on_message
        
        try:
            client.connect(self.broker_host, self.broker_port, 60)
            print(f"中央协调器启动成功！")
            print(f"MQTT Broker: {self.broker_host}:{self.broker_port}")
            print("等待移动设备连接...")
            
            client.loop_forever()
        except Exception as e:
            print(f"启动失败: {e}")

if __name__ == "__main__":
    coordinator = SimpleTestCoordinator("localhost", 1883)
    coordinator.start()