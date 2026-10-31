#!/usr/bin/env python3
"""
calculate_carbon.py — Post-pipeline carbon footprint calculator

Reads power_log.csv produced by power_monitor.sh, then computes:
  - Average watts during the pipeline run
  - Peak watts
  - Total energy consumed (Wh)
  - Carbon emitted (gCO2e) using the Electricity Maps carbon intensity

Usage:
    python3 calculate_carbon.py \\
        --csv        power_log.csv \\
        --duration   430.5 \\            # pipeline duration in seconds
        --intensity  312.5 \\            # gCO2/kWh from Electricity Maps
        --output     carbon_result.json  # optional; prints JSON to stdout too

Output JSON:
    {
        "method":                  "rapl",
        "avg_watts":               42.7,
        "peak_watts":              68.1,
        "min_watts":               18.3,
        "sample_count":            215,
        "monitoring_duration_s":   430,
        "pipeline_duration_s":     430.5,
        "energy_wh":               0.2479,
        "carbon_intensity_gco2_kwh": 312.5,
        "carbon_gco2e":            0.0775,
        "carbon_gco2e_per_min":    0.0108
    }
"""

import argparse
import csv
import json
import statistics
import sys
from pathlib import Path

DEFAULT_CARBON_INTENSITY = 475.0   # gCO2/kWh — global average (IEA 2023)
DEFAULT_IDLE_WATTS       = 18.0    # fallback if CSV is empty


def load_power_log(csv_path: str) -> list[dict]:
    """Read the CSV and return a list of {timestamp, watts, method} dicts."""
    rows = []
    path = Path(csv_path)
    if not path.exists():
        print(f"[calculate_carbon] WARNING: {csv_path} not found — using idle estimate", file=sys.stderr)
        return rows

    with open(csv_path, newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                w = float(row["watts"])
                if w > 0:                     # skip zero readings (startup noise)
                    rows.append({
                        "timestamp": int(row["timestamp_epoch"]),
                        "watts":     w,
                        "method":    row.get("method", "unknown"),
                    })
            except (ValueError, KeyError):
                continue   # skip malformed rows
    return rows


def calculate(rows: list[dict], pipeline_duration_s: float, carbon_intensity: float) -> dict:
    """Compute energy and carbon metrics."""

    if rows:
        watts_series = [r["watts"] for r in rows]
        avg_watts    = statistics.mean(watts_series)
        peak_watts   = max(watts_series)
        min_watts    = min(watts_series)
        sample_count = len(rows)
        # Monitoring duration = last - first timestamp (seconds)
        mon_duration = rows[-1]["timestamp"] - rows[0]["timestamp"] if len(rows) > 1 else pipeline_duration_s
        method       = rows[0]["method"]
    else:
        # Fallback: assume idle draw for the whole pipeline
        avg_watts    = DEFAULT_IDLE_WATTS
        peak_watts   = DEFAULT_IDLE_WATTS
        min_watts    = DEFAULT_IDLE_WATTS
        sample_count = 0
        mon_duration = pipeline_duration_s
        method       = "fallback_idle_estimate"

    # Energy: average power × time  (convert seconds → hours, watts → kWh)
    energy_wh  = avg_watts * (pipeline_duration_s / 3600.0)
    energy_kwh = energy_wh / 1000.0

    # Carbon: kWh × gCO2/kWh
    carbon_gco2e = energy_kwh * carbon_intensity

    # Per-minute rate for dashboards
    pipeline_minutes     = pipeline_duration_s / 60.0
    carbon_gco2e_per_min = carbon_gco2e / pipeline_minutes if pipeline_minutes > 0 else 0.0

    return {
        "method":                    method,
        "avg_watts":                 round(avg_watts,    2),
        "peak_watts":                round(peak_watts,   2),
        "min_watts":                 round(min_watts,    2),
        "sample_count":              sample_count,
        "monitoring_duration_s":     round(mon_duration, 1),
        "pipeline_duration_s":       round(pipeline_duration_s, 1),
        "energy_wh":                 round(energy_wh,    4),
        "carbon_intensity_gco2_kwh": round(carbon_intensity, 2),
        "carbon_gco2e":              round(carbon_gco2e, 6),
        "carbon_gco2e_per_min":      round(carbon_gco2e_per_min, 6),
    }


def main():
    parser = argparse.ArgumentParser(description="Calculate pipeline carbon footprint")
    parser.add_argument("--csv",       required=True,  help="Path to power_log.csv")
    parser.add_argument("--duration",  required=True,  type=float, help="Pipeline duration in seconds")
    parser.add_argument("--intensity", required=False, type=float,
                        default=DEFAULT_CARBON_INTENSITY,
                        help=f"Carbon intensity in gCO2/kWh (default: {DEFAULT_CARBON_INTENSITY})")
    parser.add_argument("--output",    required=False, help="Optional path to write result JSON")
    parser.add_argument("--attach-to", required=False, help="Path to existing JSON payload to inject 'power_monitoring' into")
    args = parser.parse_args()

    rows   = load_power_log(args.csv)
    result = calculate(rows, args.duration, args.intensity)

    out_json = json.dumps(result, indent=2)
    print(out_json)

    if args.output:
        Path(args.output).write_text(out_json)
        print(f"[calculate_carbon] Result written to {args.output}", file=sys.stderr)

    if args.attach_to:
        target_path = Path(args.attach_to)
        if target_path.exists():
            try:
                payload = json.loads(target_path.read_text())
                payload["power_monitoring"] = result
                target_path.write_text(json.dumps(payload, indent=2))
                print(f"[calculate_carbon] Attached power_monitoring to {args.attach_to}", file=sys.stderr)
            except Exception as e:
                print(f"[calculate_carbon] Failed to attach to {args.attach_to}: {e}", file=sys.stderr)

    # Print a human-readable summary to stderr for Jenkins console
    print(
        f"\n[calculate_carbon] ─── Carbon Summary ───────────────────────────────\n"
        f"  Method:           {result['method']}\n"
        f"  Avg power:        {result['avg_watts']} W\n"
        f"  Peak power:       {result['peak_watts']} W\n"
        f"  Energy consumed:  {result['energy_wh']} Wh\n"
        f"  Carbon intensity: {result['carbon_intensity_gco2_kwh']} gCO₂/kWh\n"
        f"  Carbon emitted:   {result['carbon_gco2e']} gCO₂e\n"
        f"─────────────────────────────────────────────────────────────────────",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()

