#!/usr/bin/env bash
# =============================================================================
# power_monitor.sh — Real-time power draw recorder for Lubuntu CI builds
#
# Usage:
#   ./power_monitor.sh start <output_csv>   # Start sampling in background
#   ./power_monitor.sh stop  <pid_file>     # Stop background process
#
# Output CSV format:
#   timestamp_epoch,watts,method
#
# Method auto-detection order:
#   1. Intel RAPL  (/sys/class/powercap/intel-rapl)  — most accurate (±2%)
#   2. powerstat   (apt package)                      — good accuracy   (±5%)
#   3. CPU% × TDP  (/proc/stat)                       — always available(±15%)
#
# Environment variables (all optional):
#   POWER_SAMPLE_INTERVAL_S  — seconds between samples (default: 2)
#   CPU_TDP_WATTS            — CPU thermal design power for method 3 estimate
#                              (default: 65 W — adjust for your processor)
# =============================================================================

set -euo pipefail

SAMPLE_INTERVAL="${POWER_SAMPLE_INTERVAL_S:-2}"
CPU_TDP="${CPU_TDP_WATTS:-65}"

# ─── RAPL helpers ────────────────────────────────────────────────────────────

# Returns the primary RAPL package-0 energy file path, or empty string.
find_rapl_path() {
    local base="/sys/class/powercap"
    if [ -d "${base}/intel-rapl:0" ]; then
        echo "${base}/intel-rapl:0/energy_uj"
    elif [ -d "${base}/intel-rapl" ]; then
        # Older kernels may enumerate differently
        local pkg
        pkg=$(find "${base}" -maxdepth 1 -name "intel-rapl:*" | head -1)
        [ -n "${pkg}" ] && echo "${pkg}/energy_uj" || echo ""
    else
        echo ""
    fi
}

# Read current RAPL energy in micro-joules
rapl_read_uj() {
    cat "$1" 2>/dev/null || echo "0"
}

# ─── CPU % helpers (method 3 fallback) ───────────────────────────────────────

read_cpu_stats() {
    # Returns: user nice system idle iowait irq softirq steal
    awk '/^cpu / {print $2,$3,$4,$5,$6,$7,$8,$9}' /proc/stat
}

cpu_percent() {
    local stats1 stats2
    stats1=$(read_cpu_stats)
    sleep "${SAMPLE_INTERVAL}"
    stats2=$(read_cpu_stats)

    awk -v s1="${stats1}" -v s2="${stats2}" '
    BEGIN {
        split(s1, a, " "); split(s2, b, " ")
        idle1 = a[4] + a[5]; idle2 = b[4] + b[5]
        total1 = 0; total2 = 0
        for (i=1; i<=8; i++) { total1 += a[i]; total2 += b[i] }
        dtotal = total2 - total1
        didle  = idle2  - idle1
        if (dtotal == 0) print 0
        else printf "%.1f\n", 100.0 * (dtotal - didle) / dtotal
    }'
}

# ─── Detection ───────────────────────────────────────────────────────────────

detect_method() {
    local rapl_path
    rapl_path=$(find_rapl_path)

    if [ -n "${rapl_path}" ] && [ -r "${rapl_path}" ]; then
        echo "rapl:${rapl_path}"
    elif command -v powerstat >/dev/null 2>&1; then
        echo "powerstat"
    else
        echo "cpu_estimate"
    fi
}

# ─── Sampling loops ──────────────────────────────────────────────────────────

