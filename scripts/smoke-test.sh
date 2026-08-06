#!/usr/bin/env bash
#
# End-to-end smoke test for the WeVolunteer messaging endpoints.
#
# The unit tests mock DynamoDbClient, so they cannot catch a malformed UpdateExpression, a
# reserved-word collision, or a missing IAM action. Those only show up against the real table,
# which is what this script is for. Run it after deploying, against a non-production stack.
#
# Usage:
#   API_URL=http://localhost:8080 \
#   VOLUNTEER_TOKEN=eyJ... VOLUNTEER_ID=<cognito-sub> \
#   ORG_TOKEN=eyJ...       ORG_ID=<cognito-sub> \
#   ./smoke-test.sh
#
# Optional: STRANGER_ID=<cognito-sub of a volunteer with no registration for ORG_ID>
#           enables the check that an organization cannot cold-message someone.
#
# Getting a token: sign in to the app, open devtools, and copy the access token the frontend
# stores under the oidc.user:... key in session storage. Or, if the app client allows
# USER_PASSWORD_AUTH:
#   aws cognito-idp initiate-auth --auth-flow USER_PASSWORD_AUTH \
#     --client-id "$CLIENT_ID" \
#     --auth-parameters USERNAME=you@example.com,PASSWORD=... \
#     --query 'AuthenticationResult.AccessToken' --output text
#
# The volunteer must already be registered for one of the organization's opportunities,
# otherwise the organization-side reply is correctly rejected with 403.

set -uo pipefail

: "${API_URL:?set API_URL}"
: "${VOLUNTEER_TOKEN:?set VOLUNTEER_TOKEN}"
: "${VOLUNTEER_ID:?set VOLUNTEER_ID}"
: "${ORG_TOKEN:?set ORG_TOKEN}"
: "${ORG_ID:?set ORG_ID}"

# Git Bash on Windows ships neither python3 nor jq, and a Python install there may be exposed
# as "python" or the "py" launcher instead. Resolve one up front rather than failing eight
# checks in with a confusing empty-string comparison.
PYTHON=""
for candidate in python3 python py; do
  if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c "import json" >/dev/null 2>&1; then
    PYTHON="$candidate"
    break
  fi
done

if [ -z "$PYTHON" ]; then
  echo "No Python interpreter found (tried python3, python, py)." >&2
  echo "The status-code checks would still work, but every JSON assertion would silently" >&2
  echo "pass on an empty string, which is worse than not running at all." >&2
  exit 2
fi

# Every id-shaped variable here is a Cognito subject, and mixing up which account is which
# produces a cascade of confusing failures rather than one clear one -- a volunteer token paired
# with an organization id looks exactly like "you cannot message yourself". The tokens already
# carry the truth, so check it rather than trusting the labels.
token_claim() {
  "$PYTHON" -c "
import base64, json, sys
try:
    payload = sys.argv[1].split('.')[1]
    payload += '=' * (-len(payload) % 4)
    print(json.loads(base64.urlsafe_b64decode(payload)).get(sys.argv[2], ''))
except Exception:
    print('')
" "$1" "$2" 2>/dev/null
}

# Cognito access tokens last an hour. An expired one turns every check into a 401, which looks
# like a broken deployment rather than a stale credential -- worth one comparison up front.
check_token() {
  local label=$1 token=$2 expected_id=$3
  local sub exp now

  sub=$(token_claim "$token" sub)
  exp=$(token_claim "$token" exp)

  # Non-JWT tokens (a stub server, say) decode to nothing. Skip rather than block those.
  if [ -z "$sub" ]; then
    return 0
  fi

  if [ "$sub" != "$expected_id" ]; then
    echo "${label} belongs to ${sub}, but the matching id is set to ${expected_id}." >&2
    echo "Set it to ${sub}, or supply the other account's token." >&2
    exit 2
  fi

  if [ -n "$exp" ]; then
    now=$(date +%s)

    if [ "$exp" -le "$now" ] 2>/dev/null; then
      echo "${label} expired $(( (now - exp) / 60 )) minute(s) ago. Sign in again and re-copy it." >&2
      exit 2
    fi
  fi
}

check_token VOLUNTEER_TOKEN "$VOLUNTEER_TOKEN" "$VOLUNTEER_ID"
check_token ORG_TOKEN "$ORG_TOKEN" "$ORG_ID"

if [ "$VOLUNTEER_ID" = "$ORG_ID" ]; then
  echo "VOLUNTEER_ID and ORG_ID are the same account. This test needs two." >&2
  exit 2
fi

CONVERSATION_ID="${VOLUNTEER_ID}_${ORG_ID}"
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

PASSED=0
FAILED=0

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; PASSED=$((PASSED + 1)); }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; printf '       %s\n' "$2"; FAILED=$((FAILED + 1)); }

# Sets RESPONSE_STATUS and RESPONSE_BODY. Deliberately not echoing the body for the caller to
# capture with $(...): that runs in a subshell, so the status assignment would be lost.
request() {
  local method=$1 path=$2 token=$3 body=${4:-}
  local args=(-sS -X "$method" -o "$BODY_FILE" -w '%{http_code}')

  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")

  RESPONSE_STATUS=$(curl "${args[@]}" "${API_URL}${path}")
  RESPONSE_BODY=$(cat "$BODY_FILE")
}

expect_status() {
  local label=$1 expected=$2 actual=$3 body=$4

  if [ "$actual" = "$expected" ]; then
    pass "$label"
  else
    fail "$label" "expected HTTP $expected, got $actual -- $body"
  fi
}

