import json
import os
import re
from typing import Any

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MEMORY_DIR = os.path.join(BASE_DIR, "memory")
USER_MEMORY_FILE = os.path.join(MEMORY_DIR, "user_memory.json")

DEFAULT_USER_MEMORY = {
    "profile": {},
    "preferences": [],
    "notes": []
}


def ensure_memory_dir() -> None:
    os.makedirs(MEMORY_DIR, exist_ok=True)


def _safe_read_json(file_path: str, default: Any) -> Any:
    if not os.path.exists(file_path):
        return default

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            text = f.read().strip()
            if not text:
                return default
            return json.loads(text)
    except Exception:
        return default


def _safe_write_json(file_path: str, data: Any) -> None:
    ensure_memory_dir()
    temp_path = file_path + ".tmp"
    with open(temp_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.flush()
        os.fsync(f.fileno())
    os.replace(temp_path, file_path)


def load_user_memory() -> dict:
    ensure_memory_dir()
    memory = _safe_read_json(USER_MEMORY_FILE, DEFAULT_USER_MEMORY.copy())
    if not isinstance(memory, dict):
        memory = DEFAULT_USER_MEMORY.copy()
    for key in DEFAULT_USER_MEMORY:
        if key not in memory:
            memory[key] = DEFAULT_USER_MEMORY[key].copy() if isinstance(DEFAULT_USER_MEMORY[key], list) else DEFAULT_USER_MEMORY[key]
    return memory


def save_user_memory(memory: dict) -> None:
    if not isinstance(memory, dict):
        raise ValueError("User memory must be a dictionary.")
    _safe_write_json(USER_MEMORY_FILE, memory)


def normalize_memory_text(value: str) -> str:
    text = value.strip()
    if not text:
        return text
    text = re.sub(r"\s+", " ", text)
    return text.rstrip(".?!")


def _extract_preferred_name(message: str) -> str | None:
    lower = message.lower()
    patterns = [
        r"\bmy name is\s+([A-Za-z][A-Za-z'\- ]{0,40})",
        r"\bcall me\s+([A-Za-z][A-Za-z'\- ]{0,40})",
        r"\bremember me as\s+([A-Za-z][A-Za-z'\- ]{0,40})",
    ]
    for pattern in patterns:
        match = re.search(pattern, message, re.IGNORECASE)
        if match:
            name = normalize_memory_text(match.group(1))
            if name and len(name.split()) <= 4:
                return name
    return None


def _extract_preference(message: str) -> str | None:
    lower = message.lower()

    if "remember" in lower or "prefer" in lower or "i like" in lower or "i want" in lower:
        patterns = [
            r"remember that i prefer\s+(.{5,80})",
            r"remember that i like\s+(.{5,80})",
            r"i prefer\s+(.{5,80})",
            r"i like\s+(.{5,80})",
            r"i want\s+(.{5,80})",
        ]
        for pattern in patterns:
            match = re.search(pattern, message, re.IGNORECASE)
            if match:
                preference = normalize_memory_text(match.group(1))
                if preference:
                    preference = re.sub(r"^to\s+", "", preference, flags=re.IGNORECASE)
                    if len(preference) <= 80:
                        return preference
    return None


def _extract_generic_note(message: str) -> str | None:
    if "remember" not in message.lower():
        return None

    pattern = r"remember that\s+(.{5,120})"
    match = re.search(pattern, message, re.IGNORECASE)
    if match:
        note = normalize_memory_text(match.group(1))
        if note and len(note) <= 120:
            if not note.lower().startswith("my name is") and not note.lower().startswith("i prefer"):
                return note
    return None


def update_user_memory(user_message: str) -> dict | None:
    memory = load_user_memory()
    updated = False

    # Preferred name extraction is prioritized.
    preferred_name = _extract_preferred_name(user_message)
    if preferred_name:
        if memory.get("profile", {}).get("preferred_name") != preferred_name:
            memory.setdefault("profile", {})["preferred_name"] = preferred_name
            updated = True

    preference = _extract_preference(user_message)
    if preference:
        normalized = preference.capitalize()
        prefs = memory.setdefault("preferences", [])
        if normalized not in prefs:
            prefs.append(normalized)
            updated = True

    note = _extract_generic_note(user_message)
    if note:
        notes = memory.setdefault("notes", [])
        if note not in notes:
            notes.append(note)
            updated = True

    if updated:
        save_user_memory(memory)
        return memory

    return None


def build_memory_summary(memory: dict) -> str:
    if not memory or not isinstance(memory, dict):
        return ""

    parts = []
    profile = memory.get("profile", {})
    if profile:
        name = profile.get("preferred_name")
        if name:
            parts.append(f"The user's preferred name is {name}.")

    preferences = memory.get("preferences", [])
    if preferences:
        parts.append("The user prefers: " + "; ".join(preferences) + ".")

    notes = memory.get("notes", [])
    if notes:
        parts.append("Additional remembered notes: " + "; ".join(notes) + ".")

    return " ".join(parts)
