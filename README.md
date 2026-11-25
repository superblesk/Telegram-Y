# Telegram Y — Experimental fork of [Telegram X](https://github.com/TGX-Android/Telegram-X), achieving full/high parity over the standard Telegram client. Based on [TDLib](https://core.telegram.org/tdlib).

> [!IMPORTANT]
> The project has only just begun development. The first version will be released later. For now, you can build it from source—at your own risk.

> [!NOTE]
> Telegram Y is an unofficial project and is not affiliated with Telegram FZ-LLC or the Telegram X team.

![Telegram Y](/images/feature.png)

This is the complete source code and the build instructions for the alternative Android client for the Telegram messenger, based on the [Telegram API](https://core.telegram.org/api) and the [MTProto](https://core.telegram.org/mtproto) secure protocol via [TDLib](https://github.com/TGX-Android/tdlib).

- ~~[**GitHub Releases**](https://github.com/superblesk/Telegram-Y/releases)~~
- There are no releases yet

## Build instructions

### Prerequisites

* Repository must be fetched via `git`
* **JDK** or **[Android Studio](https://developer.android.com/studio/)** (with compatible bundled JDK)
* At least **8GB** of RAM
* At least **7,32GB** of free disk space when cloning with `--shallow-submodules --depth=1`
* At least **2-5x** times more disk space for files generated during build process.

#### macOS

* [Homebrew](https://brew.sh)
* git with LFS: `$ brew install git git-lfs && git lfs install`
* JDK: `$ brew install openjdk@25`
* [rustup](https://rustup.rs), then Rust of `version.rust` of `version.properties` with targets of Android: `$ scripts/setup-rust.sh`, which prints `rust.dir` for `local.properties`

#### Ubuntu

* git with LFS: `# apt install git git-lfs`
* Run `$ git lfs install` if you just installed `git-lfs`
* JDK: `# apt install openjdk-25-jdk`
* rustup, and gcc to link build scripts of Rust: `# apt install rustup gcc`, then Rust of `version.rust` of `version.properties` with targets of Android: `$ scripts/setup-rust.sh`, which prints `rust.dir` for `local.properties`. Run it again after a change of `version.rust`
* If multiple JDK versions are installed:<br/>`# update-java-alternatives --list`<br/>`# update-java-alternatives --set java-1.25.0-openjdk-amd64` (or other compatible version)

#### Windows

* [MSYS2](https://www.msys2.org/#installation) (**UCRT64** environment)
* Update packages: `pacman -Syu`
* Run `pacman -S --needed git mingw-w64-ucrt-x86_64-git-lfs perl make diffutils`
* Run `git lfs install`
* Run `git config --global core.longpaths true`
* [rustup](https://rustup.rs), then Rust of `version.rust` of `version.properties` with targets of Android: `scripts/setup-rust.sh`, which prints `rust.dir` for `local.properties`
* Clone the repository
* Set `msys2.dir` in `local.properties` after cloning
* Note: you have to use `\\` or `/` as path separator in properties files

### Building

1. `$ git clone --recursive https://github.com/superblesk/Telegram-Y tgy`
2. In case you forgot the `--recursive` flag, `cd` into `tgy` directory and run: `$ git submodule update --init --recursive`
3. Open project via **[Android Studio](https://developer.android.com/studio/)** or build manually from the command line: `./gradlew assembleLatestDebug`
4. If build fails, follow the instructions provided in the error message.

#### Publishing

1. [Obtain Telegram API credentials](https://core.telegram.org/api/obtaining_api_id)
2. Create `local.properties` file in the root project folder using any text editor:<br/><pre># Location where you have Android SDK installed
   sdk.dir=YOUR_ANDROID_SDK_FOLDER
   \# Home of rustup with Rust of version.rust, printed by scripts/setup-rust.sh
   rust.dir=YOUR_RUSTUP_HOME_FOLDER
   \# Telegram API credentials obtained at previous step
   telegram.api_id=YOUR_TELEGRAM_API_ID
   telegram.api_hash=YOUR_TELEGRAM_API_HASH</pre>
3. [Setup Firebase](https://firebase.google.com/docs/android/setup) and replace `google-services.json` with the one that's suitable for your `app.id`

### Commands

<details>
<summary>Reference of commands for building and maintenance</summary>

`-Pabi` limits native libraries to given ABIs, separated by commas: `arm64`, `arm32`, `x64` or `x86`. Without it, all ABIs are built. `Latest` in task names can be replaced with another flavor.

#### Building

Set up Android SDK, NDK and `local.properties`, on Windows in MSYS2:
```shell
scripts/setup.sh
```

Check that submodules and Git LFS files are fetched:
```shell
./gradlew :app:validateGitSetup
```

Build and install debug build on connected device:
```shell
./gradlew :app:installLatestDebug -Pabi=arm64
```

Build `legacy` flavor for Android 4.x, which requires older NDK:
```shell
./gradlew :app:assembleLegacyDebug -PuseLegacyNdk=true -Pabi=arm32
```

Build libvpx, FFmpeg and tlottie for all flavors and ABIs, add `-PuseLegacyNdk=true` for `legacy` flavor:
```shell
./gradlew :app:buildLibvpx
./gradlew :app:buildFfmpeg
./gradlew :app:buildTlottie
```

Builds of tlottie for `arm64-v8a` are optimized by the profile of its drawing, `app/jni/tlottie/arm64-v8a.profdata`, and in flavors with branch protection by `arm64-v8a-protected.profdata`, made for the host that builds releases. After updates of tlottie or Rust, builds warn that it doesn't match, and it's made again from animated stickers and emoji on a connected device of the ABI, see [`GenerateTlottieProfileTask`](buildSrc/src/main/kotlin/tgx/gradle/task/GenerateTlottieProfileTask.kt):
```shell
ANDROID_SERIAL=<device> ./gradlew ":app:generateTlottieProfile[arm64-v8a]" -Ptlottie.samples=<samples>
```

</details>

## License

`Telegram Y` is based on [Telegram X](https://github.com/TGX-Android/Telegram-X) by Vyacheslav Krylov and contributors, and is licensed under the terms of the GNU General Public License v3.0.

For more information, see [LICENSE](/LICENSE) file.

License of components and third-party dependencies it relies on might differ, check `LICENSE` file in the corresponding folder.

### Third-party dependencies

List of third-party components used in **Telegram Y** can be found [here](/docs/THIRDPARTY.md). Additionally you can check the specific commit of the third-party component used, for example, [here](/app/jni/thirdparty) and [here](/thirdparty).

## Contributions

**Telegram Y** welcomes contributions. Check out [pull request template](/docs/PULL_REQUEST_TEMPLATE.md), [code style](/docs/CODE_STYLE.md) and [guide for contributors](/docs/GUIDE.md) before creating the first pull request. These documents were inherited from Telegram X and describe its internals, which Telegram Y is based on.

If you found a bug or have a suggestion, please [open an issue](https://github.com/superblesk/Telegram-Y/issues). Please do not use this repository to ask general questions about Telegram: refer to [FAQ](http://telegram.org/faq) or contact [Telegram Support](https://telegram.org/faq#telegram-support).
