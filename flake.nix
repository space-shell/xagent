{
  description = "paseo-chat — r1-style Paseo launcher for the Bluefox NX1";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };
        android = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "35" ];
          buildToolsVersions = [ "35.0.0" ];
          includePlatformTools = true;
          includeEmulator = false;     # use Android Studio's emulator, or add true (large closure)
          includeNDK = false;
          includeSystemImages = false;
        };
        jdk = pkgs.jdk17;
      in {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk
            gradle
            kotlin
            android-tools   # adb, fastboot
            scrcpy
            android.androidsdk
          ];
          ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
          JAVA_HOME = "${jdk}";
          GRADLE_OPTS = "-Dorg.gradle.daemon=false";
        };
      });
}
