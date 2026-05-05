import unittest
import unittest.mock as mock
from pathlib import Path
import sys
import types
import json
import time

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.modules.setdefault("paho", types.ModuleType("paho"))
sys.modules.setdefault("paho.mqtt", types.ModuleType("paho.mqtt"))
sys.modules.setdefault("paho.mqtt.client", types.ModuleType("paho.mqtt.client"))

from scanning_coordinator import ConfigLoader, GazeInterpreter, InteractionMode, ScanningCoordinator, State


class FakeTTS:
    def __init__(self):
        self.spoken = []

    def speak(self, text):
        self.spoken.append(text)

    def stop(self):
        pass


class CoordinatorStateMachineTest(unittest.TestCase):
    def setUp(self):
        self.coordinator = ScanningCoordinator(
            broker_host="localhost",
            broker_port=1883,
            menu_path="",
            patient_path="",
        )
        self.coordinator.tts = FakeTTS()
        self.published = []
        self.coordinator.publish_decision = lambda decision_type, option, urgency="normal": self.published.append(
            {
                "type": decision_type,
                "option": option.get("id") if option else None,
                "urgency": urgency,
            }
        )

    def test_wake_enters_scan_and_speaks_first_option(self):
        self.coordinator._handle_action("wake", None)

        self.assertEqual(self.coordinator.state, State.SCAN)
        self.assertEqual(self.coordinator.menu_engine.get_current_option()["id"], "uncomfortable")
        self.assertIn("您不舒服吗？", self.coordinator.tts.spoken[-1])

    def test_select_submenu_keeps_scan_and_speaks_first_child(self):
        self.coordinator._enter_scan()
        self.coordinator._handle_action("select", self.coordinator.menu_engine.get_current_option())

        self.assertEqual(self.coordinator.state, State.SCAN)
        self.assertFalse(self.coordinator.menu_engine.is_at_root)
        self.assertEqual(self.coordinator.menu_engine.get_current_option()["id"], "headache")
        self.assertIn("您头疼吗？", self.coordinator.tts.spoken[-1])

    def test_leaf_selection_enters_confirm_and_confirm_executes(self):
        self.coordinator._enter_scan()
        self.coordinator._handle_action("select", self.coordinator.menu_engine.get_current_option())
        self.coordinator._handle_action("select", self.coordinator.menu_engine.get_current_option())

        self.assertEqual(self.coordinator.state, State.CONFIRM)
        self.assertEqual(self.coordinator._confirm_option["id"], "headache")
        self.assertIn("确认您头疼吗？", self.coordinator.tts.spoken[-1])

        self.coordinator._handle_action("confirm", None)

        self.assertEqual(self.coordinator.state, State.WAITING)
        self.assertEqual(self.published[-1]["type"], "selection")
        self.assertEqual(self.published[-1]["option"], "headache")
        self.assertIn("已通知", self.coordinator.tts.spoken[-1])

    def test_cancel_returns_to_scan(self):
        self.coordinator._enter_scan()
        self.coordinator._handle_action("select", self.coordinator.menu_engine.get_current_option())
        self.coordinator._handle_action("select", self.coordinator.menu_engine.get_current_option())

        self.coordinator._handle_action("cancel", None)

        self.assertEqual(self.coordinator.state, State.SCAN)
        self.assertIsNone(self.coordinator._confirm_option)
        self.assertEqual(self.coordinator.menu_engine.get_current_option()["id"], "headache")
        self.assertIn("您头疼吗？", self.coordinator.tts.spoken[-1])

    def test_skip_confirm_option_executes_without_confirm(self):
        self.coordinator._enter_scan()
        self.coordinator.menu_engine._current_index = 2
        self.coordinator._handle_action("select", self.coordinator.menu_engine.get_current_option())

        self.assertEqual(self.coordinator.state, State.SCAN)
        self.assertEqual(self.coordinator.menu_engine.get_current_option()["id"], "confirm_emergency")

        self.coordinator._handle_action("select", self.coordinator.menu_engine.get_current_option())

        self.assertEqual(self.coordinator.state, State.WAITING)
        self.assertEqual(self.published[-1]["type"], "selection")
        self.assertEqual(self.published[-1]["option"], "confirm_emergency")
        self.assertEqual(self.published[-1]["urgency"], "critical")

    def test_on_message_routes_four_part_topics(self):
        handled = []
        self.coordinator._handle_gaze_status = lambda payload: handled.append(("gaze", payload["deviceId"]))
        self.coordinator._handle_device_status = lambda payload: handled.append(("status", payload["deviceId"]))

        gaze_msg = types.SimpleNamespace(
            topic="gazecontrol/device/test-device/gaze_status",
            payload=json.dumps({"deviceId": "test-device"}).encode("utf-8"),
        )
        status_msg = types.SimpleNamespace(
            topic="gazecontrol/device/test-device/status",
            payload=json.dumps({"deviceId": "test-device", "status": "online"}).encode("utf-8"),
        )

        self.coordinator._on_message(None, None, gaze_msg)
        self.coordinator._on_message(None, None, status_msg)

        self.assertEqual(handled, [("gaze", "test-device"), ("status", "test-device")])


    def test_idle_published_on_reset(self):
        self.coordinator.mqtt_client = mock.MagicMock()
        self.coordinator.mqtt_client.is_connected.return_value = True
        self.coordinator._reset_to_idle()
        self.assertTrue(self.coordinator.mqtt_client.publish.called)


    def test_hesitation_resets_dwell(self):
        self.coordinator.state = State.SCAN
        old_dwell = self.coordinator._dwell_start
        self.coordinator.gaze._device_gaze_start["test_device"] = time.time() - 0.5
        action, param = self.coordinator.gaze.process_gaze_event(
            device_id="test_device",
            role="yes",
            is_looking=True,
            confidence=0.9,
            mode=InteractionMode.DUAL_SWITCH,
            current_state=self.coordinator.state,
            current_option=None,
        )
        self.assertEqual(action, "hesitate")
        self.coordinator._handle_action(action, param)
        self.assertGreater(self.coordinator._dwell_start, old_dwell)


