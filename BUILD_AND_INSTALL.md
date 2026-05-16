# Build and Install GitHub Copilot for Anypoint Studio

This guide builds a self-contained Eclipse p2 update-site ZIP for MuleSoft Anypoint Studio and installs it without relying on external Mylyn WikiText repositories.

## Prerequisites

- macOS shell or Windows PowerShell, Git, and the repository checkout.
- Java compatible with this Tycho build. The Maven wrapper uses the project configuration.
- Anypoint Studio installed.
  - macOS example: `/Applications/AnypointStudio.app`.
  - Windows example: `C:\Program Files\AnypointStudio\AnypointStudio.exe`.

## Quick one-command build

Run this from the repository root on macOS to build the plugin and create the install-ready ZIP in `build/`:

```bash
./mvnw -DskipTests -Dcheckstyle.skip=true clean verify && mkdir -p build && cp -f com.microsoft.copilot.eclipse.repository/target/com.microsoft.copilot.eclipse.repository-0.18.0-SNAPSHOT.zip build/github-copilot-eclipse-anypoint-0.0.1.zip && { xattr -c build/github-copilot-eclipse-anypoint-0.0.1.zip 2>/dev/null || true; } && shasum -a 256 build/github-copilot-eclipse-anypoint-0.0.1.zip > build/github-copilot-eclipse-anypoint-0.0.1.zip.sha256
```

Run this from the repository root on Windows PowerShell to build the plugin and create the install-ready ZIP in `build\`:

```powershell
.\mvnw.cmd -DskipTests -Dcheckstyle.skip=true clean verify; if ($LASTEXITCODE -eq 0) { New-Item -ItemType Directory -Force build | Out-Null; Copy-Item -Force com.microsoft.copilot.eclipse.repository\target\com.microsoft.copilot.eclipse.repository-0.18.0-SNAPSHOT.zip build\github-copilot-eclipse-anypoint-0.0.1.zip; Get-FileHash -Algorithm SHA256 build\github-copilot-eclipse-anypoint-0.0.1.zip | ForEach-Object { "$($_.Hash.ToLower())  build\github-copilot-eclipse-anypoint-0.0.1.zip" } | Set-Content build\github-copilot-eclipse-anypoint-0.0.1.zip.sha256 }
```

After either command succeeds, install this archive in Anypoint Studio:

```text
build/github-copilot-eclipse-anypoint-0.0.1.zip
```

## 1. Build the update site

Run from the repository root on macOS:

```bash
./mvnw -DskipTests -Dcheckstyle.skip=true clean verify
```

Run from the repository root on Windows PowerShell:

```powershell
.\mvnw.cmd -DskipTests -Dcheckstyle.skip=true clean verify
```

Expected source update-site ZIP:

```text
com.microsoft.copilot.eclipse.repository/target/com.microsoft.copilot.eclipse.repository-0.18.0-SNAPSHOT.zip
```

## 2. Create the root build ZIP

Run from the repository root on macOS:

```bash
mkdir -p build
cp -f \
  com.microsoft.copilot.eclipse.repository/target/com.microsoft.copilot.eclipse.repository-0.18.0-SNAPSHOT.zip \
  build/github-copilot-eclipse-anypoint-0.0.1.zip
xattr -c build/github-copilot-eclipse-anypoint-0.0.1.zip 2>/dev/null || true
shasum -a 256 build/github-copilot-eclipse-anypoint-0.0.1.zip \
  > build/github-copilot-eclipse-anypoint-0.0.1.zip.sha256
```

Run from the repository root on Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force build | Out-Null
Copy-Item -Force `
  com.microsoft.copilot.eclipse.repository\target\com.microsoft.copilot.eclipse.repository-0.18.0-SNAPSHOT.zip `
  build\github-copilot-eclipse-anypoint-0.0.1.zip
Get-FileHash -Algorithm SHA256 build\github-copilot-eclipse-anypoint-0.0.1.zip |
  ForEach-Object { "$($_.Hash.ToLower())  build\github-copilot-eclipse-anypoint-0.0.1.zip" } |
  Set-Content build\github-copilot-eclipse-anypoint-0.0.1.zip.sha256
```

Final install archive:

```text
build/github-copilot-eclipse-anypoint-0.0.1.zip
```

## 3. Verify the ZIP includes required dependencies

Run from the repository root on macOS:

```bash
unzip -l build/github-copilot-eclipse-anypoint-0.0.1.zip \
  | grep -E 'org.eclipse.mylyn.wikitext.ui_4.4.0|com.microsoft.copilot.eclipse.ui_0.18.0'
```

Run from the repository root on Windows PowerShell:

```powershell
tar -tf build\github-copilot-eclipse-anypoint-0.0.1.zip |
  Select-String 'org.eclipse.mylyn.wikitext.ui_4.4.0|com.microsoft.copilot.eclipse.ui_0.18.0'
```

The output must include `org.eclipse.mylyn.wikitext.ui_4.4.0...jar`. If it does not, do not install the ZIP.

## 4. Verify p2 resolution from the ZIP

Run from the repository root on macOS:

```bash
ZIP_ABS="$PWD/build/github-copilot-eclipse-anypoint-0.0.1.zip"
rm -rf /tmp/copilot-eclipse-p2-latest-zip-verify
./mvnw -N org.eclipse.tycho:tycho-p2-director-plugin:4.0.13:director \
  -Drepositories="jar:file:${ZIP_ABS}!/" \
  -DinstallIUs=com.microsoft.copilot.eclipse.anypoint.feature.feature.group \
  -Ddestination=/tmp/copilot-eclipse-p2-latest-zip-verify \
  -Dprofile=CopilotLatestZipVerify \
  -Dprofileproperties=org.eclipse.update.install.features=true \
  -DverifyOnly=true \
  -DfollowReferences=false
```

