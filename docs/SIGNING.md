# Signing

The signing key is the app's identity. Android refuses an update signed with a different key, so
the same key must sign every build installed on a given TV.

## Two channels

| Channel | Who builds it | Key | Use |
|---|---|---|---|
| Source build | The user's agent (`./tv build`) | A key on the user's machine | The supported path; updates work in place |
| GitHub release | CI (`.github/workflows/release.yml`) | The project's key from Actions secrets | Convenience APK for people who do not build |

Pick one channel per TV. Switching channels means uninstalling first — which loses the app's
settings and, more importantly, leaves the stock launcher disabled until you run `Original` first.

## Adopting an existing key (migration from the pre-public fork)

If the TV already runs a build signed with `freemytv.keystore`, adopt that key **before** installing
anything new:

```bash
./tv sign adopt --keystore /path/to/freemytv.keystore
```

It asks for the keystore password (hidden input), copies the key to
`~/.config/mashrabiya/release.keystore` (mode 600), and writes `local.properties` (also 600).
Run this yourself; never paste the password into an agent chat.

Verify the certificate the build will use:

```bash
./tv build
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
"$BT/apksigner" verify --print-certs Mashrabiya.apk | head -2
```

Compare the SHA-256 digest with the installed app:

```bash
adb -s <IP>:5555 shell pm path io.freemytv
adb -s <IP>:5555 pull <path> /tmp/device.apk
"$BT/apksigner" verify --print-certs /tmp/device.apk | head -2
```

The digests must match.

## New machine, no key

`./tv build` creates `~/.config/mashrabiya/dev.keystore` with a random password in
`local.properties` (mode 600). That key is yours; back it up. Losing it means every TV with that
build must be uninstalled before a new build can be installed.

## CI secrets

The maintainer sets these once (values never appear in chat or logs):

```bash
base64 -w0 ~/.config/mashrabiya/release.keystore | gh secret set MASH_KEYSTORE_B64
gh secret set MASH_KEYSTORE_PASS
gh secret set MASH_KEY_ALIAS
gh secret set MASH_KEY_PASS
```

The release workflow decodes the key into `$RUNNER_TEMP`, builds with `--release`, verifies the
signature, and uploads `Mashrabiya.apk` plus its SHA-256. `tooling/release_guard.py` refuses a
release that contains a keystore, an APK, `config.properties`, or the private Thmanyah fonts.

## Key loss

There is no recovery. Android will not replace an installed app signed by another key. The user must
switch to **Original**, uninstall, install the new build, and switch again. Document the key's
backup location with the TV's owner.
