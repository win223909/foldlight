#!/usr/bin/env python3
"""USB-only angle bridge. App-scoped sampling, immediate angle delivery, independent dual-display control."""
import argparse
import queue
import re
import signal
import socket
import struct
import subprocess
import threading
import time

PATTERNS = (
    re.compile(r"\[0\]folding_angle ts=(\d+) ns value \[\s*([0-9.]+)/"),
    re.compile(r"\[0\]lid_angle_fusion ts=(\d+) ns value \[\d+/\s*([0-9.]+)/"),
)
HEARTBEAT_SECONDS = .05  # Control/liveness only: never counted as sensor measurements.


def parse(line):
    if "handle_sns_client_event:" not in line:
        return None
    for pattern in PATTERNS:
        match = pattern.search(line)
        if match:
            try:
                stamp, angle = int(match[1]), float(match[2])
                return (stamp, angle) if stamp > 0 and 0 <= angle <= 180.5 else None
            except ValueError:
                return None
    return None


def arguments(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    duration = parser.add_mutually_exclusive_group()
    duration.add_argument("--seconds", type=int, default=0, help="optional explicit timed test; default 0 has no time limit")
    duration.add_argument("--continuous", action="store_true", help="compatibility alias; continuous is now the default")
    parser.add_argument("--watch-app", action="store_true", help="wait through USB disconnection and resume automatically when the App opens")
    parser.add_argument("--dual-screen", action="store_true", help="verified SM-F9710 only; foreground fullscreen heartbeat controls concurrent displays")
    options = parser.parse_args(argv)
    if options.seconds < 0:
        parser.error("--seconds must be nonnegative")
    return options


class DualController:
    """Blocking shell and display lifecycle work must never run on the angle sender."""
    def __init__(self, adb, enabled, deadline):
        self.adb, self.enabled, self.deadline = adb, enabled, deadline
        self.stopping = threading.Event()
        self.allowed = False
        self.last_ack = 0.0
        self.lease = None
        self.failures = 0
        self.retry_after = 0.0
        self.worker = threading.Thread(target=self._run, name="Foldlight dual control", daemon=True)
        self.worker.start()

    def acknowledge(self, allowed):
        self.last_ack = time.monotonic()
        self.allowed = allowed

    def _stop_lease(self):
        if self.lease is None:
            return
        owned, self.lease = self.lease, None
        try:
            owned.stdin.close()
        except OSError:
            pass
        try:
            owned.wait(timeout=4)
        except subprocess.TimeoutExpired:
            owned.terminate()
            try:
                owned.wait(timeout=2)
            except subprocess.TimeoutExpired:
                owned.kill()
                owned.wait()
        print("Dual screen lease ended", flush=True)

    def _update(self):
        if not self.enabled:
            return
        allowed = self.allowed and time.monotonic() - self.last_ack < 1.5
        if not allowed:
            self._stop_lease()
            self.failures = 0
            return
        if self.lease is not None and self.lease.poll() is not None:
            self._stop_lease()
            self.failures += 1
            self.retry_after = time.monotonic() + 2
            print("Dual helper ended; angle connection continues", flush=True)
            return
        if self.failures >= 3 or time.monotonic() < self.retry_after:
            return
        if self.lease is None:
            state = subprocess.check_output(self.adb + ["shell", "dumpsys", "device_state"], text=True, timeout=2)
            if "mOverrideState=Optional.empty" not in state:
                self.retry_after = time.monotonic() + .5
                return  # Never replace another owner's request.
            if self.stopping.is_set() or not self.allowed or time.monotonic() - self.last_ack >= 1.5:
                return
            duration = 0 if self.deadline == float("inf") else max(1, min(int(self.deadline - time.monotonic()), 600))
            self.lease = subprocess.Popen(self.adb + ["shell", "CLASSPATH=/data/local/tmp/foldlight-dual-lease.dex", "app_process", "/system/bin", "DualScreenLease", str(duration), "5"], stdin=subprocess.PIPE)
        self.lease.stdin.write(b".")
        self.lease.stdin.flush()

    def _run(self):
        try:
            while not self.stopping.is_set():
                try:
                    self._update()
                except (OSError, subprocess.SubprocessError):
                    self._stop_lease()
                    self.failures += 1
                    self.retry_after = time.monotonic() + 2
                self.stopping.wait(HEARTBEAT_SECONDS)
        finally:
            self._stop_lease()

    def close(self):
        self.stopping.set()
        self.worker.join(timeout=8)


class AngleStream:
    def __init__(self, adb):
        pid = int(subprocess.check_output(adb + ["shell", "pidof", "android.hardware.sensors-service.multihal"], text=True, timeout=2).strip())
        self.samples = queue.Queue(maxsize=1)
        self.logs = subprocess.Popen(adb + ["logcat", "-b", "main", f"--pid={pid}", "--uid=1000", "-v", "brief", "-T", "1", "-s", "sensors-hal:I", "*:S"], stdout=subprocess.PIPE, text=True, bufsize=1)
        self.worker = threading.Thread(target=self._read, name="Foldlight angle logs", daemon=True)
        self.worker.start()

    def _read(self):
        for line in self.logs.stdout:
            sample = parse(line)
            if sample:
                try:
                    self.samples.get_nowait()
                except queue.Empty:
                    pass
                self.samples.put_nowait(sample)

    def close(self):
        self.logs.terminate()
        try:
            self.logs.wait(timeout=2)
        except subprocess.TimeoutExpired:
            self.logs.kill()
            self.logs.wait()
        self.worker.join(timeout=1)
        self.logs.stdout.close()


def run_session(connection, adb, dual, deadline, stopping):
    """Only entered after the App has acknowledged: no sensor log reader while the App is closed."""
    stream = AngleStream(adb)
    control = DualController(adb, dual, deadline)
    count, last_stamp, last_report = 0, 0, time.monotonic()
    print("App connected; angle sampling started", flush=True)
    try:
        while not stopping.is_set() and time.monotonic() < deadline and stream.logs.poll() is None:
            try:
                stamp, angle = stream.samples.get(timeout=HEARTBEAT_SECONDS)
                if stamp <= last_stamp:
                    continue
                last_stamp = stamp
            except queue.Empty:
                stamp, angle = 0, 0
            connection.sendall(struct.pack(">qf", stamp, angle))
            acknowledgement = connection.recv(1)
            if not acknowledgement:
                raise OSError("App disconnected")
            control.acknowledge(acknowledgement == b"\x01")
            if stamp:
                count += 1
                if time.monotonic() - last_report >= 30:
                    print(f"Forwarded {count} original angle samples this session", flush=True)
                    last_report = time.monotonic()
    finally:
        control.close()
        stream.close()
        print(f"App sampling stopped; forwarded {count} samples", flush=True)


def watch(options, stopping):
    adb = [options.adb, "-s", options.serial]
    deadline = float("inf") if options.seconds == 0 else time.monotonic() + options.seconds
    last_status = None
    def status(message):
        nonlocal last_status
        if message != last_status:
            print(message, flush=True)
            last_status = message
    while not stopping.is_set() and time.monotonic() < deadline:
        port = None
        try:
            state = subprocess.check_output(adb + ["get-state"], text=True, stderr=subprocess.DEVNULL, timeout=2).strip()
            if state != "device":
                raise OSError("USB unavailable")
            if options.dual_screen:
                model = subprocess.check_output(adb + ["shell", "getprop", "ro.product.model"], text=True, timeout=2).strip()
                if model != "SM-F9710":
                    raise RuntimeError("Unverified dual-screen device")
                subprocess.run(adb + ["shell", "test", "-r", "/data/local/tmp/foldlight-dual-lease.dex"], check=True, timeout=2)
            uid = int(subprocess.check_output(adb + ["shell", "run-as", "com.zksaga.foldlight", "id", "-u"], text=True, timeout=2).strip())
            port = int(subprocess.check_output(adb + ["forward", "tcp:0", f"localabstract:foldlight.hinge.{uid}"], text=True, timeout=2).strip())
            next_device_check = time.monotonic() + 5
            while not stopping.is_set() and time.monotonic() < deadline:
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=.8) as connection:
                        connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                        connection.sendall(struct.pack(">qf", 0, 0))
                        if not connection.recv(1):
                            raise OSError("App not open")
                        status("USB ready; no automatic session timeout" if options.seconds == 0 else "USB ready; explicit timed test")
                        run_session(connection, adb, options.dual_screen, deadline, stopping)
                except (OSError, subprocess.SubprocessError) as error:
                    if isinstance(error, ConnectionRefusedError):
                        break  # A removed ADB forward must be recreated even after a quick USB reconnect.
                    if stopping.is_set():
                        break
                    status("Waiting for Foldlight to open; sampling is stopped")
                    if time.monotonic() >= next_device_check:
                        try:
                            if subprocess.check_output(adb + ["get-state"], text=True, stderr=subprocess.DEVNULL, timeout=2).strip() != "device":
                                break
                        except subprocess.SubprocessError:
                            break
                        next_device_check = time.monotonic() + 5
                    stopping.wait(.25)
        except (OSError, ValueError, subprocess.SubprocessError):
            status("Waiting for authorized USB device")
        finally:
            if port is not None:
                try:
                    subprocess.run(adb + ["forward", "--remove", f"tcp:{port}"], check=False, stderr=subprocess.DEVNULL, timeout=2)
                except subprocess.SubprocessError:
                    pass
        if not options.watch_app:
            break
        stopping.wait(2)
    print("USB watcher stopped", flush=True)


def main():
    options = arguments()
    stopping = threading.Event()
    for signum in (signal.SIGINT, signal.SIGTERM):
        signal.signal(signum, lambda *_: stopping.set())
    watch(options, stopping)


if __name__ == "__main__":
    main()
