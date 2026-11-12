#!/usr/bin/env python3
"""Moves media stored before the per-environment layout into it.

New uploads already go to
    {root}/exam/questions      prompt audio and images of EXAM-pool questions
    {root}/exam/answers        students' official exam answers
    {root}/practice/questions  prompt audio and images of PRACTICE-pool questions
    {root}/practice/recordings students' practice recordings
This script moves the older files there: it renames each file at Cloudinary and then points every media_objects row
that used the old path at the new one (cloudinary_public_id, storage_key, secure_url).

Local and production share one Cloudinary account, and a local database may hold copies of production rows. So each
environment only renames files in the folders it names (--move-prefix) that the other environment's database does not
also name (--other-env-paths, required). A row that points at a file another environment owns is left alone, or, with --repoint-prefix, is
pointed at the place that environment already moved the file to (only when that file exists there).

Dry run by default: it prints the plan and changes nothing. Add --apply to rename and update. Every applied move is
appended to a CSV (old_public_id,new_public_id,resource_type,delivery_type) so it can be reversed.

Examples (run from pte-api/):
  python3 deploy/scripts/migrate-cloudinary-folders.py --env-file .env.local --move-prefix pte/local/
  python3 deploy/scripts/migrate-cloudinary-folders.py --env-file .env \
      --move-prefix pte/question-media/ --move-prefix pte/submissions/ --apply
"""

import argparse
import base64
import csv
import hashlib
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

FOLDER_EXAM = "exam"
FOLDER_PRACTICE = "practice"
QUESTIONS = "questions"
ANSWERS = "answers"
RECORDINGS = "recordings"
POOL_PRACTICE = "PRACTICE"

# One row per media_objects row with where it belongs. The pool comes from the question that uses the file; a prompt
# file no question uses any more counts as EXAM, the questions' default pool.
CLASSIFY_SQL = """
select coalesce(json_agg(row_to_json(r)), '[]') from (
  select m.id, m.cloudinary_public_id as path, m.cloudinary_resource_type as resource_type,
         lower(m.cloudinary_delivery_type::text) as delivery_type,
         case when m.practice_item_public_id is not null then 'RECORDING'
              when m.audio_prompt or m.content_type like 'image/%' then 'QUESTION'
              else 'ANSWER' end as kind,
         (select q.pool::text from questions q
           where q.audio_prompt_ref = m.public_id or q.image_prompt_ref = m.public_id
           order by q.deleted, q.id limit 1) as pool
    from media_objects m
   where m.cloudinary_public_id is not null and m.status::text = 'UPLOADED'
) r
"""


def read_env(path):
    values = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip().strip('"').strip("'")
    return values


class Database:
    """Runs SQL through psql inside the Postgres container, as the deployment does."""

    def __init__(self, container):
        self.container = container
        self.user = self._container_env("POSTGRES_USER")
        self.database = self._container_env("POSTGRES_DB") or "pte"

    def _container_env(self, name):
        result = subprocess.run(["docker", "exec", self.container, "printenv", name],
                                capture_output=True, text=True)
        return result.stdout.strip()

    def query_json(self, sql):
        result = subprocess.run(["docker", "exec", "-i", self.container, "psql", "-U", self.user, "-d", self.database,
                                 "-At", "-v", "ON_ERROR_STOP=1"], input=sql, capture_output=True, text=True,
                                encoding="utf-8", check=True)
        return json.loads(result.stdout.strip() or "[]")

    def execute(self, sql):
        subprocess.run(["docker", "exec", "-i", self.container, "psql", "-U", self.user, "-d", self.database,
                        "-q", "-v", "ON_ERROR_STOP=1"], input=sql, capture_output=True, text=True, encoding="utf-8",
                       check=True)


