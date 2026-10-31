#!/usr/bin/env python3
"""Fetch the current live grid carbon intensity for a Jenkins build."""

import argparse
import json
import os
import sys
import urllib.request


def main():
    parser = argparse.ArgumentParser(description="Fetch live Electricity Maps carbon intensity")
    parser.add_argument("--zone", default="LK", help="Electricity Maps zone")
    args = parser.parse_args()

    api_key = os.environ.get("ELECTRICITY_MAPS_API_KEY")
    if not api_key:
        print("ELECTRICITY_MAPS_API_KEY is not configured", file=sys.stderr)
        return 1

    url = f"https://api.electricitymaps.com/v4/carbon-intensity/latest?zone={args.zone}"
    request = urllib.request.Request(
        url,
        headers={"auth-token": api_key, "User-Agent": "GreenOptimizer/1.0"},
    )

    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            data = json.loads(response.read().decode("utf-8"))
        intensity = float(data["carbonIntensity"])
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"Unable to fetch live carbon intensity: {error}", file=sys.stderr)
        return 1

    print(json.dumps({
        "carbon_intensity_gco2_kwh": intensity,
        "source": "electricity_maps_live",
        "zone": args.zone,
    }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
