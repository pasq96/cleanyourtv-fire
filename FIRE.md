# CleanYourTV on Fire OS 8

Fork of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) for the Fire TV Stick 4K 2nd gen (AFTKM,
Fire OS 8 / Android 11). Amazon's launcher stays the protected system home; `HomeKeyService` covers its home
window with this launcher and leaves Settings alone. Nothing is disabled or uninstalled.

## Builds

`.github/workflows/fire-apk.yml`: every push to `main` builds `cleanyourtv-fire.apk` and puts it on the
rolling `latest` release: `releases/download/latest/cleanyourtv-fire.apk`. A `v*` tag gets its own release.
Gradle dependencies and task outputs are cached between runs; upstream's workflows are skipped on the fork.

Optional signing secrets (Settings → Secrets and variables → Actions); without them a cached debug key is used:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_B64` | `base64 -w0 fire.jks` |
| `SIGNING_PASSWORD` | keystore and key password |

```bash
keytool -genkeypair -v -keystore fire.jks -alias fire -keyalg RSA -keysize 2048 -validity 10000
```

## Install

```bash
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --release <owner>/<repo>   # latest release
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --apk cleanyourtv-fire.apk # an artifact
FIRE_HOST=192.168.1.50 launcher/fire-install.sh                           # local build, launcher/fire-theme
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --theme ../my-theme        # another theme
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --restore                  # back to Amazon's home
```

## Upstream sync

```bash
git fetch upstream && git rebase upstream/main && git push --force-with-lease
```
