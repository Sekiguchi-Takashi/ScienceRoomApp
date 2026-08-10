#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")" || exit 1

REPO="ScienceRoomApp"
OWNER="Sekiguchi-Takashi"
MSG="${1:-update}"
TOKEN="$(git config --global github.token)"

if [ -z "$TOKEN" ]; then
  printf '%s\n' "github.token が設定されていません" >&2
  exit 1
fi

curl -s -o /dev/null \
  -X POST \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO\",\"private\":true}"

if [ ! -d .git ]; then
  git init -b main
fi

git config user.name "$OWNER"
git config user.email "$OWNER@users.noreply.github.com"

git remote remove origin 2>/dev/null
git remote add origin "https://$OWNER:$TOKEN@github.com/$OWNER/$REPO.git"

git add -A
git commit -m "$MSG" || true
git push -u origin main --force

printf '%s\n' "pushed: $MSG"
printf '%s\n' "APK: https://github.com/$OWNER/$REPO/actions"