class GazeInterpreterTest(unittest.TestCase):
    def setUp(self):
        self.config = ConfigLoader("", "")
        self.config.patient["dual_device"]["select_gaze_seconds"] = 0.0
        self.gaze = GazeInterpreter(self.config)

    def test_continuous_gaze_only_emits_once_until_release(self):
        action, _ = self.gaze.process_gaze_event(
            device_id="yes-1",
            role="yes",
            is_looking=True,
            confidence=1.0,
            mode=InteractionMode.DUAL_SWITCH,
            current_state=State.IDLE,
            current_option=None,
        )
        self.assertEqual(action, "wake")

        action, _ = self.gaze.process_gaze_event(
            device_id="yes-1",
            role="yes",
            is_looking=True,
            confidence=1.0,
            mode=InteractionMode.DUAL_SWITCH,
            current_state=State.IDLE,
            current_option=None,
        )
        self.assertIsNone(action)

        self.gaze.process_gaze_event(
            device_id="yes-1",
            role="yes",
            is_looking=False,
            confidence=0.0,
            mode=InteractionMode.DUAL_SWITCH,
            current_state=State.IDLE,
            current_option=None,
        )

        action, _ = self.gaze.process_gaze_event(
            device_id="yes-1",
            role="yes",
            is_looking=True,
            confidence=1.0,
            mode=InteractionMode.DUAL_SWITCH,
            current_state=State.IDLE,
            current_option=None,
        )
        self.assertEqual(action, "wake")

    def test_triple_yes_requires_separate_gaze_events(self):
        current_option = {"id": "water", "label": "想喝水"}

        action, _ = self.gaze.process_gaze_event(
            device_id="yes-1",
            role="yes",
            is_looking=True,
            confidence=1.0,
            mode=InteractionMode.DUAL_SWITCH,
            current_state=State.SCAN,
            current_option=current_option,
        )
        self.assertEqual(action, "select")

        for _ in range(3):
            action, _ = self.gaze.process_gaze_event(
                device_id="yes-1",
                role="yes",
                is_looking=True,
                confidence=1.0,
                mode=InteractionMode.DUAL_SWITCH,
                current_state=State.SCAN,
                current_option=current_option,
            )
            self.assertIsNone(action)

        for expected in ("select", "emergency"):
            self.gaze.process_gaze_event(
                device_id="yes-1",
                role="yes",
                is_looking=False,
                confidence=0.0,
                mode=InteractionMode.DUAL_SWITCH,
                current_state=State.SCAN,
                current_option=current_option,
            )
            action, _ = self.gaze.process_gaze_event(
                device_id="yes-1",
                role="yes",
                is_looking=True,
                confidence=1.0,
                mode=InteractionMode.DUAL_SWITCH,
                current_state=State.SCAN,
                current_option=current_option,
            )
            self.assertEqual(action, expected)


if __name__ == "__main__":
    unittest.main()
