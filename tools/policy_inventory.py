#!/usr/bin/env python3
"""Generate the Permission Policy V2 Phase 0 endpoint/UI inventory.

The inventory is intentionally source-based: it gives reviewers a stable,
machine-readable baseline before individual controllers are moved to the
central policy engine.  It does not make authorization decisions.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


HTTP_ANNOTATIONS = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "PatchMapping": "PATCH",
    "DeleteMapping": "DELETE",
    "RequestMapping": "REQUEST",
}
STRING_RE = re.compile(r'"([^"\n]*)"')
ACTION_RE = re.compile(r"canAction\s*\(\s*['\"]([A-Za-z0-9_]+)['\"]")
MODULE_RE = re.compile(r"can\s*\(\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]+)['\"]")


def annotation_value(annotation: str) -> str:
    match = STRING_RE.search(annotation)
    return match.group(1) if match else ""


def join_path(parent: str, child: str) -> str:
    parent = parent or ""
    child = child or ""
    if not parent:
        return child or "/"
    if not child or child == "/":
        return parent
    return f"{parent.rstrip('/')}/{child.lstrip('/')}"


def infer_action(http_method: str, path: str, guard: str, modules: list[dict]) -> str:
    explicit = ACTION_RE.findall(guard)
    if explicit:
        return explicit[0]

    lower = path.lower()
    write = http_method in {"POST", "PUT", "PATCH", "DELETE"}
    if "/students" in lower or "/student-" in lower:
        if "transfer" in lower:
            return "ENROLLMENT_TRANSFER"
        if "withdraw" in lower:
            return "ENROLLMENT_WITHDRAW"
        if "guardian" in lower:
            return "GUARDIAN_LINK_MANAGE" if write else "GUARDIAN_VIEW"
        if "import" in lower or "registration" in lower:
            return "STUDENT_IMPORT" if "import" in lower else "STUDENT_PROFILE_CREATE"
        return "STUDENT_PROFILE_EDIT" if write else "STUDENT_PROFILE_VIEW"
    if "/attendance" in lower or "/presence" in lower:
        return "ATTENDANCE_MARK" if write else "ATTENDANCE_ROSTER_VIEW"
    if "/timetable" in lower:
        return "TIMETABLE_OVERRIDE" if write else "TIMETABLE_CLASS_SCHEDULE_VIEW"
    if "/settings" in lower or "/setup" in lower:
        return "SETTINGS_WRITE_UNMAPPED" if write else "SETTINGS_READ_UNMAPPED"
    if modules:
        module = modules[0]["module"].upper().replace("-", "_")
        return f"{module}_{'WRITE' if write else 'READ'}_UNMAPPED"
    return "UNPROTECTED_ENDPOINT"


def mapping_status(controller: str, path: str, guard: str,
                   actions: list[str], modules: list[dict]) -> str:
    if actions:
        return "explicit-action"
    if modules:
        return "module-guard"
    if "isParent()" in guard:
        return "role-guard"
    if "isAuthenticated()" in guard:
        return "authentication-guard"
    if controller == "DeviceController":
        return "machine-auth"
    if guard == "" and ("/public/" in path or path.startswith("/api/auth/")
                         or "/verify/" in path):
        return "public"
    return "unprotected"


def collect_annotation(lines: list[str], index: int) -> tuple[str, int]:
    """Collect an annotation that may span multiple source lines."""
    value = lines[index].strip()
    while value.count("(") > value.count(")") and index + 1 < len(lines):
        index += 1
        value += " " + lines[index].strip()
    return value, index


def inline_annotation(source: str, name: str) -> str:
    """Return one annotation from a line, including same-line annotations."""
    start = source.find("@" + name)
    if start < 0:
        return ""
    name_end = start + len(name) + 1
    open_index = name_end
    while open_index < len(source) and source[open_index].isspace():
        open_index += 1
    if open_index >= len(source) or source[open_index] != "(":
        return source[start:name_end].strip()
    depth = 0
    quote = ""
    escaped = False
    for index in range(open_index, len(source)):
        char = source[index]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = ""
            continue
        if char in {'\"', "'"}:
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return source[start:index + 1].strip()
    return source[start:].strip()


def resolve_guard(annotation: str, constants: dict[str, str]) -> str:
    """Resolve @PreAuthorize(NAME) constants used by controller source."""
    if not annotation:
        return ""
    match = re.fullmatch(r"@PreAuthorize\(\s*([A-Za-z_]\w*)\s*\)", annotation)
    return constants.get(match.group(1), annotation) if match else annotation


def parse_controller(path: Path, repo: Path) -> list[dict]:
    lines = path.read_text(encoding="utf-8").splitlines()
    guard_constants: dict[str, str] = {}
    constant_re = re.compile(r"\bstatic\s+final\s+String\s+(\w+)\s*=\s*(.*?);\s*$")
    for raw in lines:
        match = constant_re.search(raw.strip())
        if match:
            literals = STRING_RE.findall(match.group(2))
            if literals:
                guard_constants[match.group(1)] = "".join(literals)
    class_path = ""
    class_name = path.stem
    package = ""
    pending_guard = ""
    pending_mapping: tuple[str, str] | None = None
    rows: list[dict] = []
    for index, raw in enumerate(lines):
        line = raw.strip()
        if line.startswith("package "):
            package = line.removeprefix("package ").rstrip(";")
        if line.startswith("public class ") or line.startswith("public final class "):
            match = re.search(r"class\s+(\w+)", line)
            if match:
                class_name = match.group(1)

        request_mapping = inline_annotation(line, "RequestMapping")
        if request_mapping:
            annotation = request_mapping
            class_path = annotation_value(annotation) or class_path
        authorize = inline_annotation(line, "PreAuthorize")
        if authorize:
            pending_guard = resolve_guard(authorize, guard_constants)
        mapping_name = next((annotation for annotation in HTTP_ANNOTATIONS
                             if annotation != "RequestMapping" and inline_annotation(line, annotation)), None)
        mapping = HTTP_ANNOTATIONS.get(mapping_name) if mapping_name else None
        if mapping:
            annotation = inline_annotation(line, mapping_name)
            pending_mapping = (mapping, annotation_value(annotation))

        if pending_mapping and re.search(r"\b(public|private|protected)\b.*\(", line):
            method, child_path = pending_mapping
            guard = pending_guard
            actions = ACTION_RE.findall(guard)
            modules = [{"module": m, "level": level} for m, level in MODULE_RE.findall(guard)]
            full_path = join_path(class_path, child_path)
            rows.append(
                {
                    "controller": class_name,
                    "package": package,
                    "file": str(path.relative_to(repo)).replace("\\", "/"),
                    "line": index + 1,
                    "httpMethod": method,
                    "path": full_path,
                    "guard": guard or None,
                    "explicitActions": actions,
                    "moduleGuards": modules,
                    "staffOnly": "staffOnly" in guard,
                    "parcoursGuard": "parcours" in guard,
                    "targetAction": infer_action(method, full_path, guard, modules),
                    "mappingStatus": mapping_status(class_name, full_path, guard, actions, modules),
                }
            )
            pending_mapping = None
            pending_guard = ""
    return rows


def collect_frontend(repo: Path) -> list[dict]:
    rows: list[dict] = []
    patterns = [
        re.compile(r"(?:auth|this\.auth)\.can\s*\(([^)]*)\)"),
        re.compile(r"(?:actionPermissions|permissions)\([^)]*\)\s*\?\?")
    ]
    for path in sorted((repo / "frontend" / "src" / "app").rglob("*.ts")):
        lines = path.read_text(encoding="utf-8").splitlines()
        for index, line in enumerate(lines):
            if any(pattern.search(line) for pattern in patterns):
                rows.append(
                    {
                        "file": str(path.relative_to(repo)).replace("\\", "/"),
                        "line": index + 1,
                        "source": line.strip(),
                        "usesModuleFallback": "??" in line and ("auth.can" in line or "actionPermissions" in line or "permissions" in line),
                    }
                )
    return rows


def main() -> None:
    # PowerShell hosts often expose cp1252 stdout even though the source and
    # generated inventory are UTF-8.  Keep the generator usable in CI and
    # when reviewers pipe it through the Windows terminal.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    repo = args.repo.resolve()
    controllers = []
    for path in sorted((repo / "backend" / "src" / "main" / "java").rglob("*Controller.java")):
        controllers.extend(parse_controller(path, repo))
    payload = {
        "schemaVersion": 1,
        "governingSpecification": "PERMISSION_POLICY_V2_IMPLEMENTATION_PLAN.md",
        "source": {
            "backendRoot": "backend/src/main/java",
            "frontendRoot": "frontend/src/app",
        },
        "summary": {
            "endpointCount": len(controllers),
            "explicitActionCount": sum(bool(row["explicitActions"]) for row in controllers),
            "moduleGuardCount": sum(row["mappingStatus"] == "module-guard" for row in controllers),
            "unprotectedCount": sum(row["mappingStatus"] == "unprotected" for row in controllers),
            "roleGuardCount": sum(row["mappingStatus"] == "role-guard" for row in controllers),
            "authenticationGuardCount": sum(row["mappingStatus"] == "authentication-guard" for row in controllers),
            "machineAuthCount": sum(row["mappingStatus"] == "machine-auth" for row in controllers),
            "publicCount": sum(row["mappingStatus"] == "public" for row in controllers),
        },
        "endpoints": controllers,
        "frontendPermissionSites": collect_frontend(repo),
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    else:
        print(text, end="")


if __name__ == "__main__":
    main()
