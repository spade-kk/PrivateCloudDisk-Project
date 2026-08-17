import unittest
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from validate_python import validate


class PythonMarkerValidationTest(unittest.TestCase):
    def test_extracts_test_and_capability_markers_without_execution(self):
        report = validate(
            'from pycloud import capability, test\n'
            '@capability("file_analysis")\n'
            'def analyze(context):\n'
            '    return {}\n'
            '@test\n'
            'def test_analyze(context):\n'
            '    return analyze(context)\n',
            'src/main.py',
        )
        self.assertTrue(report["valid"])
        self.assertEqual(report["metrics"]["test_entrypoints"][0]["name"], "test_analyze")
        self.assertEqual(report["metrics"]["capabilities"][0]["name"], "file_analysis")

    def test_rejects_dynamic_execution(self):
        report = validate('def main(context):\n    return eval("1")\n', 'src/main.py')
        self.assertFalse(report["valid"])
        self.assertEqual(report["error_type"], "SECURITY_VIOLATION")


if __name__ == "__main__":
    unittest.main()
