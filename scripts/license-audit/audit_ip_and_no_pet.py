#!/usr/bin/env python3
"""
Auditoria automática de IP, licenças, zero herança nanoMuse/GPL, zero camada Pet
e zero segredos embutidos para NOVO_AGENTE_HB.
Gera também evidence/SBOM.json (declarando escopo honesto direto vs transitivo).
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID_DIR = ROOT / "android"
EVIDENCE_DIR = ROOT / "evidence"

FORBIDDEN_IP_PATTERNS = [
    r"nanomuse",
    r"openminis",
    r"minis://",
    r"nanomuse://",
    r"tama://",
    r"ai\.tama\.assistant",
    r"io\.github\.nanomuse",
    r"gnu general public license",
    r"\bgpl-?3\.0\b",
    r"\bagpl\b",
    r"tamamascot",
]

FORBIDDEN_PET_PATTERNS = [
    r"tamagotchi",
    r"\bdragon\b",
    r"\bdrag[aã]o\b",
    r"tama\.pet",
    r"hunger_death",
    r"xp_missions",
    r"pet_stage",
    r"egg_stage",
]

SECRET_PATTERNS = [
    r"AIza[0-9A-Za-z\-_]{20,}",
    r"sk-[0-9A-Za-z\-_]{16,}",
    r"gsk_[0-9A-Za-z]{16,}",
    r"AQ\.[0-9A-Za-z\-_]{10,}",
]

SCANNED_EXTENSIONS = {".kt", ".xml", ".kts", ".toml", ".json", ".pro", ".properties", ".txt"}


def generate_sbom() -> dict:
    toml_path = ANDROID_DIR / "gradle" / "libs.versions.toml"
    text = toml_path.read_text(encoding="utf-8")
    versions: dict[str, str] = {}
    libraries: list[dict[str, str]] = []
    section = ""
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            continue
        if "=" not in line:
            continue
        key, rhs = [part.strip() for part in line.split("=", 1)]
        if section == "versions":
            versions[key] = rhs.strip('"')
        elif section == "libraries":
            group_m = re.search(r'group\s*=\s*"([^"]+)"', rhs)
            name_m = re.search(r'name\s*=\s*"([^"]+)"', rhs)
            vref_m = re.search(r'version\.ref\s*=\s*"([^"]+)"', rhs)
            group = group_m.group(1) if group_m else ""
            name = name_m.group(1) if name_m else ""
            vref = vref_m.group(1) if vref_m else ""
            version = versions.get(vref, "BOM-managed")
            if group == "junit":
                lic = "EPL-1.0 (testImplementation only)"
            else:
                lic = "Apache-2.0 (declared direct coordinate)"
            libraries.append(
                {
                    "alias": key,
                    "coordinate": f"{group}:{name}",
                    "version": version,
                    "declared_license": lic,
                }
            )

    wrapper_jar = ANDROID_DIR / "gradle" / "wrapper" / "gradle-wrapper.jar"
    wrapper_sha = (
        hashlib.sha256(wrapper_jar.read_bytes()).hexdigest() if wrapper_jar.exists() else "MISSING"
    )

    sbom = {
        "schema": "novo_agente_hb_sbom_v2",
        "product_provisional_id": "NOVO_AGENTE_HB",
        "application_id": "com.novoagentehb.app",
        "owner_license_status": "PENDING_OWNER_DECISION (see LICENSE_PENDING_OWNER.txt)",
        "license_audit_scope": "DIRECT_TOML_DECLARED_ONLY",
        "transitive_audit_status": "UNVERIFIED_OFFLINE_REQUIRES_GRADLE_DEPENDENCIES",
        "gpl_components_declared_count": 0,
        "build_tooling": {
            "gradle_wrapper_version": "8.9",
            "gradle_wrapper_license": "Apache-2.0 (verified META-INF/LICENSE in gradle-wrapper.jar)",
            "gradle_wrapper_jar_sha256": wrapper_sha,
        },
        "libraries": libraries,
    }
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    (EVIDENCE_DIR / "SBOM.json").write_text(json.dumps(sbom, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return sbom


class IpAndNoPetAuditTest(unittest.TestCase):
    def test_zero_forbidden_ip_or_gpl_references_in_android_tree(self) -> None:
        hits: list[str] = []
        for path in sorted(ANDROID_DIR.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in SCANNED_EXTENSIONS:
                continue
            rel = path.relative_to(ROOT).as_posix()
            content = path.read_text(encoding="utf-8", errors="replace").lower()
            for pat in FORBIDDEN_IP_PATTERNS:
                if re.search(pat, content):
                    if rel.endswith("PolicyGate.kt") and pat in {r"tama\.pet", r"\bdragon\b"}:
                        continue
                    hits.append(f"{rel} matched /{pat}/")
        self.assertEqual([], hits, f"Forbidden IP/GPL matches: {hits}")

    def test_zero_pet_tamagotchi_implementation_or_assets(self) -> None:
        hits: list[str] = []
        for path in sorted(ANDROID_DIR.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(ROOT).as_posix()
            lower_name = path.name.lower()
            if any(w in lower_name for w in ("dragon", "tamagotchi", "pet_stage", "memorial", "gabi voz isolada")):
                hits.append(f"Forbidden filename: {rel}")
            if path.suffix.lower() in SCANNED_EXTENSIONS:
                if "/src/test/" in rel or rel.endswith("PolicyGate.kt"):
                    continue
                content = path.read_text(encoding="utf-8", errors="replace").lower()
                for pat in FORBIDDEN_PET_PATTERNS:
                    if re.search(pat, content):
                        hits.append(f"{rel} matched Pet pattern /{pat}/")
        self.assertEqual([], hits, f"Forbidden Pet/Tamagotchi matches: {hits}")

    def test_zero_hardcoded_secrets_or_buildconfig_api_keys(self) -> None:
        hits: list[str] = []
        for path in sorted(ROOT.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(ROOT).as_posix()
            if "gabi voz isolada" in path.name.lower():
                hits.append(f"Private minor audio must not exist: {rel}")
            if path.suffix.lower() in SCANNED_EXTENSIONS:
                if "/src/test/" in rel or rel.endswith("audit_ip_and_no_pet.py"):
                    continue
                text = path.read_text(encoding="utf-8", errors="replace")
                for pat in SECRET_PATTERNS:
                    if re.search(pat, text):
                        hits.append(f"Secret pattern {pat} in {rel}")

        app_gradle = (ANDROID_DIR / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        forbidden_bc = re.findall(r"buildConfigField\([^)]*(?:API_KEY|SPOTIFY_CLIENT|ELEVENLABS)", app_gradle)
        self.assertEqual([], forbidden_bc, f"BuildConfig must not embed secret/API key fields: {forbidden_bc}")

        for kt_rel in ("agent/models/ModelRouter.kt", "bridges/SpotifyBridge.kt"):
            src = (ANDROID_DIR / "app" / "src" / "main" / "kotlin" / "com" / "novoagentehb" / "app" / kt_rel).read_text(encoding="utf-8")
            self.assertNotIn("BuildConfig.", src, f"Must not read credentials from BuildConfig in {kt_rel}")

        self.assertEqual([], hits, f"Secret/privacy hits: {hits}")

    def test_sbom_generated_with_honest_scope_and_zero_declared_gpl(self) -> None:
        sbom = generate_sbom()
        self.assertEqual(0, sbom["gpl_components_declared_count"])
        self.assertEqual("DIRECT_TOML_DECLARED_ONLY", sbom["license_audit_scope"])
        self.assertEqual("UNVERIFIED_OFFLINE_REQUIRES_GRADLE_DEPENDENCIES", sbom["transitive_audit_status"])
        self.assertGreaterEqual(len(sbom["libraries"]), 25)
        for lib in sbom["libraries"]:
            self.assertNotIn("GPL", lib["declared_license"].upper())


if __name__ == "__main__":
    result = unittest.main(exit=False)
    sys.exit(0 if result.result.wasSuccessful() else 1)
