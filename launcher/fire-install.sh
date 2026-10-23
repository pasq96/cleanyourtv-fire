#!/usr/bin/env bash
# Fire OS 8 (Fire TV Stick 4K AFTKM and similar): builds and installs the launcher over Amazon's home.
# Amazon's launcher stays enabled and the system home (Fire OS protects it); HomeKeyService covers its
# home window, Settings keeps working. Nothing is disabled or uninstalled.
#
#   FIRE_HOST=192.168.1.50 ./fire-install.sh              build, install, enable the redirect
#   FIRE_HOST=... ./fire-install.sh --theme ../themes/x   same, then push x/config.json (dock, vpn_app, ...)
#   FIRE_HOST=... ./fire-install.sh --apk app-release.apk install a prebuilt APK, no build
#   FIRE_HOST=... ./fire-install.sh --restore             redirect off, launcher uninstalled, Amazon home back
# FIRE_HOST unset: uses the single device adb already sees (USB or an earlier adb connect).
set -euo pipefail
cd "$(dirname "$0")"
PKG=com.gathin.tvhome
SVC=$PKG/.HomeKeyService
DIR=/sdcard/Android/data/$PKG/files
APK=app/build/outputs/apk/release/app-release.apk
THEME="" BUILD=1 MODE=install

log() { printf '%s %s fire-install.sh.%s:%s: %s\n' "$(date +'%y/%m/%d %H:%M:%S')" "$1" "${FUNCNAME[1]:-main}" "${BASH_LINENO[0]}" "$2"; }
die() { log ERROR "$1"; exit 1; }
sh_() { adb shell "$@" | tr -d '\r'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --theme) THEME="${2:?--theme needs a folder}"; shift 2 ;;
    --apk) APK="${2:?--apk needs a file}"; BUILD=0; shift 2 ;;
    --restore) MODE=restore; shift ;;
    *) die "unknown option $1" ;;
  esac
done

connect() {
  command -v adb >/dev/null || die "adb not found (platform-tools)"
  if [ -n "${FIRE_HOST:-}" ]; then
    adb connect "$FIRE_HOST" >/dev/null
    export ANDROID_SERIAL="$FIRE_HOST:5555"
    [[ "$FIRE_HOST" == *:* ]] && export ANDROID_SERIAL="$FIRE_HOST"
  fi
  [ "$(sh_ echo ok 2>/dev/null)" = ok ] || die "no device: accept the debugging prompt on the TV and retry"
  [ "$(sh_ pm has-feature amazon.hardware.fire_tv)" = true ] || die "not a Fire TV; use build-and-install.sh"
  log INFO "device $(sh_ getprop ro.product.model), Fire OS $(sh_ getprop ro.build.version.name | awk '{print $NF}'), SDK $(sh_ getprop ro.build.version.sdk)"
}

# Drops this launcher's service from the enabled list, keeping any other accessibility service.
service_off() {
  sh_ "s=\$(settings get secure enabled_accessibility_services); n=\$(echo \"\$s\" | tr ':' '\n' | grep -v '^$PKG/' | paste -sd: -); \
settings put secure enabled_accessibility_services \"\${n:-}\"; [ -n \"\$n\" ] || settings put secure accessibility_enabled 0" >/dev/null
}

service_on() {
  service_off
  sh_ "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in null|'') s=$SVC ;; *) s=\"\$s:$SVC\" ;; esac; \
settings put secure enabled_accessibility_services \"\$s\"; settings put secure accessibility_enabled 1" >/dev/null
  case "$(sh_ settings get secure enabled_accessibility_services)" in
    *"$SVC"*) log INFO "HomeKeyService on" ;; *) die "Fire OS refused the accessibility service" ;;
  esac
}

push_theme() {
  [ -f "$THEME/config.json" ] || die "no config.json in $THEME"
  python3 -c 'import json,sys; json.load(open(sys.argv[1]))' "$THEME/config.json" || die "$THEME/config.json is not valid JSON"
  adb push "$THEME/config.json" /data/local/tmp/cyt-config.json >/dev/null
  # The launcher's own provider writes into its private files; plain cp is the fallback the launcher also reads.
  sh_ "mkdir -p $DIR; content write --uri content://$PKG.theme/config.json < /data/local/tmp/cyt-config.json 2>/dev/null \
|| cp /data/local/tmp/cyt-config.json $DIR/config.json; date +%s%N > /data/local/tmp/cyt-rev; \
content write --uri content://$PKG.theme/theme_revision < /data/local/tmp/cyt-rev 2>/dev/null || cp /data/local/tmp/cyt-rev $DIR/theme_revision; \
rm -f /data/local/tmp/cyt-config.json /data/local/tmp/cyt-rev"
  log INFO "theme $(basename "$THEME") pushed"
}

restore() {
  connect
  service_off
  sh_ "pm uninstall $PKG" >/dev/null || true
  sh_ "am start -a android.intent.action.MAIN -c android.intent.category.HOME" >/dev/null
  log INFO "launcher removed, Amazon home back"
}

install() {
  connect
  if [ "$BUILD" = 1 ]; then
    [ -f local.properties ] || [ -z "${ANDROID_HOME:-}" ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
    log INFO "building release APK"
    ./gradlew assembleRelease -q --console=plain
  fi
  [ -f "$APK" ] || die "APK not found: $APK"
  log INFO "installing $(basename "$APK") ($(( $(wc -c < "$APK") / 1024 )) KB)"
  adb install -r "$APK" | grep -q Success || die "adb install failed"
  sh_ "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS; pm grant $PKG android.permission.ACCESS_FINE_LOCATION 2>/dev/null; \
cmd appops set $PKG AUTO_START allow 2>/dev/null; cmd package compile -m speed -f $PKG" >/dev/null || true
  [ -z "$THEME" ] || push_theme
  sh_ "am force-stop $PKG"
  service_on
  sh_ "am start -n $PKG/.MainActivity" >/dev/null
  log INFO "done: press Home on the remote. If it ever stops redirecting, open the launcher once from Your apps"
}

"$MODE"