Run from the repository root on Windows PowerShell:

```powershell
$ZipPath = (Resolve-Path "build\github-copilot-eclipse-anypoint-0.0.1.zip").Path -replace '\\','/'
$Repository = "jar:file:/$ZipPath!/"
Remove-Item -Recurse -Force "$env:TEMP\copilot-eclipse-p2-latest-zip-verify" -ErrorAction SilentlyContinue
.\mvnw.cmd -N org.eclipse.tycho:tycho-p2-director-plugin:4.0.13:director `
  "-Drepositories=$Repository" `
  -DinstallIUs=com.microsoft.copilot.eclipse.anypoint.feature.feature.group `
  "-Ddestination=$env:TEMP\copilot-eclipse-p2-latest-zip-verify" `
  -Dprofile=CopilotLatestZipVerify `
  -Dprofileproperties=org.eclipse.update.install.features=true `
  -DverifyOnly=true `
  -DfollowReferences=false
```

Expected result:

```text
Installing com.microsoft.copilot.eclipse.anypoint.feature.feature.group ...
Operation completed
BUILD SUCCESS
```

## 5. Install in Anypoint Studio UI

1. Close Anypoint Studio.
2. Reopen Anypoint Studio.
3. Open **Help → Install New Software...**.
4. Remove any old Copilot archive or update-site entry if it shows an older version such as `0.18.0.202605152322`.
5. Select **Add... → Archive...**.
6. Choose `build/github-copilot-eclipse-anypoint-0.0.1.zip`.
7. Select the GitHub Copilot / Anypoint Studio feature.
8. Complete the install wizard and restart Anypoint Studio when prompted.

If the wizard still reports missing `org.eclipse.mylyn.wikitext.ui`, Anypoint Studio is not using the new archive. Remove the old update-site entry, re-add the ZIP from `build/`, and restart Studio with `-clean`.

## 6. Configure MuleSoft MCP tools

The Anypoint Studio feature includes a MuleSoft MCP bridge and local Mule-aware Agent Mode tools.

Prerequisites:

- Node.js 20 or newer must be available to the Anypoint Studio process.
- An Anypoint Platform connected app or client credentials with permissions for the MuleSoft tasks you want Copilot to perform.
- Credentials can be set in **Preferences → Copilot → MuleSoft MCP** or provided to the Studio process as environment variables:

```text
ANYPOINT_CLIENT_ID
ANYPOINT_CLIENT_SECRET
ANYPOINT_REGION
```

`ANYPOINT_REGION` is optional. Use the region value that matches your control plane, such as `PROD_US`, `PROD_EU`, `PROD_CA`, or `PROD_JP`.

The registered MCP server command is:

```bash
npx -y mulesoft-mcp-server start
```

After enabling MuleSoft MCP:

1. Restart Anypoint Studio if credentials were set as environment variables.
2. Open **Preferences → Copilot → MCP**.
3. Approve the plug-in registered MuleSoft MCP configuration.
4. Confirm the MuleSoft tools appear in the Agent Mode tools list.

The feature also registers local built-in tools for Mule projects:

- `summarize_mule_project` reads `src/main/mule/*.xml` and summarizes flows, sub-flows, global configs, namespaces, processors, connectors, and property placeholders.
- `get_mule_project_errors` reads Anypoint Studio/Eclipse problem markers for a Mule project.
- `run_mule_maven_tests` runs Maven validation, defaulting to `test`, and requires confirmation before execution.

Recommended first prompts in Agent Mode:

```text
Summarize this Mule project and explain the existing flows before making changes.
Generate an MUnit test for the selected flow, then run Maven tests and fix failures.
Search Exchange for an HTTP connector example and adapt this flow to use the project conventions.
Validate this API spec against governance rulesets and implement the API in this Mule project.
```

## Optional: command-line install on macOS

Only run this with Anypoint Studio closed:

```bash
ZIP_ABS="$PWD/build/github-copilot-eclipse-anypoint-0.0.1.zip"
/Applications/AnypointStudio.app/Contents/MacOS/AnypointStudio \
  -nosplash \
  -application org.eclipse.equinox.p2.director \
  -repository "jar:file:${ZIP_ABS}!/" \
  -installIU com.microsoft.copilot.eclipse.anypoint.feature.feature.group \
  -profile DefaultProfile \
  -destination /Applications/AnypointStudio.app/Contents/Eclipse \
  -bundlepool /Applications/AnypointStudio.app/Contents/Eclipse \
  -roaming
```

Restart Anypoint Studio after the command completes.

## Optional: command-line install on Windows

Only run this with Anypoint Studio closed. Update `$Studio` if Anypoint Studio is installed in a different folder.

```powershell
$ZipPath = (Resolve-Path "build\github-copilot-eclipse-anypoint-0.0.1.zip").Path -replace '\\','/'
$Repository = "jar:file:/$ZipPath!/"
$Studio = "C:\Program Files\AnypointStudio\AnypointStudio.exe"

& $Studio `
  -nosplash `
  -application org.eclipse.equinox.p2.director `
  -repository $Repository `
  -installIU com.microsoft.copilot.eclipse.anypoint.feature.feature.group `
  -profile DefaultProfile `
  -destination "C:\Program Files\AnypointStudio" `
  -bundlepool "C:\Program Files\AnypointStudio" `
  -roaming
```

Restart Anypoint Studio after the command completes. If the install path is not `C:\Program Files\AnypointStudio`, use the directory that contains `AnypointStudio.exe` for both `-destination` and `-bundlepool`.
