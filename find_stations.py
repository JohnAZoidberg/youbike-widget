#!/usr/bin/env python3
"""Helper script to find YouBike station IDs."""

import json
import sys

def load_stations():
    with open("sample_data.json", "r", encoding="utf-8") as f:
        return json.load(f)

def search_stations(stations, query):
    """Search stations by name (Chinese or English) or area."""
    query_lower = query.lower()
    results = []
    for s in stations:
        if (query_lower in s["sna"].lower() or
            query_lower in s["snaen"].lower() or
            query_lower in s["ar"].lower() or
            query_lower in s["aren"].lower() or
            query_lower in s["sarea"].lower() or
            query_lower in s["sareaen"].lower()):
            results.append(s)
    return results

def list_areas(stations):
    """List all unique areas."""
    areas = {}
    for s in stations:
        area = s["sarea"]
        if area not in areas:
            areas[area] = s["sareaen"]
    return areas

def print_station(s):
    name = s["sna"].replace("YouBike2.0_", "")
    print(f"  ID: {s['sno']}")
    print(f"  Name: {name}")
    print(f"  English: {s['snaen'].replace('YouBike2.0_', '')}")
    print(f"  Area: {s['sarea']} ({s['sareaen']})")
    print(f"  Address: {s['ar']}")
    print(f"  Bikes: {s['available_rent_bikes']}, Spots: {s['available_return_bikes']}")
    print()

def main():
    stations = load_stations()
    print(f"Loaded {len(stations)} stations\n")

    if len(sys.argv) > 1:
        query = " ".join(sys.argv[1:])
        if query == "--areas":
            print("Available areas:")
            for zh, en in sorted(list_areas(stations).items()):
                print(f"  {zh} ({en})")
            return

        results = search_stations(stations, query)
        print(f"Found {len(results)} stations matching '{query}':\n")
        for s in results[:20]:  # Limit to 20 results
            print_station(s)
        if len(results) > 20:
            print(f"... and {len(results) - 20} more")
    else:
        print("Usage:")
        print("  python find_stations.py <search term>")
        print("  python find_stations.py --areas")
        print()
        print("Examples:")
        print("  python find_stations.py 科技大樓")
        print("  python find_stations.py 'Technology'")
        print("  python find_stations.py 大安區")
        print("  python find_stations.py 'Daan'")

if __name__ == "__main__":
    main()
