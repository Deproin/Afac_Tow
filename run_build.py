import subprocess
import os

try:
    result = subprocess.run(["cmd.exe", "/c", "gradlew assembleRelease"], cwd=r"c:\Users\N.A\Desktop\افاق محاسب\Afac_Tow", capture_output=True, text=True)
    with open(r"c:\Users\N.A\Desktop\افاق محاسب\Afac_Tow\build_log.txt", "w", encoding="utf-8") as f:
        f.write(result.stdout)
        f.write(result.stderr)
except Exception as e:
    with open(r"c:\Users\N.A\Desktop\افاق محاسب\Afac_Tow\build_log.txt", "w", encoding="utf-8") as f:
        f.write(str(e))
