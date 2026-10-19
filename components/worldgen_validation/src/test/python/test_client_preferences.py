"""Client preferences must not alter view distance, simulation, or player worlds."""

import importlib.util
from pathlib import Path
import tomllib
import unittest


SCRIPT = Path(__file__).resolve().parents[3] / "scripts/development/prepare_client.py"
SPEC = importlib.util.spec_from_file_location("prepare_client", SCRIPT)
CLIENT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CLIENT)


class ClientPreferencesTests(unittest.TestCase):
    def test_automatic_mutes_without_other_changes(self):
        text = "renderDistance:12\nsimulationDistance:8\nsoundCategory_music:0.7\nsoundCategory_master:1.0\n"
        actual = CLIENT.update_options(text, "automatic")
        self.assertIn("renderDistance:12\nsimulationDistance:8\nsoundCategory_music:0.7\n", actual)
        self.assertTrue(actual.endswith("soundCategory_master:0.0\n"))

    def test_acceptance_sound_and_idempotence(self):
        text = "soundCategory_master:1.0\nsoundCategory_master:0.8\nsoundCategory_music:0.9\n"
        actual = CLIENT.update_options(text, "acceptance")
        self.assertIn("overrideWidth:2560\noverrideHeight:1440\n", actual)
        self.assertTrue(actual.endswith("soundCategory_master:0.5\nsoundCategory_music:0.0\n"))
        self.assertEqual(1, actual.count("soundCategory_master:"))
        self.assertEqual(actual, CLIENT.update_options(actual, "acceptance"))

    def test_acceptance_cannot_use_validation(self):
        repository = SCRIPT.parents[4]
        with self.assertRaises(ValueError):
            CLIENT.client_directory(repository, "26.3-fabric", "acceptance", "performance-worldgen")

    def test_native_client_argument_file_is_not_mistaken_for_a_closed_instance(self):
        root = Path("D:/project")
        instance = root / "instances/1.21.1-neoforge/client"
        commands = ['java -cp D:/project/classes net.neoforged.devlaunch.Main @D:/project/build/clientRunProgramArgs.txt']
        self.assertTrue(CLIENT.owned_client_running(commands, root, instance))
        self.assertFalse(CLIENT.owned_client_running(['java @D:/other/build/clientRunProgramArgs.txt'], root, instance))

    def test_all_declared_automatic_clients_have_standard_resolution_without_acceptance_mods(self):
        repository = SCRIPT.parents[4]
        for path in (repository / "validations").glob("*/validation.toml"):
            document = tomllib.loads(path.read_text(encoding="utf-8"))
            client = document.get("instances", {}).get("client")
            if client is not None:
                with self.subTest(validation=path.parent.name):
                    self.assertEqual((1920, 1080), (client.get("width"), client.get("height")))
                    self.assertFalse({"customskinloader-bootstrap", "zume"} & set(client.get("mods", [])))


if __name__ == "__main__":
    unittest.main()
