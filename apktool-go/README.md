# apktool-go

This is a native `apktool` compatibility tool written in Go.
It is intentionally kept in its own top-level folder like `medit` so it can be
built and tested independently, then copied into Android asset folders when the
main app is ready to call it.

## Current goal

The first implementation focuses on practical decode/rebuild workflows before trying
to clone every upstream apktool feature:

- accept upstream-style commands and common flags so existing command lines stay compatible;
- decode APKs into a directory;
- convert binary `AndroidManifest.xml` into readable XML for preview/editing workflows;
- keep the original binary manifest under `original/AndroidManifest.xml` for safe rebuilds;
- rebuild a decoded directory back into an APK;
- apply the focused binary-manifest android:debuggable=true patch needed by Android frontend debug-package creation;
- provide verbose `I:`/`W:`/`E:`/`D:` style logging with optional `--log <file>` trace files;
- preserve stored ZIP entries where practical;
- force `resources.arsc` to be stored/uncompressed and 4-byte aligned for Android 11+ install rules;
- provide framework command placeholders compatible with `apktool if`, `cf`, and `lf`.

Unsupported flags are accepted where safe. They are not treated as implemented
until the backing feature exists. This avoids breaking Android frontend command strings
while keeping missing behavior explicit in source comments and docs.

## Important limitation in this first pass

General text XML is not compiled back into Android binary XML yet. During rebuild,
the original binary manifest is used from `original/AndroidManifest.xml`. For the
For debug-package and rename workflows, apktool-go recognizes when the decoded text
manifest requests `android:debuggable="true"` and applies that focused change to
the preserved binary manifest before writing the APK. Broader manifest/resource
compilation is still a future compatibility layer.

## Build requirements

- Go installed.
- No third-party Go modules are required.
- CGO is not required for arm64-v8a.
- Android NDK is recommended for armeabi-v7a, x86, and x86_64 Android PIE output. Without NDK, the build scripts produce linux static fallback binaries for those secondary ABIs and print a warning.

## Build host binary

```sh
go build -o apktool
```

## Build Android binaries

Git Bash / Linux / macOS:

```sh
./build_android.sh
```

Windows Command Prompt:

```bat
build_android.bat
```

The scripts create binaries under `dist/`. arm64-v8a is built as a proper Android PIE binary. The other ABIs are built as Android PIE binaries when `ANDROID_NDK_HOME` or `ANDROID_NDK_ROOT` points to an installed NDK; otherwise they are fallback host-cross Linux binaries until an NDK-backed build is run:

- `dist/arm64-v8a/apktool`
- `dist/armeabi-v7a/apktool`
- `dist/x86/apktool`
- `dist/x86_64/apktool`

## Examples

Decode only the manifest:

```sh
./apktool d -f --only-manifest -o out app.apk
```

Decode an APK while copying raw files:

```sh
./apktool d -f -o out app.apk
```

Rebuild:

```sh
./apktool b -f -o rebuilt.apk out
```

Verbose trace with a saved log file:

```sh
./apktool d -v --log decode.log -f -o out app.apk
./apktool b -v --log build.log out -o rebuilt.apk
```


## Debug logging

`apktool-go` uses apktool-style `I:`, `W:`, and `E:` messages plus `D:` verbose traces when `-v`, `--verbose`, or `--debug` is passed. Use `--log <file>` on any supported command to save the same diagnostic stream to a file. Parent log directories are created automatically.

Callers should run `apktool` as a standalone native tool. Frontends should stay thin runners/controllers; decode, build, manifest patching, ZIP layout, and apktool compatibility belong here.

Current build support includes `apktool d` decode, `apktool b --debuggable` rebuild with binary manifest debuggable patching, preserved `resources.arsc` storage/alignment, and signature-entry cleanup before re-signing.
