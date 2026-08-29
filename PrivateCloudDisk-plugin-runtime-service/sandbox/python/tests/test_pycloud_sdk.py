"""PyCloud lifecycle and Unix Socket capability transport tests.

CF-PLUGIN-UDS-001: capability calls must use the private UDS protobuf contract;
tests deliberately contain no work-directory request/response polling fixture.
"""

from __future__ import annotations

import json
import os
import socket
import struct
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch

import pycloud
from pycloud import capabilities, file


class PyCloudFileTests(unittest.TestCase):
    def test_preprocess_uses_staging_permissions_and_writes_candidate(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / "input.bin", Path(directory) / "output.bin"
            source.write_bytes(b"source")
            pycloud.configure({"permissions": ["file.content.read_staging", "file.content.write_pre_activation"], "content_frozen": False})
            with patch.object(file, "_INPUT", str(source)), patch.object(file, "_OUTPUT", str(output)):
                self.assertEqual(file.read_staging("logical-file-id"), b"source")
                file.write_pre_activation("logical-file-id", b"candidate")
            self.assertEqual(output.read_bytes(), b"candidate")

    def test_available_content_is_immutable(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "input.bin"
            source.write_bytes(b"active")
            pycloud.configure({"permissions": ["file.content.read", "file.content.write_pre_activation"], "content_frozen": True})
            with patch.object(file, "_INPUT", str(source)):
                self.assertEqual(file.read(), b"active")
                with self.assertRaises(PermissionError):
                    file.write_pre_activation(b"forbidden")


class PyCloudCapabilitySocketTests(unittest.TestCase):
    """A small AF_UNIX fake Runtime Agent validates real SDK framing behavior."""

    def setUp(self):
        # Use the system tmpfs: the macOS app sandbox may deny bind(2) beneath
        # its per-process TemporaryDirectory path although it permits /tmp.
        self.directory = tempfile.TemporaryDirectory(dir="/private/tmp")
        self.socket_path = str(Path(self.directory.name) / "runtime.sock")
        capabilities._drop_connection()
        self.patch_socket_path = patch("pycloud.capabilities._SOCKET_PATH", self.socket_path)
        self.patch_socket_path.start()
        self.addCleanup(self.patch_socket_path.stop)
        self.addCleanup(capabilities._drop_connection)
        self.addCleanup(self.directory.cleanup)
        capabilities._configure_runtime_transport("instance-abcdefghijklmnopqrstuvwxyz", "t" * 64)
        pycloud.configure({"permissions": ["platform.capability.invoke"], "user_id": "untrusted-user", "space_id": "untrusted-space"})

    def _start_server(self, handler, expected_calls=1):
        ready, done = threading.Event(), threading.Event()
        seen = []

        def receive_exact(connection, size):
            chunks = []
            while size:
                data = connection.recv(size)
                if not data:
                    raise OSError("closed")
                chunks.append(data)
                size -= len(data)
            return b"".join(chunks)

        def worker():
            listener = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            listener.bind(self.socket_path)
            listener.listen(16)
            listener.settimeout(5)
            ready.set()
            try:
                while len(seen) < expected_calls:
                    connection, _ = listener.accept()
                    with connection:
                        connection.settimeout(5)
                        header = receive_exact(connection, 4)
                        size = struct.unpack(">I", header)[0]
                        request = capabilities._decode_fields(receive_exact(connection, size))
                        seen.append(request)
                        response = handler(request)
                        try:
                            connection.sendall(struct.pack(">I", len(response)) + response)
                        except BrokenPipeError:
                            # Timeout tests intentionally close the SDK side
                            # before the fake Agent responds.
                            pass
            finally:
                listener.close()
                done.set()

        thread = threading.Thread(target=worker, daemon=True)
        thread.start()
        self.assertTrue(ready.wait(2), "UDS fake Runtime did not start")
        return seen, done, thread

    @staticmethod
    def _success(request, output):
        encoded = json.dumps(output, ensure_ascii=False).encode("utf-8")
        return b"".join((
            capabilities._field_bytes(1, request[1]),
            capabilities._field_bytes(2, b"SUCCESS"),
            capabilities._field_bytes(3, encoded),
        ))

    @staticmethod
    def _failure(request, code, message):
        error = capabilities._field_bytes(1, code.encode()) + capabilities._field_bytes(2, message.encode())
        return capabilities._field_bytes(1, request[1]) + capabilities._field_bytes(2, b"FAILED") + capabilities._field_bytes(4, error)

    def test_call_api_round_trip_uses_instance_credentials_not_context_identity(self):
        seen, done, thread = self._start_server(lambda request: self._success(request, {"echo": capabilities._field_text(request, 2)}))
        self.assertEqual(pycloud.call_api("api.user.info", {"name": "alice"}), {"echo": "api.user.info"})
        self.assertTrue(done.wait(2)); thread.join(2)
        self.assertEqual(capabilities._field_text(seen[0], 4), "instance-abcdefghijklmnopqrstuvwxyz")
        self.assertEqual(seen[0][5], b"t" * 64)
        self.assertNotIn(b"untrusted-user", seen[0][3])

    def test_capability_failure_and_missing_permission(self):
        _, done, thread = self._start_server(lambda request: self._failure(request, "CAPABILITY_FORBIDDEN", "空间权限不足"))
        with self.assertRaises(pycloud.CapabilityError) as raised:
            pycloud.call_api("api.space.members.list", {})
        self.assertEqual(raised.exception.code, "CAPABILITY_FORBIDDEN")
        self.assertTrue(done.wait(2)); thread.join(2)
        pycloud.configure({"permissions": []})
        with self.assertRaises(PermissionError):
            pycloud.call_api("api.user.info", {})

    def test_socket_timeout_and_unavailable_are_structured(self):
        _, done, thread = self._start_server(lambda request: (time.sleep(0.4) or self._success(request, {})))
        with self.assertRaises(pycloud.CapabilityTimeout):
            pycloud.call_api("api.user.info", {}, timeout=0.1)
        self.assertTrue(done.wait(2)); thread.join(2)
        capabilities._drop_connection()
        with self.assertRaises(pycloud.CapabilityError) as raised:
            pycloud.call_api("api.user.info", {}, timeout=0.2)
        self.assertEqual(raised.exception.code, "RUNTIME_SOCKET_UNAVAILABLE")

    def test_helpers_and_concurrent_calls_use_uds_without_audit_file(self):
        seen, done, thread = self._start_server(
            lambda request: self._success(request, {"capability": capabilities._field_text(request, 2)}), expected_calls=6,
        )
        self.assertEqual(pycloud.user_info("u")["capability"], "api.user.info")
        self.assertEqual(pycloud.space_members_list("s")["capability"], "api.space.members.list")
        self.assertEqual(pycloud.notification_send(["u"], "hello")["capability"], "api.notification.send")
        results = []

        def concurrent_call():
            try:
                results.append(pycloud.call_api("api.file.read", {}))
            finally:
                capabilities._drop_connection()

        threads = [threading.Thread(target=concurrent_call) for _ in range(3)]
        for item in threads: item.start()
        for item in threads: item.join(2)
        self.assertEqual(len(results), 3)
        self.assertTrue(done.wait(2)); thread.join(2)
        self.assertEqual(len(seen), 6)
        self.assertFalse((Path(self.directory.name) / "capability-audit.jsonl").exists())

    def test_invalid_input_and_notification_message_are_rejected_before_socket(self):
        with self.assertRaises(pycloud.CapabilityError):
            pycloud.call_api("", {})
        with self.assertRaises(pycloud.CapabilityError):
            pycloud.call_api("api.file.read", [])
        with self.assertRaises(pycloud.CapabilityError):
            pycloud.notification_send(["u"], "")
        self.assertFalse(os.path.exists(self.socket_path))


if __name__ == "__main__":
    unittest.main()
