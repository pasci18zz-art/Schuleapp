#!/usr/bin/env python3
"""Sucht in einem uiautomator-Dump ein Element (content-desc oder text) und gibt die Mitte "x y" aus."""
import re
import sys
import xml.etree.ElementTree as ET

path, kind, value = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
for n in root.iter("node"):
    v = n.get("content-desc") if kind == "desc" else n.get("text")
    if v and (v == value or v.startswith(value)):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2, x1, y1, x2, y2)
            break
