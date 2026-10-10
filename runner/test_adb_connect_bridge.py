"""Contract tests for the Python ADB transport launched by the Android app.

These execute the actual embedded script with a mocked adb subprocess, so no
physical device or running ADB server is modified during regression tests.
"""
import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

SOURCE = (Path(__file__).resolve().parents[1] /
          'app/src/main/java/com/animacode/animadev/projects/AdbDeviceGateway.kt')
ADB = '/data/data/com.termux/files/usr/bin/adb'
REQ = '1' * 32
ENDPOINT = '192.168.1.132:36897'


def embedded_code():
    source = SOURCE.read_text()
    marker = 'val code="""\n'
    script = source.split(marker, 1)[1].split('"""', 1)[0]
    return compile(script, str(SOURCE), 'exec')


def result(stdout, code=0):
    return subprocess.CompletedProcess([], code, stdout.encode(), b'')


def scan(*rows):
    return result('List of devices attached\n' + '\n'.join(rows) + '\n')


class AdbBridgeContract(unittest.TestCase):
    def execute(self, outputs, endpoint=ENDPOINT):
        calls = []
        stream = io.StringIO()
        reply = iter(outputs)
        def fake_run(argv, **kwargs):
            calls.append(tuple(argv))
            self.assertEqual(argv[0], ADB)
            self.assertIsNotNone(kwargs.get('timeout'))
            return next(reply)
        with patch('subprocess.run', side_effect=fake_run):
            with patch.object(sys, 'argv', ['adb_bridge', REQ, endpoint]):
                with contextlib.redirect_stdout(stream):
                    exec(embedded_code(), {'__name__': '__main__'})
        payload = json.loads(stream.getvalue().strip())
        self.assertEqual(payload['request_id'], REQ)
        return payload, calls

    def test_reuses_healthy_termux_connection_without_reconnecting(self):
        healthy = scan(ENDPOINT + ' device model:Pixel_8')
        output, calls = self.execute([healthy, healthy])
        self.assertEqual(output['count'], 1)
        self.assertEqual(output['device'], ENDPOINT)
        self.assertEqual(output['connect_status'], 'ALREADY_CONNECTED')
        self.assertEqual(len(calls), 2)
        self.assertTrue(all(c[1:3] == ('devices','-l') for c in calls))

    def test_offline_transport_is_not_misreported_as_missing(self):
        offline = scan(ENDPOINT + ' offline transport_id:2')
        output, calls = self.execute([
            offline, result('failed to connect to '+ENDPOINT+': Connection refused', 1), offline
        ])
        self.assertEqual(output['count'], 0)
        self.assertEqual(output['offline'], 1)
        self.assertEqual(output['connect_status'], 'CONNECT_REFUSED')
        self.assertEqual(len(calls), 3)

    def test_connects_once_if_not_already_online(self):
        offline = scan(ENDPOINT + ' offline')
        connected = scan(ENDPOINT + ' device model:Pixel_8')
        output, calls = self.execute([offline, result('connected to '+ENDPOINT), connected])
        self.assertEqual(output['count'], 1)
        self.assertEqual(output['offline'], 0)
        self.assertEqual(output['connect_status'], 'CONNECTED')
        self.assertEqual(calls[1][:2], (ADB, 'connect'))

    def test_query_only_never_initiates_connection(self):
        output, calls = self.execute([scan(), scan()], endpoint='')
        self.assertTrue(output['ok'])
        self.assertEqual(output['count'], 0)
        self.assertEqual(output['connect_status'], 'NOT_ATTEMPTED')
        self.assertEqual(len(calls), 2)

    def test_explicit_unauthorized_is_distinguished(self):
        unauthorized = scan('ADB1234 unauthorized')
        output, calls = self.execute([unauthorized, unauthorized], endpoint='')
        self.assertEqual(output['count'], 0)
        self.assertEqual(output['unauthorized'], 1)
        self.assertEqual(len(calls), 2)


if __name__ == '__main__':
    unittest.main()
