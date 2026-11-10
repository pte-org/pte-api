"""Exercise and populate local UI fixtures using the application's real APIs.

Run after seed-local-role-ui-week.py. Synthetic AI references are explicitly
labelled ui-fixture. REAL is the storage category required by the application's
machine-trait editor; it does not mean these local fixture scores were measured.
No scoring provider is called. Only UI-WEEK assignments may be changed here.
"""

import json
import os
from pathlib import Path
import runpy
import urllib.error
import urllib.request
import uuid


BASE_URL = "http://localhost:8091"
SEED = runpy.run_path(str(Path(__file__).with_name("seed-local-role-ui-week.py")))
DOCKER = SEED["docker"]
CREDENTIAL_PATH = Path(os.environ["LOCALAPPDATA"]) / "PTE/local-role-ui-credentials.json"


def api(path, token=None, body=None, method=None):
    request = urllib.request.Request(
        BASE_URL + path,
        headers={"Content-Type": "application/json", **({"Authorization": "Bearer " + token} if token else {})},
        data=json.dumps(body).encode() if body is not None else None, method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)
            return result.get("data", result)
    except urllib.error.HTTPError as error:
        try:
            payload = json.load(error)
        except ValueError:
            payload = {}
        raise RuntimeError(f"{request.get_method()} {path}: HTTP {error.code} {payload.get('code')}") from None


def sql(statement):
    return DOCKER("exec", "-i", "pte-postgres", "psql", "-U", "pte-db", "-d", "pte",
                  "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", input_text=statement)


def new_request(**fields):
    return {"requestPublicId": str(uuid.uuid4()), **fields}


