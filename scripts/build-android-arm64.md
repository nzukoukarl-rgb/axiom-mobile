# Build precompiled arm64-v8a (CI / PC — never on phone)

```bash
# Host requirements: Rust pinned, Android NDK, cargo-ndk
rustup target add aarch64-linux-android
cargo install cargo-ndk --version 3.5.4   # pin in CI

export ANDROID_NDK_HOME=/path/to/ndk/26.x
cd axiom-mobile

cargo ndk -t arm64-v8a -o android/app/src/main/jniLibs \
  -p axiom-android build --release

# Produce hashes
sha256sum android/app/src/main/jniLibs/arm64-v8a/*.so > build-manifest.sha256
```

APK packaging: open `android/` in Android Studio or `./gradlew assembleRelease` once Gradle wrapper is added.