class Cloudinary:
    def __init__(self, cloud_name, api_key, api_secret):
        self.cloud_name = cloud_name
        self.api_key = api_key
        self.api_secret = api_secret

    def _sign(self, params):
        payload = "&".join(f"{key}={params[key]}" for key in sorted(params))
        return hashlib.sha1((payload + self.api_secret).encode("utf-8")).hexdigest()

    def rename(self, resource_type, delivery_type, old_path, new_path):
        params = {"from_public_id": old_path, "to_public_id": new_path, "type": delivery_type,
                  "invalidate": "true", "overwrite": "false", "timestamp": str(int(time.time()))}
        body = dict(params, api_key=self.api_key, signature=self._sign(params))
        request = urllib.request.Request(
            f"https://api.cloudinary.com/v1_1/{self.cloud_name}/{resource_type}/rename",
            data=urllib.parse.urlencode(body).encode("utf-8"), method="POST")
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))

    def find(self, resource_type, delivery_type, path):
        """The stored file at this path, or None when there is none."""
        token = base64.b64encode(f"{self.api_key}:{self.api_secret}".encode("utf-8")).decode("ascii")
        url = (f"https://api.cloudinary.com/v1_1/{self.cloud_name}/resources/{resource_type}/{delivery_type}/"
               + urllib.parse.quote(path, safe="/"))
        request = urllib.request.Request(url, headers={"Authorization": f"Basic {token}"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            raise


def target_path(root, row, path):
    if row["kind"] == "RECORDING":
        folder = f"{root}/{FOLDER_PRACTICE}/{RECORDINGS}"
    elif row["kind"] == "ANSWER":
        folder = f"{root}/{FOLDER_EXAM}/{ANSWERS}"
    else:
        branch = FOLDER_PRACTICE if row["pool"] == POOL_PRACTICE else FOLDER_EXAM
        folder = f"{root}/{branch}/{QUESTIONS}"
    return f"{folder}/{path.rsplit('/', 1)[-1]}"


def in_layout(path, root):
    """True for a file already under {root}/exam/ or {root}/practice/; an old folder may sit under the same root."""
    return path.startswith(f"{root}/{FOLDER_EXAM}/") or path.startswith(f"{root}/{FOLDER_PRACTICE}/")


def plan(rows, root, move_prefixes, repoint_prefixes, repoint_root, shared_paths=frozenset()):
    """Groups rows by stored file: rows that share a file must all agree on where it goes."""
    by_path = {}
    for row in rows:
        by_path.setdefault(row["path"], []).append(row)
    moves, repoints, skipped = [], [], []
    for path, group in sorted(by_path.items()):
        if in_layout(path, root) or (repoint_root and in_layout(path, repoint_root)):
            continue  # already in the new layout
        # A file the other environment's database also names is not this environment's to move.
        owned = path not in shared_paths and any(path.startswith(prefix) for prefix in move_prefixes)
        foreign = any(path.startswith(prefix) for prefix in repoint_prefixes)
        if not owned and not foreign:
            reason = ("the other environment also uses this file" if path in shared_paths
                      else "not in a --move-prefix or --repoint-prefix folder")
            skipped.append((path, reason))
            continue
        destination_root = root if owned else repoint_root
        targets = {target_path(destination_root, row, path) for row in group}
        if len(targets) != 1:
            skipped.append((path, "rows sharing this file disagree on its folder: " + ", ".join(sorted(targets))))
            continue
        entry = (path, targets.pop(), group[0]["resource_type"], group[0]["delivery_type"], len(group))
        (moves if owned else repoints).append(entry)
    return moves, repoints, skipped


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def point_rows(database, old_path, new_path, secure_url):
    database.execute(
        "update media_objects set cloudinary_public_id = {new}, storage_key = {new}, secure_url = {url}, "
        "updated_at = now() where cloudinary_public_id = {old};".format(
            new=sql_text(new_path), url=sql_text(secure_url), old=sql_text(old_path)))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--env-file", required=True, help="env file with CLOUDINARY_* and CLOUDINARY_ROOT_FOLDER")
    parser.add_argument("--move-prefix", action="append", default=[],
                        help="old folder this environment owns, e.g. pte/local/ (repeatable)")
    parser.add_argument("--repoint-prefix", action="append", default=[],
                        help="old folder another environment owns and has already moved (repeatable)")
    parser.add_argument("--repoint-root", help="root that other environment moved its files to, e.g. pte/prod")
    parser.add_argument("--other-env-paths", required=True,
                        help="file listing every cloudinary_public_id the other environment's database names (one per "
                             "line); those files are never renamed from here. Export it with: psql -Atc \"select "
                             "cloudinary_public_id from media_objects where cloudinary_public_id is not null\"")
    parser.add_argument("--postgres-container", default="pte-postgres")
    parser.add_argument("--log", default="cloudinary-folder-moves.csv", help="CSV of applied moves, for reversal")
    parser.add_argument("--apply", action="store_true", help="rename and update; without it nothing changes")
    args = parser.parse_args()

    env = read_env(args.env_file)
    root = env.get("CLOUDINARY_ROOT_FOLDER", "").strip("/")
    if not root:
        sys.exit("CLOUDINARY_ROOT_FOLDER is not set in the env file")
    if args.repoint_prefix and not args.repoint_root:
        sys.exit("--repoint-prefix needs --repoint-root")
    if not args.move_prefix and not args.repoint_prefix:
        sys.exit("give at least one --move-prefix or --repoint-prefix")
    cloudinary = Cloudinary(env.get("CLOUDINARY_CLOUD_NAME", ""), env.get("CLOUDINARY_API_KEY", ""),
                            env.get("CLOUDINARY_API_SECRET", ""))
    if not (cloudinary.cloud_name and cloudinary.api_key and cloudinary.api_secret):
        sys.exit("Cloudinary credentials are missing from the env file")

    database = Database(args.postgres_container)
    rows = database.query_json(CLASSIFY_SQL)
    with open(args.other_env_paths, encoding="utf-8") as handle:
        shared_paths = frozenset(line.strip() for line in handle if line.strip())
    moves, repoints, skipped = plan(rows, root, args.move_prefix, args.repoint_prefix,
                                    (args.repoint_root or "").strip("/"), shared_paths)

    print(f"root: {root}   rows: {len(rows)}   files to move: {len(moves)}   rows to repoint: {len(repoints)}"
          f"   skipped: {len(skipped)}")
    for old, new, _, _, count in moves:
        print(f"  MOVE    {old} -> {new}" + (f"  ({count} rows)" if count > 1 else ""))
    for old, new, _, _, count in repoints:
        print(f"  REPOINT {old} -> {new}" + (f"  ({count} rows)" if count > 1 else ""))
    for old, reason in skipped:
        print(f"  SKIP    {old}: {reason}")
    if not args.apply:
        print("Dry run: nothing changed. Add --apply to rename and update.")
        return

    failures = 0
    with open(args.log, "a", newline="", encoding="utf-8") as log_file:
        log = csv.writer(log_file)
        for old, new, resource_type, delivery_type, _ in moves:
            try:
                renamed = cloudinary.rename(resource_type, delivery_type, old, new)
                # Logged before the database update: if that update fails, the CSV still says where the file went.
                log.writerow([old, new, resource_type, delivery_type])
                log_file.flush()
                point_rows(database, old, new, renamed.get("secure_url", ""))
            except (urllib.error.URLError, subprocess.CalledProcessError, ValueError) as error:
                failures += 1
                print(f"  FAILED  {old}: {type(error).__name__}", file=sys.stderr)
        for old, new, resource_type, delivery_type, _ in repoints:
            try:
                found = cloudinary.find(resource_type, delivery_type, new)
                if found is None:
                    print(f"  WAIT    {old}: {new} does not exist yet; move that environment first")
                    continue
                point_rows(database, old, new, found.get("secure_url", ""))
            except (urllib.error.URLError, subprocess.CalledProcessError, ValueError) as error:
                failures += 1
                print(f"  FAILED  {old}: {type(error).__name__}", file=sys.stderr)
    print(f"Done: {len(moves) + len(repoints) - failures} applied, {failures} failed. Moves logged in {args.log}.")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
