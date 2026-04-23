import unittest
from pathlib import Path
import sys
import types

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.modules.setdefault("paho", types.ModuleType("paho"))
sys.modules.setdefault("paho.mqtt", types.ModuleType("paho.mqtt"))
sys.modules.setdefault("paho.mqtt.client", types.ModuleType("paho.mqtt.client"))

from scanning_coordinator import ConfigLoader, GazeInterpreter, InteractionMode, State


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
