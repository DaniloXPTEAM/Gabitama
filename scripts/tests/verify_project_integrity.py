#!/usr/bin/env python3
"""
Verificação local executável do projeto Android NOVO_AGENTE_HB:
- Validação sintática XML (Manifest, strings, layouts, xml, drawables)
- Validação de referências R.* (strings, raw, layout, id, xml, mipmap)
- Validação JSON de voz congelada (`gabi_24l`, B2/C2) e catálogo (`gemini` primário)
- Integridade SHA-256 dos 1.324 assets Gabi copiados de `voz-latencia`
- Integridade do Gradle Wrapper 8.9 (gradlew, gradlew.bat, gradle-wrapper.jar Apache-2.0)
- Coerência de pacotes Kotlin, rotas NavRoutes, controlador único HB e imports válidos
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import sys
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID_DIR = ROOT / "android"
MAIN_DIR = ANDROID_DIR / "app" / "src" / "main"
KOTLIN_DIR = MAIN_DIR / "kotlin" / "com" / "novoagentehb" / "app"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def check_balanced_delimiters(text: str, filename: str) -> str | None:
    i = 0
    n = len(text)
    stack: list[str] = []
    in_line_comment = False
    in_block_comment = False
    in_string = False
    in_triple = False
    in_char = False

    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_triple:
            if text[i : i + 3] == '"""':
                in_triple = False
                i += 3
                continue
            i += 1
            continue
        if in_string:
            if ch == "\\":
                i += 2
                continue
            if ch == '"':
                in_string = False
            i += 1
            continue
        if in_char:
            if ch == "\\":
                i += 2
                continue
            if ch == "'":
                in_char = False
            i += 1
            continue

        if ch == "/" and nxt == "/":
            in_line_comment = True
            i += 2
            continue
        if ch == "/" and nxt == "*":
            in_block_comment = True
            i += 2
            continue
        if text[i : i + 3] == '"""':
            in_triple = True
            i += 3
            continue
        if ch == '"':
            in_string = True
            i += 1
            continue
        if ch == "'":
            in_char = True
            i += 1
            continue

        if ch in "({":
            stack.append(ch)
        elif ch in ")}":
            if not stack:
                return f"{filename}: unexpected closing '{ch}' near offset {i}"
            top = stack.pop()
            if (top == "(" and ch != ")") or (top == "{" and ch != "}"):
                return f"{filename}: mismatched '{top}' vs '{ch}' near offset {i}"
        i += 1

    if stack:
        return f"{filename}: unclosed delimiters {stack}"
    return None


