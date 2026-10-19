#!/usr/bin/env python3
"""Minimal ADB client for the TV, built on the `adb-shell` package.

The stock adb binary is blocked by macOS Local Network privacy on this Mac
("No route to host"), so everything goes through this script instead.

Usage:
  tvadb.py shell "<command>"            run one shell command, print output
  tvadb.py screenshot <out.png>         grab the TV screen
  tvadb.py key <KEY> [<KEY> ...]        inject key presses (DPAD_UP, ENTER, HOME...)
  tvadb.py push <local> <remote>        copy a file (base64 chunks + md5 check)
  tvadb.py install <app.apk>            push and `pm install -r` an APK

Environment: TV_IP (default 192.168.1.99), TV_PORT (default 5555).
"""
import base64
import hashlib
import os
import sys
import time

from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.keygen import keygen
from adb_shell.auth.sign_pythonrsa import PythonRSASigner

TV_IP = os.environ.get("TV_IP", "192.168.1.99")
TV_PORT = int(os.environ.get("TV_PORT", "5555"))
KEY_PATH = os.path.expanduser("~/.android/adbkey")
CHUNK = 48 * 1024  # bytes of raw data per base64 chunk


def connect():
    if not os.path.exists(KEY_PATH):
        os.makedirs(os.path.dirname(KEY_PATH), exist_ok=True)
        keygen(KEY_PATH)
    with open(KEY_PATH) as f:
        priv = f.read()
    with open(KEY_PATH + ".pub") as f:
        pub = f.read()
    signer = PythonRSASigner(pub, priv)
    dev = AdbDeviceTcp(TV_IP, TV_PORT, default_transport_timeout_s=30.0)
    # Long auth timeout: the first time, someone has to accept the prompt on the TV.
    dev.connect(rsa_keys=[signer], auth_timeout_s=90.0)
    return dev


def sh(dev, cmd, timeout=120.0, decode=True):
    return dev.shell(cmd, timeout_s=timeout, read_timeout_s=timeout, decode=decode)


def cmd_shell(dev, args):
    out = sh(dev, " ".join(args))
    sys.stdout.write(out)
    if out and not out.endswith("\n"):
        sys.stdout.write("\n")


def cmd_screenshot(dev, args):
    out = args[0] if args else "screen.png"
    data = sh(dev, "screencap -p | base64", timeout=60.0)
    png = base64.b64decode("".join(data.split()))
    if not png.startswith(b"\x89PNG"):
        sys.exit("screencap failed: %r" % png[:80])
    with open(out, "wb") as f:
        f.write(png)
    print(out)


KEY_ALIASES = {
    "UP": "DPAD_UP", "DOWN": "DPAD_DOWN", "LEFT": "DPAD_LEFT", "RIGHT": "DPAD_RIGHT",
    "OK": "DPAD_CENTER", "CENTER": "DPAD_CENTER", "SELECT": "DPAD_CENTER",
}


def cmd_key(dev, args):
    for k in args:
        if k.startswith("sleep:"):
            time.sleep(float(k.split(":", 1)[1]))
            continue
        name = KEY_ALIASES.get(k.upper(), k.upper())
        code = name if name.isdigit() else "KEYCODE_" + name
        sh(dev, "input keyevent " + code)
        time.sleep(0.35)


def cmd_push(dev, args):
    local, remote = args
    with open(local, "rb") as f:
        data = f.read()
    md5 = hashlib.md5(data).hexdigest()
    tmp = remote + ".b64"
    sh(dev, "rm -f '%s' '%s'" % (tmp, remote))
    total = (len(data) + CHUNK - 1) // CHUNK
    for i in range(total):
        piece = base64.b64encode(data[i * CHUNK:(i + 1) * CHUNK]).decode()
        sh(dev, "echo -n '%s' >> '%s'" % (piece, tmp))
        sys.stderr.write("\rpush %s: %d/%d" % (os.path.basename(local), i + 1, total))
    sys.stderr.write("\n")
    sh(dev, "base64 -d '%s' > '%s' && rm -f '%s'" % (tmp, remote, tmp))
    remote_md5 = sh(dev, "md5sum '%s'" % remote).split()[0]
    if remote_md5 != md5:
        sys.exit("md5 mismatch: local %s remote %s" % (md5, remote_md5))
    print("pushed %s -> %s (md5 %s ok)" % (local, remote, md5))


def cmd_install(dev, args):
    apk = args[0]
    remote = "/data/local/tmp/" + os.path.basename(apk)
    cmd_push(dev, [apk, remote])
    print(sh(dev, "pm install -r -t '%s'" % remote, timeout=180.0).strip())
    sh(dev, "rm -f '%s'" % remote)


COMMANDS = {
    "shell": cmd_shell,
    "screenshot": cmd_screenshot,
    "key": cmd_key,
    "push": cmd_push,
    "install": cmd_install,
}


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        sys.exit(__doc__)
    dev = connect()
    try:
        COMMANDS[sys.argv[1]](dev, sys.argv[2:])
    finally:
        dev.close()


if __name__ == "__main__":
    main()
