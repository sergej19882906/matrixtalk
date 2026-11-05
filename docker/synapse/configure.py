#!/usr/bin/env python3
"""Apply environment configuration and discover all supported bridge registrations.

Preserve user-managed appservices and YAML comments. Read only mounted files;
never regenerate registration tokens. Paths are injectable for isolated tests.
"""
from ruamel.yaml import YAML
import os
from pathlib import Path
import tempfile

CONFIG_PATH = "/data/homeserver.yaml"
BRIDGES = (
    "mautrix-telegram", "mautrix-whatsapp", "mautrix-signal", "mautrix-meta",
    "mautrix-gmessages", "mautrix-discord", "heisenbridge", "hookshot",
)


def registration_files(root, yaml):
    found = []
    for bridge in BRIDGES:
        directory = Path(root) / bridge
        candidates = [directory / "registration.yaml", directory / "registration.yml"]
        existing = [path for path in candidates if path.is_file()]
        if len(existing) > 1:
            raise ValueError(f"Ambiguous registration files for {bridge}")
        for path in existing:
            with path.open(encoding="utf-8") as stream:
                registration = yaml.load(stream)
            if not isinstance(registration, dict) or not all(
                registration.get(field) for field in ("id", "as_token", "hs_token", "sender_localpart", "url")
            ) or not isinstance(registration.get("namespaces"), dict):
                raise ValueError(f"Invalid registration for {bridge}; configure it before restarting Synapse")
            found.append(str(path).replace("\\", "/"))
    return found


def configure(config_path=CONFIG_PATH, registration_root="/data"):
    yaml = YAML()
    yaml.preserve_quotes = True
    yaml.indent(mapping=2, sequence=4, offset=2)
    with open(config_path, encoding="utf-8") as stream:
        config = yaml.load(stream)
    if not isinstance(config, dict):
        raise ValueError("homeserver configuration must be a mapping")

    password = os.environ.get("POSTGRES_PASSWORD", "")
    if not password:
        raise ValueError("POSTGRES_PASSWORD is required")
    config["database"] = {
        "name": "psycopg2", "allow_unsafe_locale": True,
        "args": {
            "user": os.environ.get("POSTGRES_USER", "synapse"), "password": password,
            "database": os.environ.get("POSTGRES_DB", "synapse"), "host": "postgres", "cp_min": 5, "cp_max": 10,
        },
    }
    turn_secret = os.environ.get("TURN_SHARED_SECRET", "")
    if turn_secret:
        server_name = os.environ.get("SYNAPSE_SERVER_NAME", "localhost")
        port = int(os.environ.get("TURN_PORT", "3478"))
        config["turn_uris"] = [f"turn:{server_name}:{port}?transport=udp", f"turn:{server_name}:{port}?transport=tcp"]
        config["turn_shared_secret"] = turn_secret
        config["turn_username_lifetime"] = 86400000
    registration_secret = os.environ.get("SYNAPSE_REGISTRATION_SHARED_SECRET", "")
    if registration_secret:
        config["registration_shared_secret"] = registration_secret

    # Union, not replacement: unrelated user-managed registrations are preserved.
    user_services = config.get("app_service_config_files", []) or []
    if not isinstance(user_services, list):
        raise ValueError("app_service_config_files must be a list")
    services = list(dict.fromkeys(user_services + registration_files(registration_root, yaml)))
    if services or "app_service_config_files" in config:
        config["app_service_config_files"] = services

    # Avoid a partially rewritten homeserver.yaml if discovery/serialization fails.
    path = Path(config_path)
    fd, temporary = tempfile.mkstemp(prefix="homeserver-", suffix=".yaml", dir=path.parent)
    try:
        os.chmod(temporary, path.stat().st_mode & 0o777)
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            yaml.dump(config, stream)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print("[matrix-talk] Configuration applied; bridge registrations validated.")


if __name__ == "__main__":
    configure()
