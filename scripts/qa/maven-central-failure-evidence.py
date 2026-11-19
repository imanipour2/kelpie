#!/usr/bin/env python3
"""Bounded, metadata-only probes for the observed Android Maven failures.

Run only after a CI build failure. These public requests do not resolve Gradle
dependencies, modify its cache, or establish why an earlier request failed.
Requires Python 3.10+ and curl 8.4+ (streaming --max-filesize enforcement).
"""

from __future__ import annotations

import concurrent.futures
import hashlib
import json
import pathlib
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

COORDINATES = (
    "org.jetbrains.kotlin:kotlin-stdlib:1.9.22",
    "com.squareup.okhttp3:okhttp:4.12.0",
    "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0",
    "io.ktor:ktor-server-netty:2.3.8",
    "io.ktor:ktor-server-content-negotiation:2.3.8",
    "io.ktor:ktor-serialization-kotlinx-json:2.3.8",
    "io.ktor:ktor-server-cors:2.3.8",
    "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3",
    "org.jspecify:jspecify:1.0.0",
    "com.google.guava:listenablefuture:1.0",
    "com.google.crypto.tink:tink-android:1.8.0",
    "org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1",
)
MAX_WORKERS = 3
MAX_BODY_BYTES = 65536
REQUEST_SECONDS = 15
PROCESS_SECONDS = 20


def pom_matches(body: bytes, coordinate: str) -> dict[str, bool]:
    result = dict(xml_parsed=False, group_matches=False, artifact_matches=False, version_matches=False)
    try:
        xml = body.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        return result
    # Reject NUL so BOM-less UTF-16 cannot reach XML encoding autodetection.
    # POMs need no DTD; scan decoded text before any entity expansion.
    if "\x00" in xml or "<!DOCTYPE" in xml.upper() or "<!ENTITY" in xml.upper():
        return result
    try:
        root = ET.fromstring(xml)
    except (ET.ParseError, ValueError, LookupError):
        return result
    if root.tag not in {"project", "{http://maven.apache.org/POM/4.0.0}project"}:
        return result
    namespace = "{http://maven.apache.org/POM/4.0.0}" if root.tag.startswith("{") else ""

    def value(name: str) -> str | None:
        text = root.findtext(namespace + name)
        if text is None and name != "artifactId":
            text = root.findtext(namespace + "parent/" + namespace + name)
        return text.strip() if text is not None else None

    group, artifact, version = coordinate.split(":")
    return dict(
        xml_parsed=True,
        group_matches=value("groupId") == group,
        artifact_matches=value("artifactId") == artifact,
        version_matches=value("version") == version,
    )


def probe(coordinate: str, method: str) -> dict:
    group, artifact, version = coordinate.split(":")
    url = (
        "https://repo.maven.apache.org/maven2/"
        f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    )
    evidence: dict = {"coordinate": coordinate, "method": method}
    with tempfile.TemporaryDirectory(prefix="kelpie-maven-probe-") as directory:
        body_path = pathlib.Path(directory) / "response"
        command = [
            "curl", "--disable", "--silent", "--proto", "=https",
            "--connect-timeout", "5", "--max-time", str(REQUEST_SECONDS),
            "--max-filesize", str(MAX_BODY_BYTES), "--max-redirs", "0",
            "--output", str(body_path) if method == "GET" else "/dev/null",
            "--write-out", "%{http_code}\t%{time_total}",
        ]
        # No redirect following, curlrc, retries, custom credentials or TLS bypass.
        if method == "HEAD":
            command.append("--head")
        command.append(url)
        try:
            response = subprocess.run(
                command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                timeout=PROCESS_SECONDS, check=False,
            )
        except subprocess.TimeoutExpired:
            return {**evidence, "error": "PROCESS_DEADLINE"}
        except OSError:
            return {**evidence, "error": "CURL_UNAVAILABLE"}
        evidence["curl_exit"] = response.returncode
        metadata = re.fullmatch(rb"([0-9]{3})\t([0-9]+\.[0-9]+)", response.stdout)
        if metadata:
            evidence["http_status"] = int(metadata.group(1))
            evidence["duration_seconds"] = float(metadata.group(2))
        else:
            evidence["metadata_valid"] = False
        if method == "GET":
            with body_path.open("rb") if body_path.exists() else open("/dev/null", "rb") as stream:
                body = stream.read(MAX_BODY_BYTES + 1)
            complete = response.returncode == 0 and len(body) <= MAX_BODY_BYTES
            evidence.update(
                body_bytes=len(body), body_complete=complete,
                body_sha256=hashlib.sha256(body).hexdigest(),
            )
            if complete:
                evidence.update(pom_matches(body, coordinate))
    return evidence


def probe_coordinate(coordinate: str) -> list[dict]:
    return [probe(coordinate, method) for method in ("HEAD", "GET")]


def main() -> int:
    try:
        response = subprocess.run(
            ["curl", "--disable", "--version"], stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL, timeout=5, check=False,
        )
        version = re.match(rb"curl ([0-9]+)\.([0-9]+)\.([0-9]+)\b", response.stdout)
        supported = response.returncode == 0 and version is not None and tuple(
            int(part) for part in version.groups()
        ) >= (8, 4, 0)
    except (OSError, subprocess.TimeoutExpired):
        supported = False
    if not supported:
        print(json.dumps({"error": "CURL_8_4_OR_NEWER_REQUIRED"}))
        return 1
    print(json.dumps({
        "purpose": "post-failure observation; original failure is unchanged",
        "coordinates": len(COORDINATES), "max_concurrency": MAX_WORKERS,
        "max_body_bytes": MAX_BODY_BYTES, "request_seconds": REQUEST_SECONDS,
        "process_seconds": PROCESS_SECONDS,
    }), flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        for records in executor.map(probe_coordinate, COORDINATES):
            for record in records:
                print(json.dumps(record, sort_keys=True), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
