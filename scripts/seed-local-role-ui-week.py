"""Add local PROCTOR/EXAMINER UI fixtures without resetting existing data.

Requires Python bcrypt, the local pte-postgres container, and a full e2e exam.
Credentials are generated once in the user's local application-data directory;
they are never printed or committed. Existing fixture sessions are preserved.
"""

import json
import os
from pathlib import Path
import secrets
import subprocess
import sys

import bcrypt


def docker(*args, input_text=None):
    result = subprocess.run(
        ["docker", *args], input=input_text, text=True,
        encoding="utf-8", capture_output=True, check=False,
    )
    if result.returncode:
        # Do not print SQL/error context: it may contain a password hash.
        details = [line for line in result.stderr.splitlines()
                   if ("ERROR:" in line or "line " in line and " at " in line) and "credential" not in line.lower()
                   and "$2" not in line and "password" not in line.lower()]
        raise RuntimeError("Docker operation failed; seed transaction was not committed. "
                           + " ".join(details))
    return result.stdout


def main():
    context = json.loads(docker("context", "inspect"))[0]
    endpoint = context["Endpoints"]["docker"]["Host"]
    if not endpoint.startswith(("npipe://", "unix://")):
        raise RuntimeError("Only a local Docker socket is allowed.")
    if os.environ.get("DOCKER_HOST") or os.environ.get("DOCKER_CONTEXT"):
        raise RuntimeError("Unset Docker connection overrides before using this local seed.")
    container = json.loads(docker("inspect", "pte-postgres"))[0]
    config = dict(value.split("=", 1) for value in container["Config"]["Env"] if "=" in value)
    database_user = config["POSTGRES_USER"]
    credential_path = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "PTE" / "local-role-ui-credentials.json"
    if credential_path.exists():
        credentials = json.loads(credential_path.read_text(encoding="utf-8"))
    else:
        existing = docker("exec", "-i", "pte-postgres", "psql", "-U", database_user,
                          "-d", "pte", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1",
                          input_text="SELECT count(*) FROM users WHERE username IN "
                          "('proctor.ui@test.local','examiner.ui@test.local') "
                          "OR username LIKE 'student.ui.%@test.local';")
        if int(existing.strip()) != 0:
            raise RuntimeError("Fixture accounts exist but the local credentials file is missing. "
                               "Restore that file before rerunning; existing passwords were preserved.")
        credentials = {
            "url": "http://localhost:3000/login",
            "password": secrets.token_urlsafe(18) + "aA1!",
            "proctor": "proctor.ui@test.local",
            "examiner": "examiner.ui@test.local",
            "students": [f"student.ui.{index}@test.local" for index in range(1, 14)],
        }
    password_hash = bcrypt.hashpw(credentials["password"].encode(), bcrypt.gensalt()).decode()
    sql_path = Path(__file__).parent / "seed" / "seed-local-role-ui-week.sql"
    sql = sql_path.read_text(encoding="utf-8").replace(
        "INSERT INTO ui_seed_config VALUES (:'password_hash');",
        "INSERT INTO ui_seed_config VALUES ('" + password_hash + "');",
    )
    output = docker("exec", "-i", "pte-postgres", "psql", "-U", database_user,
                    "-d", "pte", "-X", "-v", "ON_ERROR_STOP=1", input_text=sql)
    credential_path.parent.mkdir(parents=True, exist_ok=True)
    credential_path.write_text(json.dumps(credentials, indent=2), encoding="utf-8")
    print(output)
    print(f"Local test credentials: {credential_path}")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, KeyError, ValueError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
