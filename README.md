# CleanYourTV Fire

**English** | [Italiano](README.it.md)

A Home launcher for the Fire TV Stick 4K (2nd gen, Fire OS 8 / Android 11) that also runs on Android TV.
Personal fork of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv). On Fire OS, Amazon's launcher
stays the protected system home; `HomeKeyService` covers its home window with this launcher and leaves Settings
alone. Nothing is disabled or uninstalled.

## Features

- **Dock and grid** of your apps, 4 to 8 tiles per row; apps without a TV banner get one made from their icon.
- **Focus highlight** in five styles (glass, soft, rim, halo, neon) that glides between tiles; speed adjustable.
- **Wallpaper**: your own pictures, a daily one from Picsum or Bing, opacity 0 to 100%, clock and weather.
- **Continue watching** row under the dock (Android TV): Watch Next entries of other apps, optional channel
  suggestions, and on Fire OS the apps opened last. Hold OK on a card to hide it.
- **Network pill** showing Wi-Fi, or Ethernet only while the cable has a working link; optional VPN badge.
- **Key helpers** (Home, Input, app buttons) put back after an update or a force stop.
- **Launcher settings** (hold OK on Settings at the top): every part above can be switched off.

## On the TV

- Hold OK on a tile to move it (arrows, then OK or Back to drop it).
- Menu key: Open, Move, dock, Hide from Home, Force stop, App info, Uninstall.
- Hidden apps come back from the App library (button in the top bar).

## Install

```bash
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --release <owner>/<repo>   # latest release
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --apk cleanyourtv-fire.apk # an artifact
FIRE_HOST=192.168.1.50 launcher/fire-install.sh                           # local build, launcher/fire-theme
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --restore                  # back to Amazon's home
```

Updating over adb (`adb install -r`) keeps the settings.

## Continue watching

On Android TV the row reads other apps' Watch Next entries and needs `READ_TV_LISTINGS`. The launcher asks once;
over adb:

```bash
adb shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS
```

Fire OS has no TV provider, so the row shows the apps opened last. Details in [docs/watch-next.md](docs/watch-next.md).

## Builds

`.github/workflows/fire-apk.yml`: every push to `main` builds `cleanyourtv-fire.apk` and puts it on the rolling
`latest` release (`releases/download/latest/cleanyourtv-fire.apk`). A `v*` tag gets its own release. Without signing
secrets a cached debug key is used; to sign, set `SIGNING_KEYSTORE_B64` (`base64 -w0 fire.jks`) and
`SIGNING_PASSWORD` under Settings, Secrets and variables, Actions.

## Upstream sync

```bash
git fetch upstream && git rebase upstream/main && git push --force-with-lease
```

## Licence

As the upstream project, see [LICENSE](LICENSE).
