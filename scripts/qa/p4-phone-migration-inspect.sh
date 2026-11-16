#!/bin/sh
# EF-OPS-KELPIE-P4-MIGRATION-INSPECT-001
# Read-only Termux diagnostics. Never print authentication material.
# No installs, uninstalls, service changes, config edits or restarts.
set -u

has_literal() {
    f=$1
    needle=$2
    [ -f "$f" ] && grep -F -q -- "$needle" "$f" 2>/dev/null
}

probe_port() {
    port=$1
    endpoint=$2
    if ! command -v curl >/dev/null 2>&1; then
        printf '%s\n' "NO_CURL"
        return
    fi
    code=$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' \
        --max-time 4 "http://127.0.0.1:$port$endpoint" 2>/dev/null) || :
    if [ -z "$code" ]; then code=000; fi
    printf '%s\n' "$code"
}

ref_flags() {
    f=$1
    label=$2
    if [ ! -f "$f" ]; then
        printf 'SOURCE_%s=ABSENT\n' "$label"
        return
    fi
    printf 'SOURCE_%s=PRESENT\n' "$label"
    for spec in \
        'OLD_UNIFIED:18440' \
        'EXISTING_P4:18437' \
        'PARALLEL_P4:18447' \
        'TUNNEL:8080' \
        'APP_UNIFIED:com.kelpie.browser.unifiedverify' \
        'APP_PCVERIFY:com.kelpie.browser.pcverify' \
        'APP_PARALLEL:com.kelpie.browser.pcparallelverify'; do
        key=$(printf '%s' "$spec" | cut -d: -f1)
        value=$(printf '%s' "$spec" | cut -d: -f2-)
        if has_literal "$f" "$value"; then
            printf 'SOURCE_%s_%s=YES\n' "$label" "$key"
        else
            printf 'SOURCE_%s_%s=NO\n' "$label" "$key"
        fi
    done
}

self_test() {
    tmp=$(mktemp) || exit 2
    trap 'rm -f "$tmp"' EXIT HUP INT TERM
    printf '%s\n' 'target 18440 and com.kelpie.browser.unifiedverify' > "$tmp"
    has_literal "$tmp" '18440' || exit 3
    has_literal "$tmp" 'com.kelpie.browser.unifiedverify' || exit 4
    if has_literal "$tmp" '18447'; then exit 5; fi
    if has_literal "$tmp" 'com.kelpie.browser.pcverify'; then exit 6; fi
    printf '%s\n' 'SELF_TEST_PASS'
}

if [ "$#" -eq 1 ] && [ "$1" = "--self-test" ]; then
    self_test
    exit 0
fi
if [ "$#" -gt 0 ]; then
    printf '%s\n' "Usage: sh p4-phone-migration-inspect.sh [--self-test]" >&2
    exit 2
fi
if [ -z "$HOME" ] || [ -z "$PREFIX" ]; then
    printf '%s\n' 'TERMUX_HOME_OR_PREFIX_MISSING' >&2
    exit 2
fi

printf '%s\n' 'EF_OPS_KELPIE_P4_READ_ONLY_PREFLIGHT_V1'
if command -v getprop >/dev/null 2>&1; then
    printf 'MODEL=%s\n' "$(getprop ro.product.model 2>/dev/null)"
    printf 'ANDROID=%s\n' "$(getprop ro.build.version.release 2>/dev/null)"
else
    printf '%s\n' 'MODEL=UNAVAILABLE_ANDROID=UNAVAILABLE'
fi

printf 'HEALTH_UNIFIED_18440=%s\n' "$(probe_port 18440 /health)"
printf 'HEALTH_P4_18437=%s\n' "$(probe_port 18437 /health)"
printf 'HEALTH_PARALLEL_18447=%s\n' "$(probe_port 18447 /health)"
printf 'READY_TUNNEL_8080=%s\n' "$(probe_port 8080 /readyz)"

if command -v sv >/dev/null 2>&1; then
    # Standard runit output: no environment values or authentication headers.
    sv status enerfoam-s24-tunnel 2>/dev/null || printf '%s\n' 'SUPERVISOR_STATUS=UNAVAILABLE'
else
    printf '%s\n' 'SUPERVISOR_STATUS=NO_SV'
fi

ref_flags "$PREFIX/var/service/enerfoam-s24-tunnel/run" "RUNIT_RUN"
ref_flags "$HOME/.local/bin/ef-kelpie-autostart" "AUTOSTART_HELPER"
ref_flags "$HOME/.bashrc" "BASHRC"
i=0
for f in "$HOME"/.termux/boot/*; do
    [ -f "$f" ] || continue
    i=$((i + 1))
    case "$(basename "$f")" in
        10-kelpie) label=BOOT_10 ;;
        20-enerfoam-s24) label=BOOT_20 ;;
        30-ef-s24-autocare) label=BOOT_30 ;;
        *) label=BOOT_EXTRA_$i ;;
    esac
    ref_flags "$f" "$label"
    if [ -x "$f" ]; then
        printf 'SOURCE_%s_EXECUTABLE=YES\n' "$label"
    else
        printf 'SOURCE_%s_EXECUTABLE=NO\n' "$label"
    fi
done
printf 'BOOT_FILES_COUNT=%s\n' "$i"

logfile="$PREFIX/var/log/sv/enerfoam-s24-tunnel/current"
if [ -f "$logfile" ]; then
    for token in waiting starting ready error; do
        count=$(tail -n 100 "$logfile" 2>/dev/null | grep -i -c "$token" || :)
        printf 'LOG_%s_LAST100=%s\n' "$token" "$count"
    done
else
    printf '%s\n' 'LOG=NOT_FOUND'
fi

# NEVER print YAML, command line, environment variables or auth headers.
printf '%s\n' 'UBUNTU_PHONE_DIRECT_YAML=NOT_INSPECTED_SECRET_BEARING'
printf '%s\n' 'ANDROID_PACKAGE_LIST=NOT_AUTHORITATIVE_TERMUX_VISIBILITY'
printf '%s\n' 'MCP_END_TO_END=NOT_TESTED_HERE'
printf '%s\n' 'CUTOVER=NOT_PERFORMED'
printf '%s\n' 'UNINSTALL=NOT_PERFORMED'
printf '%s\n' 'RELEASE_NO_GO'
