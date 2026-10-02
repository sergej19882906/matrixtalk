#!/usr/bin/env python3
"""Patch homeserver.yaml with environment variables.

Replaces database, TURN, and app_service_config_files sections.
Other sections (including comments) are preserved via ruamel.yaml.
"""
from ruamel.yaml import YAML
import os

CONFIG_PATH = "/data/homeserver.yaml"


def configure():
    yaml = YAML()
    yaml.preserve_quotes = True
    yaml.indent(mapping=2, sequence=4, offset=2)

    with open(CONFIG_PATH, "r") as f:
        config = yaml.load(f)

    # --- Database: replace SQLite with PostgreSQL ---
    config["database"] = {
        "name": "psycopg2",
        "allow_unsafe_locale": True,
        "args": {
            "user": os.environ.get("POSTGRES_USER", "synapse"),
            "password": os.environ.get("POSTGRES_PASSWORD", ""),
            "database": os.environ.get("POSTGRES_DB", "synapse"),
            "host": "postgres",
            "cp_min": 5,
            "cp_max": 10,
        },
    }

    # --- TURN server for VoIP ---
    turn_secret = os.environ.get("TURN_SHARED_SECRET", "")
    if turn_secret:
        server_name = os.environ.get("SYNAPSE_SERVER_NAME", "localhost")
        config["turn_uris"] = [
            f"turn:{server_name}?transport=udp",
            f"turn:{server_name}?transport=tcp",
        ]
        config["turn_shared_secret"] = turn_secret
        config["turn_username_lifetime"] = 86400000  # 24 h in ms

    # --- Bridge registration files (only add existing ones) ---
    app_services = []
    for bridge in ("mautrix-telegram", "mautrix-whatsapp", "mautrix-signal"):
        reg_path = f"/data/{bridge}/registration.yaml"
        if os.path.exists(reg_path):
            app_services.append(reg_path)

    if app_services:
        config["app_service_config_files"] = app_services

    with open(CONFIG_PATH, "w") as f:
        yaml.dump(config, f)

    print("[matrix-talk] Configuration applied.")


if __name__ == "__main__":
    configure()
