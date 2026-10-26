#!/data/data/com.termux/files/usr/bin/bash
# One-time opt-in registration: this NEVER starts or stops the live manual agent.
set -euo pipefail
PREFIX=/data/data/com.termux/files/usr
HOME=/data/data/com.termux/files/home
if [ "$#" -ge 1 ]; then TARGET="$1"; else TARGET="$HOME/anima-workspace/AnimaBridge-3.5"; fi
LINK="$HOME/.local/share/animabridge/active"
SERVICES="$PREFIX/var/service"
DEST="$SERVICES/animabridge"
STAGE="$HOME/.local/share/animabridge/service-registration-stage"
if ! command -v sv >/dev/null 2>&1 || ! command -v runsvdir >/dev/null 2>&1; then
  echo "Install termux-services (pkg install termux-services), then reopen Termux." >&2
  exit 2
fi
if [ ! -f "$TARGET/termux_agent/version.py" ] || [ ! -f "$TARGET/termux_agent/__main__.py" ]; then
  echo "Not an AnimaBridge Agent directory: $TARGET" >&2
  exit 2
fi
if [ -e "$DEST" ] || [ -L "$DEST" ]; then
  echo "Service is already registered; refusing to overwrite." >&2
  exit 2
fi
if [ -e "$LINK" ] || [ -L "$LINK" ] || [ -e "$STAGE" ]; then
  echo "Active link or staging exists; refusing overwrite." >&2
  exit 2
fi
mkdir -p "$HOME/.local/share/animabridge" "$SERVICES"
ln -s "$TARGET" "$LINK"
mkdir -p "$STAGE"
# New service starts DOWN, preventing a second concurrent agent.
touch "$STAGE/down"
cat > "$STAGE/run" <<'RUN'
#!/data/data/com.termux/files/usr/bin/sh
set -eu
HOME=/data/data/com.termux/files/home
PREFIX=/data/data/com.termux/files/usr
export HOME PREFIX
# If another agent is running manually, refuse an unsafe duplicate.
if command -v pgrep >/dev/null 2>&1 &&
    pgrep -f '^python(3)? -m termux_agent run --remote$' >/dev/null 2>&1; then
  echo "Manual agent detected. Stop it before sv up animabridge." >&2
  sleep 10
  exit 1
fi
cd "$HOME/.local/share/animabridge/active"
exec "$PREFIX/bin/python" -m termux_agent run --remote
RUN
chmod 700 "$STAGE/run"
mv "$STAGE" "$DEST"
echo "Service registered DOWN; manual agent has not been touched."
echo "Stop the manual agent, then use AnimaDev > Termux > AnimaBridge."
