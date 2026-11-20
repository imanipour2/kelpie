# Stable private owner signing for Android QA

CI debug signing identities can change between runners. Android requires a
[compatible signing identity](https://developer.android.com/studio/publish/app-signing)
for an installed package update. Reusing one local
identity avoids repeatedly replacing a private QA installation and losing its
pairing state just because the next CI build has a different debug certificate.

`scripts/qa/sign-private-owner-debug-apk.ps1` packages an existing MotherNext debug
APK for private owner use. It preserves the application's code, resources and
debug status. The output is **PRIVATE_OWNER_DEBUG**, with its CI origin retained.
The [production release signing contract](../api/native-browser-gateway-android.md#release-signing)
continues to require the configured release secrets and verified APK/AAB outputs.

## Run

Use Windows PowerShell 5.1 (`powershell.exe`) under the same owner account
on every run. Supply an explicit Java 17 installation containing `java.exe` and
`keytool.exe`, and Android SDK Build Tools 35.x. Java does not need to be on PATH.
Download the APK and `candidate-manifest.json` with its `.sha256` sidecar from the
reviewed, successful MotherNext CI run. Obtain the expected full commit SHA from
that run's source revision; the helper does not query GitHub or establish trust
in an arbitrary downloaded manifest.

```powershell
$signing = @{
    JavaHome = 'C:\Tools\java-17'
    BuildToolsPath = 'C:\Android\Sdk\build-tools\35.0.0'
    InputApk = 'C:\Artifacts\candidate\app-debug.apk'
    CIManifest = 'C:\Artifacts\candidate\candidate-manifest.json'
    ExpectedCommit = '<reviewed 40-character commit SHA>'
    OutputDir = 'C:\Artifacts\private-owner-signed'
}
.\scripts\qa\sign-private-owner-debug-apk.ps1 @signing
```

`OutputDir` must not already exist. Subsequent builds use a new output directory
and reuse the same owner key automatically. This helper performs no installation,
uninstallation, device access, backup, upload or release publication.

## Input and output verification

Before opening or creating a signing identity, the helper checks the manifest's
checksum, expected commit, repository/workflow, MotherNext profile, package
`com.kelpie.browser.r5mothernextverify`, port `18463`, APK hash and original CI
certificate. `aapt` independently checks the APK package, version and debuggable
flag. Versions are read from the verified manifest rather than fixed in the
helper. The port is CI provenance: the MotherNext workflow checks both generated
BuildConfig files, then hashes that APK. This is not a runtime port probe.

The helper aligns the APK before signing, verifies its v2 signature and owner
certificate, then checks 4-byte ZIP alignment with 16 KiB alignment for
uncompressed native libraries. It compares the names, lengths and SHA-256 hashes
of every ZIP entry except JAR signature metadata. Other `META-INF` content is
included. APK signing blocks are outside ZIP entries. The final package and
version are checked again, and the original APK remains unchanged.

This recipe explicitly enables APK v2 and disables v1, v3 and v4, matching the
existing CI v2 verification contract and the app's minimum Android version. No
signing-key rotation lineage is used. With v3 present and a minimum SDK of 28 or
newer, Android's verifier can select v3 without evaluating v2; that is not a failed
signature, but it cannot satisfy a report that specifically requires verified v2.
The helper uses normal verification against the APK's real minimum SDK and does
not weaken the signature check to accept an unverified scheme.

The output directory contains:

- `kelpie-private-owner-debug.apk` and its SHA-256 sidecar.
- `private-owner-debug-manifest.json` and its SHA-256 sidecar, recording both APK
  hashes, both signer fingerprints, the source commit, the complete original CI
  manifest, tool versions, helper hash and successful payload/alignment checks.
- `source-ci-manifest.json`, an exact copy of the original manifest.

The local manifest records installation and device acceptance as unperformed.
Successful packaging is not evidence that the app was installed or that its
browser connection and renderer work on a phone.

## Stable owner identity

The first successful identity creation writes a PKCS#12 RSA-3072 key, a Windows
DPAPI-protected random password and a public certificate fingerprint beneath
`%LOCALAPPDATA%\KelpieConnect\signing\owner-debug`. Access is restricted to the
current Windows user and SYSTEM, with inherited permissions removed. A file lock
prevents concurrent key creation. A complete new identity is published by an
atomic directory rename.

ACL updates change only the access rules through the .NET Framework API, leaving
audit rules and ownership unchanged. Existing signing state must already belong
to the current user. This requires no administrator elevation or audit privilege.

The password is decrypted only for signing under DPAPI `CurrentUser` and passed
to Java tools through their child-process environment. It is never passed on a
command line or written as plaintext. The private key remains in its encrypted
local keystore. Only the public certificate is read into memory for fingerprint
verification. The helper neither uploads nor exports a private key.

Keep this identity under the same Windows account for future builds. An
incomplete identity, abandoned generation directory, DPAPI decryption failure or
changed certificate fingerprint stops the helper; it never silently creates a
replacement. A Windows profile loss or deliberate key change requires an
explicit migration decision. A first switch from a different CI signer is also
an installation migration; this packaging helper does not make that decision.

## Packaging validation

On 2026-11-20, Windows PowerShell 5.1 parsed the helper without errors. A previously
successful CI artifact was independently matched to its GitHub archive digest,
then signed twice into separate outputs. Both runs verified v2, package/version,
alignment and all 225 non-signature payload entries. They produced the same APK
hash and certificate while the keystore, DPAPI password file and pinned public
fingerprint file remained byte-for-byte unchanged. An incorrect expected commit
was rejected with no output and no identity change. These results validate the
packaging path; they are not installation or rendering acceptance.

## References used

Reviewed 2026-11-20:

- [Android apksigner](https://developer.android.com/tools/apksigner): keystore
  signing, environment password inputs, explicit output and signature checks.
- [Android zipalign](https://developer.android.com/tools/zipalign): alignment
  before signing and `-P 16` verification.
- [Java 17 keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html):
  PKCS#12 key creation, `:env` password input and public-certificate export.
- [Windows ProtectedData](https://learn.microsoft.com/en-us/dotnet/api/system.security.cryptography.protecteddata.protect):
  DPAPI encryption scoped to the current user.
- [.NET Framework Directory.SetAccessControl](https://learn.microsoft.com/en-us/dotnet/api/system.io.directory.setaccesscontrol?view=netframework-4.8.1):
  persist only the modified access-control sections without rewriting audit rules.
- [AOSP ApkVerifier at da15f1a](https://android.googlesource.com/platform/tools/apksig/+/da15f1a63dd5f0c1fcf028d74f4ddaba958649d1/src/main/java/com/android/apksig/ApkVerifier.java):
  v3 selection can leave v2 unexamined when the minimum supported SDK is 28 or later.
