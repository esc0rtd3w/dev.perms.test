#!/usr/bin/env sh
set -eu

mkdir -p dist/arm64-v8a dist/armeabi-v7a dist/x86 dist/x86_64

CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build -trimpath -o dist/arm64-v8a/jadx

find_ndk_bin() {
    for root in "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}"; do
        if [ -n "$root" ] && [ -d "$root/toolchains/llvm/prebuilt" ]; then
            find "$root/toolchains/llvm/prebuilt" -type d -name bin | head -n 1
            return 0
        fi
    done
    return 1
}

build_android_external() {
    abi="$1"
    goarch="$2"
    goarm="$3"
    cc_name="$4"
    out="dist/$abi/jadx"
    if ndk_bin="$(find_ndk_bin)" && [ -x "$ndk_bin/$cc_name" ]; then
        if [ -n "$goarm" ]; then
            CC="$ndk_bin/$cc_name" CGO_ENABLED=1 GOOS=android GOARCH="$goarch" GOARM="$goarm" go build -trimpath -o "$out"
        else
            CC="$ndk_bin/$cc_name" CGO_ENABLED=1 GOOS=android GOARCH="$goarch" go build -trimpath -o "$out"
        fi
    else
        echo "W: Android NDK clang not found for $abi; building linux static fallback. Install/set ANDROID_NDK_HOME for Android PIE output." >&2
        if [ -n "$goarm" ]; then
            CGO_ENABLED=0 GOOS=linux GOARCH="$goarch" GOARM="$goarm" go build -trimpath -o "$out"
        else
            CGO_ENABLED=0 GOOS=linux GOARCH="$goarch" go build -trimpath -o "$out"
        fi
    fi
}

build_android_external armeabi-v7a arm 7 armv7a-linux-androideabi23-clang
build_android_external x86 386 "" i686-linux-android23-clang
build_android_external x86_64 amd64 "" x86_64-linux-android23-clang