sample_rapl() {
    local output_csv="$1"
    local energy_file="$2"

    local e1 e2 ts1 ts2 delta_uj delta_s watts
    e1=$(rapl_read_uj "${energy_file}")
    ts1=$(date +%s%3N)  # ms epoch

    while true; do
        sleep "${SAMPLE_INTERVAL}"
        e2=$(rapl_read_uj "${energy_file}")
        ts2=$(date +%s%3N)

        # Handle RAPL counter wrap (max_energy_range_uj)
        local max_uj
        local max_file
        max_file="$(dirname "${energy_file}")/max_energy_range_uj"
        max_uj=$(cat "${max_file}" 2>/dev/null || echo "262143328850")

        if [ "${e2}" -lt "${e1}" ]; then
            delta_uj=$(( max_uj - e1 + e2 ))
        else
            delta_uj=$(( e2 - e1 ))
        fi

        delta_s=$(awk -v t1="${ts1}" -v t2="${ts2}" 'BEGIN { printf "%.3f\n", (t2 - t1) / 1000.0 }')
        # Power = energy / time  →  W = µJ / µs  =  µJ / (s×10^6)
        watts=$(awk -v duj="${delta_uj}" -v ds="${delta_s}" 'BEGIN { if (ds > 0) printf "%.2f\n", duj / (ds * 1000000.0); else print 0 }')

        echo "$(date +%s),${watts},rapl" >> "${output_csv}"

        e1="${e2}"
        ts1="${ts2}"
    done
}

sample_powerstat() {
    local output_csv="$1"
    # powerstat -R: raw readings, -d 0: no delay, interval samples
    # We parse "Average:" line for each burst, but easier to do per-second reads
    while true; do
        local line
        line=$(powerstat -R -d 0 "${SAMPLE_INTERVAL}" 1 2>/dev/null \
               | awk '/^[[:space:]]*[0-9]/ && NF>=6 {print $(NF-1)}' \
               | tail -1)
        if [ -n "${line}" ]; then
            echo "$(date +%s),${line},powerstat" >> "${output_csv}"
        fi
        # powerstat itself sleeps for the interval; no extra sleep needed
    done
}

sample_cpu_estimate() {
    local output_csv="$1"
    # cpu_percent() already sleeps SAMPLE_INTERVAL internally
    while true; do
        local pct
        pct=$(cpu_percent)
        # Estimate: idle ~= 15% TDP, full load ~= 100% TDP
        # watts = 0.15*TDP + 0.85*TDP * cpu_pct/100
        local watts
        watts=$(awk -v tdp="${CPU_TDP}" -v pct="${pct}" 'BEGIN { printf "%.2f\n", (0.15 * tdp) + (0.85 * tdp * pct / 100.0) }')
        echo "$(date +%s),${watts},cpu_estimate" >> "${output_csv}"
    done
}

# ─── Entry point ─────────────────────────────────────────────────────────────

ACTION="${1:-}"
shift

case "${ACTION}" in
    start)
        OUTPUT_CSV="${1:?Usage: power_monitor.sh start <output.csv> [pid_file]}"
        PID_FILE="${2:-${OUTPUT_CSV%.csv}.pid}"

        # Write CSV header
        echo "timestamp_epoch,watts,method" > "${OUTPUT_CSV}"

        METHOD=$(detect_method)
        echo "[power_monitor] Detected method: ${METHOD}" >&2

        case "${METHOD}" in
            rapl:*)
                RAPL_PATH="${METHOD#rapl:}"
                sample_rapl "${OUTPUT_CSV}" "${RAPL_PATH}" &
                ;;
            powerstat)
                sample_powerstat "${OUTPUT_CSV}" &
                ;;
            cpu_estimate)
                sample_cpu_estimate "${OUTPUT_CSV}" &
                ;;
        esac

        echo $! > "${PID_FILE}"
        echo "[power_monitor] Started PID=$(cat "${PID_FILE}"), CSV=${OUTPUT_CSV}" >&2
        ;;

    stop)
        PID_FILE="${1:?Usage: power_monitor.sh stop <pid_file>}"
        if [ -f "${PID_FILE}" ]; then
            PID=$(cat "${PID_FILE}")
            kill "${PID}" 2>/dev/null && echo "[power_monitor] Stopped PID=${PID}" >&2 || true
            rm -f "${PID_FILE}"
        else
            echo "[power_monitor] PID file not found: ${PID_FILE}" >&2
        fi
        ;;

    *)
        echo "Usage: $0 {start|stop} ..." >&2
        exit 1
        ;;
esac