def main():
    # Use the seed's connection guard before touching Docker.
    context = json.loads(DOCKER("context", "inspect"))[0]
    if os.environ.get("DOCKER_HOST") or os.environ.get("DOCKER_CONTEXT") or not context[
            "Endpoints"]["docker"]["Host"].startswith(("npipe://", "unix://")):
        raise RuntimeError("Only the local default Docker connection is supported.")
    credentials = json.loads(CREDENTIAL_PATH.read_text())
    tokens = {role: api("/api/v1/auth/login", body={
        "username": credentials[role], "password": credentials["password"],
    })["accessToken"] for role in ("proctor", "examiner")}
    proctor = tokens["proctor"]
    examiner = tokens["examiner"]
    sessions = json.loads(sql("SELECT json_agg(s) FROM (SELECT public_id,session_code FROM exam_sessions "
                             "WHERE session_code LIKE 'UI-WEEK-%' ORDER BY session_code) s;").strip())
    live_id = sessions[0]["public_id"]
    live_path = "/api/v1/exam-sessions/" + live_id
    roster = api(live_path + "/attendance", proctor)
    rows = roster["data"]
    if rows and rows[0]["version"] == 0:
        first = rows[0]
        api(live_path + "/attendance/students/" + first["studentPublicId"], proctor,
            new_request(status="PRESENT", enrollmentPublicId=first["enrollmentPublicId"],
                        expectedVersion=0, reasonOrNote="UI Week sample attendance action."), method="PATCH")
    incidents = api(live_path + "/incidents", proctor)
    if not incidents["data"]:
        for title, category in [("UI Week - Audio device issue", "TECHNICAL"),
                                ("UI Week - Identity verification review", "IDENTITY")]:
            incident = api(live_path + "/incidents", proctor, {
                "title": title, "description": "Synthetic local UI incident. Safe to edit for testing.",
                "category": category, "severity": "MEDIUM",
            })
            if category == "TECHNICAL":
                api(live_path + "/incidents/" + incident["incidentPublicId"] + "/status", proctor,
                    {"status": "IN_PROGRESS", "notes": "UI Week sample status transition."}, method="PATCH")
    else:
        incident = incidents["data"][0]
        if incident["status"] == "NEW":
            api(live_path + "/incidents/" + incident["incidentPublicId"] + "/status", proctor,
                {"status": "IN_PROGRESS", "notes": "UI Week sample status transition."}, method="PATCH")
    student = api("/api/v1/auth/login", body={
        "username": credentials["students"][0], "password": credentials["password"],
    })["accessToken"]
    attempt = sql("SELECT public_id FROM exam_attempts WHERE session_public_id='" + live_id
                  + "' AND student_public_id=(SELECT public_id FROM users "
                  "WHERE username='student.ui.1@test.local');").strip()
    api("/api/v1/attempts/" + str(uuid.UUID(attempt)) + "/security-violations", student, {
        "clientEventId": "ui-week-fullscreen-example", "violationType": "LOCKDOWN_FULLSCREEN_EXIT",
        "detail": "Synthetic local UI example: fullscreen exited.",
    })
    # The complete cohort remains closed; pending work has no automatic expiry.
    queue = api("/api/v1/examiner/work", examiner)["items"]
    fixture_ids = {row["public_id"] for row in sessions}
    queue = sorted((row for row in queue if row["sessionPublicId"] in fixture_ids),
                   key=lambda row: row["attemptPublicId"])
    for index, row in enumerate(queue):
        path = f"/api/v1/examiner/work/{row['sessionPublicId']}/{row['attemptPublicId']}"
        detail = api(path, examiner)
        workflow = detail["workflow"]
        if workflow["status"] == "COMPLETED":
            continue
        # Supply bounded sample machine traits so the speech editor is usable.
        # These local-only rows are visibly synthetic, never assessment evidence.
        statements = []
        for answer in detail["answers"] if workflow["status"] == "PENDING" else []:
            rubric = answer.get("rubric")
            if not rubric:
                continue
            criteria = {"scale": "PEARSON_V1", "rubricVersion": rubric["rubricVersionPublicId"],
                        "traits": {trait["key"]: max(0, int(trait["maxScore"]) // 2)
                                   for trait in rubric["traits"]}}
            encoded = json.dumps(criteria).replace("'", "''")
            answer_id = str(uuid.UUID(answer["answerPublicId"]))
            statements.append("UPDATE answer_evaluations SET ai_provider_category='REAL', "
                              f"criteria_scores_json='{encoded}' WHERE answer_public_id='{answer_id}' "
                              "AND source='AI' AND ai_provider='ui-fixture';")
        sql("BEGIN;\n" + "\n".join(statements) + "\nCOMMIT;")
        if index == 3:
            continue
        if workflow["status"] == "PENDING":
            api(path + "/start", examiner, new_request(expectedWorkVersion=workflow["workVersion"]))
        detail = api(path, examiner)
        if index == 2:
            if not api(path + "/issues", examiner):
                api(path + "/issues", examiner, new_request(
                    expectedWorkVersion=detail["workflow"]["workVersion"], type="OTHER",
                    description="UI Week sample issue: review this response before finalizing."))
            continue
        answers = detail["answers"] if index == 0 else detail["answers"][:1]
        for answer in answers:
            detail = api(path, examiner)
            if any(draft["answerPublicId"] == answer["answerPublicId"]
                   for draft in detail["workflow"]["drafts"]):
                continue
            current = next(item for item in detail["answers"]
                           if item["answerPublicId"] == answer["answerPublicId"])
            rubric = current.get("rubric")
            traits = ({trait["key"]: max(0, int(trait["maxScore"]) // 2)
                       for trait in rubric["traits"] if trait["editable"] and trait["key"] != "FORM"}
                      if rubric else None)
            api(path + "/answers/" + current["answerPublicId"] + "/draft", examiner,
                new_request(expectedWorkVersion=detail["workflow"]["workVersion"],
                            expectedDraftVersion=0, traitScores=traits,
                            score=None if rubric else 65, decisionKind="MANUAL",
                            expectedReviewVersion=current["reviewVersion"],
                            reviewedAiVersion=current["aiReference"]["version"],
                            comment="Synthetic local UI marking example."), method="PUT")
        if index == 0:
            detail = api(path, examiner)
            drafts = {item["answerPublicId"]: item for item in detail["workflow"]["drafts"]}
            api(path + "/finalize", examiner, new_request(
                expectedWorkVersion=detail["workflow"]["workVersion"], answers=[{
                    "answerPublicId": item["answerPublicId"],
                    "expectedDraftVersion": drafts[item["answerPublicId"]]["draftVersion"],
                    "expectedReviewVersion": item["reviewVersion"],
                    "reviewedAiVersion": item["aiReference"]["version"],
                } for item in detail["answers"]]))
    # Reuse the backend-calculated synthetic result for untouched pending work.
    # This keeps Confirm AI consistent with the same rubric as manual marking.
    sql("""UPDATE answer_evaluations target SET score=sample.score,
          criteria_scores_json=sample.trait_scores_json
        FROM answer_evaluations source_ai
        JOIN answer_evaluations sample ON sample.answer_public_id=source_ai.answer_public_id
          AND sample.source='EXAMINER' AND sample.examiner_status='SUBMITTED'
        JOIN grading_assignments finished ON finished.attempt_public_id=source_ai.attempt_public_id
          AND finished.status='COMPLETED'
        JOIN grading_assignments pending ON pending.session_public_id=finished.session_public_id
          AND pending.status='PENDING'
        JOIN exam_sessions s ON s.public_id=pending.session_public_id AND s.session_code='UI-WEEK-3'
        WHERE source_ai.source='AI' AND source_ai.ai_provider='ui-fixture'
          AND target.source='AI' AND target.ai_provider='ui-fixture'
          AND target.attempt_public_id=pending.attempt_public_id
          AND target.item_public_id=source_ai.item_public_id;""")
    for role in tokens:
        profile = api("/api/v1/auth/me", tokens[role])
        assert profile["roles"] == [role.upper()] and not profile["mustChangePassword"]
        print(role + ": login and role verified")
    for path in ["/api/v1/proctor-assignments/mine?scope=ALL&timezone=Asia%2FBangkok",
                 "/api/v1/proctor-assignments/mine/overview?timezone=Asia%2FBangkok",
                 *[live_path + suffix for suffix in ("/attendance", "/monitoring", "/incidents",
                                                    "/violations", "/security-audit")]]:
        api(path, proctor)
        print("HTTP 200 " + path)
    for suffix in ("", "/overview", "/reports"):
        result = api("/api/v1/examiner/work" + suffix, examiner)
        print("HTTP 200 /api/v1/examiner/work" + suffix)
        if suffix == "/overview":
            print("Examiner counts: " + json.dumps(result["counts"]))


if __name__ == "__main__":
    main()
