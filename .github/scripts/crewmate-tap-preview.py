"""Tap the in-app design preview button using an Android UIAutomator dump."""
import re
import subprocess
import xml.etree.ElementTree as ET

root = ET.parse("runtime-screenshots/ui.xml").getroot()
nodes = [node for node in root.iter("node") if "디자인 시안" in node.attrib.get("text", "")]
if not nodes:
    raise SystemExit("FAIL: Preview button was not found on the main screen")
bounds = nodes[0].attrib.get("bounds", "")
coords = [int(item) for item in re.findall(r"[0-9]+", bounds)]
if len(coords) != 4:
    raise SystemExit(f"FAIL: Unexpected Android UI bounds: {bounds!r}")
x1, y1, x2, y2 = coords
x, y = (x1 + x2) // 2, (y1 + y2) // 2
subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)], check=True)
print(f"Tapped design preview button at ({x}, {y})")
