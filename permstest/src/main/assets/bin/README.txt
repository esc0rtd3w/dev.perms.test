Place additional ELF binaries here.

Preferred layout (ABI-aware):
  assets/bin/arm64-v8a/<name>
  assets/bin/armeabi-v7a/<name>
  assets/bin/x86_64/<name>
  assets/bin/x86/<name>

The app will pick the best matching ABI directory based on Build.SUPPORTED_ABIS,
and fall back to assets/bin/<name> if no ABI subfolder is present.

At runtime, missing scanned-binary commands will auto-stage and copy the bundled
ELF from assets/bin/... to a shell-accessible directory under:
  /data/local/tmp/dev.perms.test/bin/
(chmod 755), then execute from that location.

Example: add adb/fastboot under the proper ABI folder to support devices that
don't ship /system/bin/adb or /system/bin/fastboot.

Apktool native compatibility binary:
  assets/bin/arm64-v8a/apktool
  assets/bin/armeabi-v7a/apktool
  assets/bin/x86/apktool
  assets/bin/x86_64/apktool

Source lives in the top-level apktool-go folder. Rebuild with apktool-go/build_android.sh
or apktool-go/build_android.bat, then copy the produced dist/<abi>/apktool files back
into these ABI folders.

Optional DEX-to-Java backend (jadx-go command name: jadx):
  assets/bin/arm64-v8a/jadx
  assets/bin/armeabi-v7a/jadx
  assets/bin/x86/jadx
  assets/bin/x86_64/jadx

Debugging > DEX to Java / Java output stages and runs this standalone backend.
Source lives in the top-level jadx-go folder. Rebuild with jadx-go/build_android.sh
or jadx-go/build_android.bat, then copy dist/<abi>/jadx back into these ABI folders.
The upstream jadx Java/Gradle source is not a runnable Android ELF by itself.

Current jadx-go output is best-effort Java/source index output. It recovers simple
method bodies and emits decoded Dalvik comments for complex methods, but is not
full upstream-JADX method-body decompilation yet. Long-running app runs use the Debugging foreground job
service and pass --progress/--log; optional zip export uses --zip-out, and selected-DEX runs use --dex-entry.
