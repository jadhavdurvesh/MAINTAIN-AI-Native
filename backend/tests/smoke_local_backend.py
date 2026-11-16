"""Boot the migrated FastAPI application against a temporary local SQLite database.

This is an integration smoke test for the Native desktop launch configuration.
It does not use the source product's database or any cloud credentials.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
from urllib.error import URLError
from urllib.request import Request, urlopen


BACKEND_DIR = Path(__file__).resolve().parents[1]
REQUIRED_ROUTERS = {
    "machines",
    "maintenance",
    "work_orders",
    "faults",
    "alerts",
    "devices",
    "component_sensors",
    "component_telemetry",
    "ai_assistant",
    "reports",
    "analytics",
    "users",
    "settings",
    "audit_log",
}


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def get_json(base: str, path: str) -> tuple[int, dict | list]:
    request = Request(base + path, headers={"Accept": "application/json"})
    with urlopen(request, timeout=5) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def post_json(base: str, path: str, payload: dict, headers: dict | None = None) -> tuple[int, dict | list]:
    request_headers = {"Accept": "application/json", "Content-Type": "application/json"}
    request_headers.update(headers or {})
    request = Request(
        base + path,
        data=json.dumps(payload).encode("utf-8"),
        headers=request_headers,
        method="POST",
    )
    with urlopen(request, timeout=10) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def main() -> None:
    port = free_port()
    with tempfile.TemporaryDirectory(prefix="maintain-native-smoke-") as temporary:
        database = Path(temporary) / "native-smoke.sqlite3"
        env = os.environ.copy()
        env.update({
            "PORT": str(port),
            "DATABASE_URL": "sqlite:///" + database.as_posix(),
            "REQUIRE_AUTH": "false",
            "SEED_DEMO_DATA": "false",
            "ML_AUTO_FORECASTS": "false",
            "MAINTAIN_ML_SERVICE_URL": "",
            "MAINTAIN_PRETRAINED_FORECASTS": "false",
            "SUPABASE_URL": "",
            "SUPABASE_PUBLISHABLE_KEY": "",
            "SUPABASE_SECRET_KEY": "",
            "SUPABASE_JWT_SECRET": "",
            "VERCEL": "",
            "CORS_ORIGINS": "",
            "PYTHONUNBUFFERED": "1",
        })

        log_path = Path(temporary) / "backend.log"
        with log_path.open("w", encoding="utf-8") as log:
            process = subprocess.Popen(
                [sys.executable, "run_server.py"],
                cwd=BACKEND_DIR,
                env=env,
                stdout=log,
                stderr=subprocess.STDOUT,
            )
            base = f"http://127.0.0.1:{port}"
            try:
                deadline = time.monotonic() + 60
                last_error: Exception | None = None
                while time.monotonic() < deadline:
                    if process.poll() is not None:
                        raise RuntimeError(f"FastAPI exited during startup with code {process.returncode}")
                    try:
                        status_code, auth_status = get_json(base, "/api/auth/status")
                        if status_code == 200:
                            break
                    except (URLError, TimeoutError, OSError, ValueError) as error:
                        last_error = error
                    time.sleep(0.35)
                else:
                    raise RuntimeError(f"FastAPI health check timed out: {last_error}")

                assert auth_status.get("auth_required") is False, auth_status

                router_code, router_status = get_json(base, "/api/system/router-status")
                assert router_code == 200, router_status
                failed = router_status.get("failed", {})
                missing = sorted(REQUIRED_ROUTERS.intersection(failed))
                assert not missing, f"Core routers failed to import: {missing}; all failures: {failed}"

                me_code, local_user = get_json(base, "/api/auth/me")
                assert me_code == 200, local_user
                assert local_user.get("role") == "admin", local_user

                machines_code, machines = get_json(base, "/api/machines")
                assert machines_code == 200, machines
                assert isinstance(machines, list), machines

                create_code, created = post_json(base, "/api/machines", {
                    "machine_code": "NATIVE-SMOKE-001",
                    "name": "Native telemetry smoke machine",
                    "category": "industrial",
                    "location": "CI test bench",
                    "criticality": "medium",
                })
                assert create_code == 200, created
                machine_id = created["id"]

                enable_code, device = post_json(base, f"/api/devices/{machine_id}/enable", {})
                assert enable_code == 200, device
                device_key = device["device_key"]
                assert device_key, device

                ingest_code, ingest = post_json(
                    base,
                    "/api/devices/ingest",
                    {
                        "reading_type": "temperature",
                        "value": 42.5,
                        "unit": "°C",
                        "event_id": "native-smoke-reading-001",
                    },
                    {"X-Device-Key": device_key},
                )
                assert ingest_code == 200, ingest
                assert ingest.get("accepted") is True, ingest

                telemetry_code, telemetry = get_json(base, "/api/devices/telemetry/latest?ts=1")
                assert telemetry_code == 200, telemetry
                readings = telemetry.get("machines", [])
                latest = next(
                    (row for row in readings if row.get("machine_id") == machine_id
                     and row.get("reading_type") == "temperature"),
                    None,
                )
                assert latest is not None, telemetry
                assert latest["value"] == 42.5, latest
                assert latest["source"] == "sensor", latest

                assert database.is_file(), f"SQLite file was not created: {database}"
                print(
                    "Local backend smoke test passed: auth mode, core routers, local admin, "
                    "machine creation, authenticated sensor ingest, and SQLite-backed live telemetry polling."
                )
            except Exception:
                try:
                    print(log_path.read_text(encoding="utf-8", errors="replace")[-12000:], file=sys.stderr)
                except OSError:
                    pass
                raise
            finally:
                process.terminate()
                try:
                    process.wait(timeout=8)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)


if __name__ == "__main__":
    main()
