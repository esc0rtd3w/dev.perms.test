# jadx-go

`jadx-go` is a small standalone Go-based DEX/APK to Java-output tool. It is not a full upstream JADX clone. The current goal is a portable command line backend that can run from a shell or be embedded by other frontends and produce useful best-effort Java files from APK/DEX input.

## Current scope

- APK/ZIP input with top-level `classes*.dex` discovery by default.
- Raw `.dex` input.
- Multi-dex handling and optional selected DEX-entry filtering.
- Optional nested DEX asset scanning with an explicit flag.
- Class, superclass, interface, field, and method metadata parsing.
- Best-effort Java output under package folders.
- Method-body recovery for common simple constructors, accessors, field writes, call wrappers, new-instance returns, const-class returns, arrays, throw-only methods, simple conditionals, simple switch bodies, guarded returns, lazy-init blocks, synchronized blocks, and selected if/goto/conditional-assignment shapes.
- Descriptor-aware method arguments, field/array assignments, zero comparisons, and primitive/reference/boolean result handling where the DEX metadata is known.
- Decoded Dalvik instruction comments for complex method bodies.
- `index.txt`, text summary, optional progress lines, optional debug-summary counters, log mirroring/appending, binary-safe inner/synthetic type rendering, and zip export.

## Usage

```sh
jadx -d out input.apk
jadx --dex-entry classes2.dex -d out input.apk
jadx --progress --log jadx-go.log --zip-out out.zip -d out input.apk
jadx --progress --debug-summary --append-log --log jadx-go.log --dex-entry classes2.dex -d out input.apk
jadx --debug-summary --include-nested-dex -d out input.apk
jadx --binary-inner-names -d out input.apk
# --java-inner-names is accepted for compatibility, but output stays binary-safe
# until jadx-go supports nested source declarations.
```

`--debug-summary` prints generic parse/write counters such as DEX input count, bytes, class/method/field totals, recovered simple methods, complex fallback methods, no-code methods, decoded instruction count, and elapsed time. It is safe for frontends to pass this flag when they need troubleshooting logs.

The output command is named `jadx` for command-line compatibility. This source tree remains named `jadx-go` to distinguish it from upstream Java JADX.

## Build

Host build:

```sh
go build -trimpath -o dist/host/jadx
```

Android builds for all ABI folders:

```sh
./build_android.sh
```

This produces:

```text
dist/arm64-v8a/jadx
dist/armeabi-v7a/jadx
dist/x86/jadx
dist/x86_64/jadx
```

With an Android NDK configured, Android targets use the NDK clang toolchains. Without the NDK, the script can emit static Linux fallback binaries for local smoke testing.

Windows:

```bat
build_android.bat
```

## Limitations

This is currently a best-effort Java output generator, not a full upstream JADX replacement. It keeps binary `$` names in type references so generated top-level `Outer$Inner.java` and synthetic classes reference each other correctly; Java-dot inner-name rendering will wait until nested source declaration output exists.

Complex loops, broad control-flow restructuring, deeper register-to-variable analysis, full type inference, try/finally recovery, and synthetic cleanup are future work. Complex bodies intentionally retain decoded Dalvik instruction comments instead of emitting unsafe Java.