# json <expression> reads "$BODY_FILE". Uses Python because jq is not universally present.
json() {
  "$PYTHON" -c "
import json, sys
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print('')
    sys.exit(0)
print($1)
" "$BODY_FILE" 2>/dev/null
}

echo
echo "Messaging smoke test against ${API_URL}"
echo "  conversation: ${CONVERSATION_ID}"
echo

echo "Authentication"
request GET /conversations/me ""
expect_status "rejects a request with no token" 401 "$RESPONSE_STATUS" "$RESPONSE_BODY"

echo
echo "Volunteer opens a conversation"
request GET /conversations/me "$VOLUNTEER_TOKEN"
expect_status "volunteer can read their inbox" 200 "$RESPONSE_STATUS" "$RESPONSE_BODY"

request POST /conversations "$VOLUNTEER_TOKEN" \
  "{\"recipientId\":\"${ORG_ID}\",\"body\":\"Smoke test: is parking available?\"}"
expect_status "volunteer can message any organization" 200 "$RESPONSE_STATUS" "$RESPONSE_BODY"

returned_id=$(json "d.get('conversationId','')")
if [ "$returned_id" = "$CONVERSATION_ID" ]; then
  pass "conversation id is derived from the participants"
else
  fail "conversation id is derived from the participants" \
       "expected ${CONVERSATION_ID}, got '${returned_id}'"
fi

counterpart=$(json "d.get('counterpartName','')")
if [ -n "$counterpart" ]; then
  pass "inbox row names the counterpart (${counterpart})"
else
  fail "inbox row names the counterpart" "counterpartName was empty"
fi

echo
echo "Organization replies"
request GET /conversations/me "$ORG_TOKEN"
expect_status "organization can read its inbox" 200 "$RESPONSE_STATUS" "$RESPONSE_BODY"

unread=$(json "next((c.get('unreadCount',0) for c in d if c.get('conversationId')=='${CONVERSATION_ID}'), -1)")
if [ "${unread:-0}" -ge 1 ] 2>/dev/null; then
  pass "recipient's unread count incremented (${unread})"
else
  fail "recipient's unread count incremented" "unreadCount was '${unread}'"
fi

request POST "/conversations/${CONVERSATION_ID}/messages" "$ORG_TOKEN" \
  '{"body":"Smoke test: yes, behind the building."}'
expect_status "organization can reply in an existing thread" 200 "$RESPONSE_STATUS" "$RESPONSE_BODY"

sender_type=$(json "d.get('senderType','')")
if [ "$sender_type" = "ORGANIZATION" ]; then
  pass "sender type resolved from the token, not the request"
else
  fail "sender type resolved from the token, not the request" "got '${sender_type}'"
fi

echo
echo "Thread ordering and read receipts"
request GET "/conversations/${CONVERSATION_ID}/messages" "$VOLUNTEER_TOKEN"
expect_status "volunteer can read the thread" 200 "$RESPONSE_STATUS" "$RESPONSE_BODY"

ordered=$(json "'yes' if [m['sentAt'] for m in d] == sorted(m['sentAt'] for m in d) else 'no'")
if [ "$ordered" = "yes" ]; then
  pass "messages come back oldest first"
else
  fail "messages come back oldest first" "sentAt values were not ascending"
fi

count=$(json "len(d)")
if [ "${count:-0}" -ge 2 ] 2>/dev/null; then
  pass "both messages are in the thread (${count})"
else
  fail "both messages are in the thread" "found ${count}"
fi

request POST "/conversations/${CONVERSATION_ID}/read" "$ORG_TOKEN"
expect_status "organization can mark the thread read" 200 "$RESPONSE_STATUS" "$RESPONSE_BODY"

request GET /conversations/me "$ORG_TOKEN"
unread_after=$(json "next((c.get('unreadCount',-1) for c in d if c.get('conversationId')=='${CONVERSATION_ID}'), -1)")
if [ "$unread_after" = "0" ]; then
  pass "unread count cleared"
else
  fail "unread count cleared" "unreadCount was '${unread_after}'"
fi

echo
echo "Authorization"
request GET "/conversations/somebody-else_${ORG_ID}/messages" "$VOLUNTEER_TOKEN"
expect_status "outsider cannot read a conversation they are not in" 403 "$RESPONSE_STATUS" "$RESPONSE_BODY"

request GET "/conversations/not-a-conversation/messages" "$VOLUNTEER_TOKEN"
expect_status "malformed conversation id is a 400, not a 403" 400 "$RESPONSE_STATUS" "$RESPONSE_BODY"

request POST /conversations "$VOLUNTEER_TOKEN" \
  "{\"recipientId\":\"${VOLUNTEER_ID}\",\"body\":\"hello me\"}"
expect_status "cannot start a conversation with yourself" 400 "$RESPONSE_STATUS" "$RESPONSE_BODY"

request POST "/conversations/${CONVERSATION_ID}/messages" "$VOLUNTEER_TOKEN" '{"body":"   "}'
expect_status "blank message body is rejected" 400 "$RESPONSE_STATUS" "$RESPONSE_BODY"

if [ -n "${STRANGER_ID:-}" ]; then
  request POST /conversations "$ORG_TOKEN" \
    "{\"recipientId\":\"${STRANGER_ID}\",\"body\":\"Come volunteer with us!\"}"
  expect_status "organization cannot cold-message an unregistered volunteer" 403 "$RESPONSE_STATUS" "$RESPONSE_BODY"
else
  echo "  SKIP organization cold-message check (set STRANGER_ID to enable)"
fi

echo
echo "----------------------------------------"
printf 'passed: %d   failed: %d\n' "$PASSED" "$FAILED"
echo

[ "$FAILED" -eq 0 ]
