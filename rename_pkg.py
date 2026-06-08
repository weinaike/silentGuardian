import os

old_pkg = "com.example.silentguardian"
new_pkg = "com.yestek.silentguardian"

for root, dirs, files in os.walk("."):
    if ".git" in root or "build" in root or "gitee_release" in root:
        continue
    for file in files:
        if file.endswith((".kt", ".xml", ".kts", ".pro", ".md")):
            filepath = os.path.join(root, file)
            with open(filepath, "r", encoding="utf-8") as f:
                content = f.read()
            if old_pkg in content:
                content = content.replace(old_pkg, new_pkg)
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(content)

os.rename("app/src/main/java/com/example", "app/src/main/java/com/yestek")