class ProjectIntegrityTest(unittest.TestCase):
    def test_gradle_wrapper_8_9_is_complete_and_valid(self) -> None:
        gradlew = ANDROID_DIR / "gradlew"
        gradlew_bat = ANDROID_DIR / "gradlew.bat"
        jar = ANDROID_DIR / "gradle" / "wrapper" / "gradle-wrapper.jar"
        props = ANDROID_DIR / "gradle" / "wrapper" / "gradle-wrapper.properties"

        self.assertTrue(gradlew.exists(), "Missing gradlew")
        self.assertTrue(bool(gradlew.stat().st_mode & stat.S_IXUSR), "gradlew must have executable bit (+x)")
        self.assertTrue(gradlew_bat.exists(), "Missing gradlew.bat")
        self.assertTrue(jar.exists(), "Missing gradle-wrapper.jar")
        self.assertIn("gradle-8.9-bin.zip", props.read_text(encoding="utf-8"))

        self.assertTrue((ROOT / ".gitignore").exists(), "Missing root .gitignore")
        self.assertTrue((ANDROID_DIR / ".gitignore").exists(), "Missing android/.gitignore")
        wf_path = ROOT / ".github" / "workflows" / "android-debug.yml"
        self.assertTrue(wf_path.exists(), "Missing .github/workflows/android-debug.yml")
        wf = wf_path.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", wf)
        self.assertIn("java-version: '17'", wf)
        self.assertIn("testDebugUnitTest assembleDebug", wf)
        self.assertNotIn("android-actions/setup-android", wf)

        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
            self.assertIn("META-INF/LICENSE", names)
            self.assertIn("Apache License", z.read("META-INF/LICENSE").decode("utf-8", errors="replace"))
            self.assertTrue(any(n.endswith("GradleWrapperMain.class") for n in names))

    def test_all_xml_and_resource_references_are_valid(self) -> None:
        xml_files = sorted(MAIN_DIR.rglob("*.xml"))
        self.assertGreaterEqual(len(xml_files), 14)
        for xml_file in xml_files:
            ET.parse(xml_file)

        strings_xml = (MAIN_DIR / "res" / "values" / "strings.xml").read_text(encoding="utf-8")
        defined_strings = set(re.findall(r'<string\s+name="([^"]+)"', strings_xml))
        raw_stems = {p.stem for p in (MAIN_DIR / "res" / "raw").glob("*")} if (MAIN_DIR / "res" / "raw").exists() else set()
        layout_stems = {p.stem for p in (MAIN_DIR / "res" / "layout").glob("*.xml")}

        for kt in sorted((ANDROID_DIR / "app" / "src").rglob("*.kt")):
            text = kt.read_text(encoding="utf-8")
            for m in re.finditer(r"(?<!android\.)\bR\.string\.([a-zA-Z0-9_]+)", text):
                self.assertIn(m.group(1), defined_strings, f"Missing R.string.{m.group(1)} in {kt.name}")
            for m in re.finditer(r"(?<!android\.)\bR\.raw\.([a-zA-Z0-9_]+)", text):
                self.assertIn(m.group(1), raw_stems, f"Missing R.raw.{m.group(1)} in {kt.name}")
            for m in re.finditer(r"(?<!android\.)\bR\.layout\.([a-zA-Z0-9_]+)", text):
                self.assertIn(m.group(1), layout_stems, f"Missing R.layout.{m.group(1)} in {kt.name}")

    def test_manifest_isolation_and_components(self) -> None:
        manifest = (MAIN_DIR / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('android:allowBackup="false"', manifest)
        self.assertIn('android:scheme="novoagentehb"', manifest)
        self.assertIn("NotificationFeedService", manifest)
        self.assertIn("AssistantAccessibilityService", manifest)
        self.assertIn("GabiTileService", manifest)
        self.assertIn("ReminderAlarmReceiver", manifest)
        self.assertIn("BootReceiver", manifest)
        self.assertIn("HomeWidgetProvider", manifest)

        texture_layout = (MAIN_DIR / "res" / "layout" / "view_texture_player.xml").read_text(encoding="utf-8")
        self.assertIn('app:surface_type="texture_view"', texture_layout)
        self.assertIn('android:clickable="false"', texture_layout)
        self.assertIn('android:focusable="false"', texture_layout)

        gradle = (ANDROID_DIR / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationId = "com.novoagentehb.app"', gradle)
        self.assertIn("PROVISIONAL_INTERNAL_ID", gradle)
        self.assertIn("kotlin.kapt", gradle)

    def test_configs_freeze_gabi_voice_and_gemini_primary(self) -> None:
        vp = json.loads((MAIN_DIR / "assets" / "config" / "voice_profiles.json").read_text(encoding="utf-8"))
        self.assertEqual("gabi_24l", vp["frozen_identity"])
        self.assertEqual("portuguese_24l", vp["pocket_tts_model"])
        self.assertEqual(24000, vp["sample_rate_hz"])
        self.assertFalse(vp["fallback_policy"]["allow_silent_swap_to_system_tts"])
        self.assertEqual("REQUIRES_ACCOUNT_DISCOVERY_ON_DEVICE", vp["gemini_live"]["model_verification_status"])
        for req_prof in ("B2_base", "B2_pausas", "C2_base", "C2_pausas"):
            self.assertIn(req_prof, vp["requested_profiles"])

        cat = json.loads((MAIN_DIR / "assets" / "config" / "providers_catalog.json").read_text(encoding="utf-8"))
        self.assertEqual("gemini", cat["primary_active_stack"])
        ids = {p["id"] for p in cat["providers"]}
        self.assertEqual({"gemini", "groq", "openrouter", "deepseek", "anthropic"}, ids)

    def test_all_1324_approved_assets_exist_and_match_sha256(self) -> None:
        manifest_path = ROOT / "evidence" / "APPROVED_ASSETS_SHA256.txt"
        self.assertTrue(manifest_path.exists())
        lines = [ln.strip() for ln in manifest_path.read_text(encoding="utf-8").splitlines() if ln.strip()]
        self.assertEqual(1324, len(lines))
        for line in lines:
            expected_hash, rel_asset = line.split(None, 1)
            asset_path = ROOT / rel_asset
            self.assertTrue(asset_path.exists(), f"Missing approved asset: {rel_asset}")
            self.assertEqual(expected_hash, sha256_file(asset_path), f"Hash mismatch for {rel_asset}")

    def test_kotlin_packages_delimiters_routes_hb_and_runtime_contracts(self) -> None:
        kt_files = sorted((ANDROID_DIR / "app" / "src").rglob("*.kt"))
        self.assertGreaterEqual(len(kt_files), 62)

        for kt in kt_files:
            text = kt.read_text(encoding="utf-8")
            if "/src/main/kotlin/" in kt.as_posix():
                rel_pkg = kt.relative_to(MAIN_DIR / "kotlin").parent.as_posix().replace("/", ".")
            else:
                rel_pkg = kt.relative_to(ANDROID_DIR / "app" / "src" / "test" / "kotlin").parent.as_posix().replace("/", ".")
            self.assertRegex(text, rf"^package\s+{re.escape(rel_pkg)}\b", f"Package mismatch in {kt}")

            err = check_balanced_delimiters(text, kt.name)
            self.assertIsNone(err, err or "")

            self.assertNotIn("io.coil", text, f"Undeclared Coil import in {kt}")
            self.assertNotIn("com.javis.assistant", text, f"Legacy package leak in {kt}")
            self.assertNotIn("org.json.", text, f"org.json JVM-test hazard in {kt}")

        # Verify R1.4.1 build & design system continuity
        toml_text = (ANDROID_DIR / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
        self.assertNotIn("accompanist", toml_text, "Unused accompanist dependency must stay removed")
        app_gradle_text = (ANDROID_DIR / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("unitTests.isReturnDefaultValues = true", app_gradle_text)
        self.assertIn("correctErrorTypes = true", app_gradle_text)
        self.assertIn("versionCode = 7", app_gradle_text)
        self.assertIn("0.2.5-r1.4.1", app_gradle_text)

        # Verify HB shared controller, R1.4.1 catalog, motion truth, background compatibility & Stage Continuity
        self.assertTrue((KOTLIN_DIR / "gabi" / "HBAnimationCatalog.kt").exists())
        self.assertTrue((KOTLIN_DIR / "gabi" / "HBCharacterModel.kt").exists())
        self.assertTrue((KOTLIN_DIR / "gabi" / "HBCharacterController.kt").exists())
        self.assertTrue((KOTLIN_DIR / "bridges" / "GabiTileService.kt").exists())
        self.assertTrue((KOTLIN_DIR / "agent" / "skills" / "McpJsonRpcClient.kt").exists())
        self.assertTrue((KOTLIN_DIR / "agent" / "skills" / "LocalSkillExecutor.kt").exists())

        hb_model_kt = (KOTLIN_DIR / "gabi" / "HBCharacterModel.kt").read_text(encoding="utf-8")
        self.assertIn("enum class HBMotionTruth", hb_model_kt)
        self.assertIn("enum class RoomBackgroundCompatibility", hb_model_kt)
        self.assertIn("catalogStateKey", hb_model_kt)
        self.assertIn("isStagePreserved", hb_model_kt)
        self.assertIn("hasDedicatedStateAnimation", hb_model_kt)

        theme_kt = (KOTLIN_DIR / "design" / "Theme.kt").read_text(encoding="utf-8")
        self.assertIn("LocalHBStage", theme_kt)
        self.assertIn("CopperAccent", theme_kt)
        self.assertIn("0xFF120F11", theme_kt)
        components_kt = (KOTLIN_DIR / "design" / "Components.kt").read_text(encoding="utf-8")
        self.assertIn("GabiAtmosphericBackdrop", components_kt)
        self.assertIn("GabiTopBar", components_kt)
        self.assertIn("CharacterBibleDialog", components_kt)
        bottom_bar_kt = (KOTLIN_DIR / "design" / "GabiBottomBar.kt").read_text(encoding="utf-8")
        self.assertIn("LocalHBStage", bottom_bar_kt)
        self.assertIn("GabiFaceAvatar", bottom_bar_kt)
        home_kt = (KOTLIN_DIR / "ui" / "home" / "HomeScreen.kt").read_text(encoding="utf-8")
        self.assertIn("preferCircularStageMedallion = true", home_kt)
        self.assertIn("theme/gabitama_brand_crest.png", home_kt)
        self.assertIn("theme/gabitama_stage_halo.png", home_kt)
        self.assertIn("STAGE_PRESERVED_IDLE_ONLY", home_kt)

        catalog_text = (KOTLIN_DIR / "gabi" / "HBAnimationCatalog.kt").read_text(encoding="utf-8")
        for state_id in (
            "IDLE", "LISTENING", "LISTENING_INTENT", "THINKING", "COMPUTER_THINKING",
            "TALKING", "WORKING", "EATING", "EATING_REACTION", "READING", "SLEEPING",
            "MUSIC_REACTION", "TAP_REACTION", "SUCCESS", "ERROR", "CANCELLED", "OUTFIT_CHANGE",
            "ROOM_INTRO_MP4", "ROOM_BG_LOOP_MP4",
            "STAGE_PRE_ADOLESCENT_IDLE", "STAGE_ADOLESCENT_IDLE", "SPRITESHEET_OPTIONAL",
            "BIBLE_CHARACTER_SHEET"
        ):
            self.assertIn(f"HBCatalogStateId.{state_id}", catalog_text)

        avatar_views = (KOTLIN_DIR / "gabi" / "GabiAvatarViews.kt").read_text(encoding="utf-8")
        self.assertIn("R.layout.view_texture_player", avatar_views)
        self.assertIn("rememberReduceMotionEnabled", avatar_views)
        self.assertIn("Lifecycle.Event.ON_PAUSE", avatar_views)
        self.assertIn("player.release()", avatar_views)
        self.assertIn("StageLoopVideo", avatar_views)
        self.assertIn("GabiFaceAvatar", avatar_views)
        self.assertIn("preferCircularStageMedallion", avatar_views)
        self.assertIn("GabiParticleOrb", avatar_views)
        self.assertIn("visualSource.stage.avatarCirclePngPath", avatar_views)

        quarto = (KOTLIN_DIR / "ui" / "habitat" / "QuartoGabiScreen.kt").read_text(encoding="utf-8")
        self.assertIn("RoomBackgroundLayer", quarto)
        self.assertIn("RoomBackgroundCompatibility", quarto)
        self.assertIn("OPAQUE_FULL_SCENE_BLOCKS_CUSTOM_BG", quarto)
        self.assertIn("HBStageCharacter", quarto)
        self.assertIn("WardrobeDialog", quarto)
        self.assertIn("StageSelectorDialog", quarto)
        self.assertIn("CharacterBibleDialog", quarto)
        self.assertIn("StageLoopVideo", quarto)
        self.assertIn("Arte pendente", quarto)
        self.assertIn("RoomIntroVideo", quarto)
        self.assertIn("RoomLoopBackgroundVideo", quarto)
        self.assertIn("rememberReduceMotionEnabled", quarto)
        self.assertIn("SpotifySpeakerBadge", quarto)
        self.assertNotIn("GabiTokens.NightCard", quarto)
        self.assertNotIn("GabiTokens.CoralAccent", quarto)

        bridge_text = (KOTLIN_DIR / "bridges" / "AndroidDeviceBridge.kt").read_text(encoding="utf-8")
        self.assertIn("routeAudioToBluetoothSco", bridge_text)
        self.assertIn("bondedBluetoothDevices", bridge_text)
        self.assertIn("set_reminder", bridge_text)
        self.assertIn('"subject" to cleanedSubject, "trigger_at" to triggerAt', bridge_text)
        self.assertIn('"description" to "Alarme da Gabi"', bridge_text)
        self.assertIn("suspend fun sendWhatsAppMessage", bridge_text)

        saf_text = (KOTLIN_DIR / "bridges" / "SafLibraryManager.kt").read_text(encoding="utf-8")
        self.assertIn("createSafDirectory", saf_text)
        self.assertIn("deleteSafEntry", saf_text)
        self.assertIn("renameSafEntry", saf_text)
        self.assertIn("shareWorkspaceFile", saf_text)

        voice = (KOTLIN_DIR / "gabi" / "VoiceEngineCoordinator.kt").read_text(encoding="utf-8")
        self.assertIn("geminiLive.connect", voice)
        self.assertIn("AudioRecord", voice)
        self.assertIn("sendPcm16AudioChunk", voice)
        self.assertIn("GeminiLiveSessionReducer", voice)
        self.assertIn("GeminiLiveTurnAggregator", voice)
        self.assertIn("activeVoiceDisclosure", voice)
        self.assertIn("Channel.UNLIMITED", voice)
        self.assertIn("releasePcmTrack()", voice)
        self.assertIn("geminiLive.sendTextTurn(clean)", voice)

        self.assertIn("Lifecycle.Event.ON_RESUME", avatar_views)
        self.assertIn("SystemClock.uptimeMillis()", avatar_views)
        self.assertIn(".imePadding()", home_kt)

        skill_exec = (KOTLIN_DIR / "agent" / "skills" / "LocalSkillExecutor.kt").read_text(encoding="utf-8")
        self.assertIn("ARTIFACT_PREPARED", skill_exec)
        self.assertIn("TASK_EXECUTED", skill_exec)
        self.assertIn("SCOPE_WORKSPACE_WRITE_VERIFY", skill_exec)

        mcp_client = (KOTLIN_DIR / "agent" / "skills" / "McpJsonRpcClient.kt").read_text(encoding="utf-8")
        self.assertIn("notifications/initialized", mcp_client)
        self.assertIn("Mcp-Session-Id", mcp_client)
        self.assertIn("followRedirects(false)", mcp_client)
        self.assertIn("allowLocalLan", mcp_client)

        cap_reg = (KOTLIN_DIR / "agent" / "tools" / "CapabilityRegistry.kt").read_text(encoding="utf-8")
        self.assertIn("NOTIFICATION_PREVIEW_ONLY", cap_reg)
        self.assertIn("HANDOFF_TO_USER", cap_reg)

        notif_svc = (KOTLIN_DIR / "bridges" / "NotificationFeedService.kt").read_text(encoding="utf-8")
        self.assertIn("OnActiveSessionsChangedListener", notif_svc)
        self.assertIn("fun refreshActiveMediaSessions(context: Context)", notif_svc)

        nav_text = (KOTLIN_DIR / "app" / "navigation" / "NavRoutes.kt").read_text(encoding="utf-8")
        for screen_cls in (
            "Screen.Home.route", "Screen.Chat.route", "Screen.Habitat.route",
            "Screen.Feed.route", "Screen.Goals.route", "Screen.Library.route",
            "Screen.Memory.route", "Screen.Computer.route", "Screen.Settings.route",
            "Screen.Permissions.route"
        ):
            self.assertIn(screen_cls, nav_text)
        self.assertIn("QuartoGabiScreen", nav_text)

        perm_text = (KOTLIN_DIR / "ui" / "permissions" / "PermissionsScreen.kt").read_text(encoding="utf-8")
        self.assertIn("safeDrawingPadding()", perm_text)

    def test_hb_layer_alpha_channels_and_webp_containers(self) -> None:
        import struct
        assets_dir = MAIN_DIR / "assets"
        for png_name in ("gabi-idle.png", "gabi-ler.png", "gabi-computador.png", "gabi-dormir.png", "gabi-comer.png"):
            raw = (assets_dir / png_name).read_bytes()
            self.assertEqual(b"\x89PNG\r\n\x1a\n", raw[:8], f"Invalid PNG header: {png_name}")
            w, h, bit_depth, color_type = struct.unpack(">IIBB", raw[16:26])
            self.assertEqual((768, 1024, 8, 6), (w, h, bit_depth, color_type), f"Expected RGBA 768x1024 for {png_name}")

        for folder in ("idle", "listening", "talking", "thinking"):
            webp_raw = (assets_dir / "avatar" / folder / "f001.webp").read_bytes()
            self.assertEqual(b"RIFF", webp_raw[:4])
            self.assertEqual(b"WEBP", webp_raw[8:12])
            self.assertIn(b"ALPH", webp_raw[:128], f"Avatar frame {folder}/f001.webp must keep ALPH transparency chunk")

    def test_r14_drive_avatar_originals_and_integrated_assets(self) -> None:
        import struct
        orig_manifest = ROOT / "evidence" / "DRIVE_AVATAR_ORIGINALS_SHA256.txt"
        self.assertTrue(orig_manifest.exists(), "Missing DRIVE_AVATAR_ORIGINALS_SHA256.txt")
        entries = [
            ln.strip()
            for ln in orig_manifest.read_text(encoding="utf-8").splitlines()
            if ln.strip() and not ln.strip().startswith("#")
        ]
        self.assertEqual(23, len(entries))
        for ln in entries:
            parts = ln.split()
            exp_sha, _, exp_size, _, rel_path = parts[:5]
            if rel_path.startswith("EXTERNAL_"):
                continue
            p = ROOT / rel_path
            self.assertTrue(p.exists(), f"Missing integrated Drive original: {rel_path}")
            self.assertEqual(int(exp_size), p.stat().st_size, f"Size mismatch: {rel_path}")
            self.assertEqual(exp_sha, sha256_file(p), f"SHA-256 mismatch: {rel_path}")

        assets_dir = MAIN_DIR / "assets"
        for new_anim in ("scene_listening_intent", "scene_computador_pensando"):
            d = assets_dir / "anim" / new_anim
            pngs = sorted(d.glob("*.png"))
            self.assertEqual(96, len(pngs), f"Expected 96 RGBA frames in {new_anim}")
            raw = pngs[0].read_bytes()
            self.assertEqual(b"\x89PNG\r\n\x1a\n", raw[:8])
            w, h, bit_depth, color_type = struct.unpack(">IIBB", raw[16:26])
            self.assertEqual((320, 569, 8, 6), (w, h, bit_depth, color_type), f"Expected RGBA 320x569 in {new_anim}")

        for stage_id in ("young_child", "pre_adolescent", "adolescent"):
            sd = assets_dir / "hb-stages" / stage_id
            self.assertTrue((sd / "source.json").exists(), f"Missing source.json for {stage_id}")
            self.assertTrue((sd / "preview.png").exists(), f"Missing preview.png for {stage_id}")
            self.assertTrue((sd / "avatar_circle.png").exists(), f"Missing avatar_circle.png for {stage_id}")
            json.loads((sd / "source.json").read_text(encoding="utf-8"))
            if stage_id != "young_child":
                self.assertTrue((sd / "idle.mp4").exists(), f"Missing idle.mp4 for {stage_id}")

        for theme_png in (
            "theme/gabitama_home_backdrop.png",
            "theme/gabitama_stage_halo.png",
            "theme/gabitama_card_surface.png",
            "theme/gabitama_brand_crest.png",
            "theme/gabitama_empty_scene.png",
            "bible.png",
            "gabi-spritesheet.png",
            "gabi-talking.png",
        ):
            self.assertTrue((assets_dir / theme_png).exists(), f"Missing R1.4 visual asset: {theme_png}")


if __name__ == "__main__":
    result = unittest.main(exit=False)
    sys.exit(0 if result.result.wasSuccessful() else 1)
