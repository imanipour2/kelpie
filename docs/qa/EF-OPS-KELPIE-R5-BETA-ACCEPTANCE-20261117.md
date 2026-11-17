# R5 owner-only beta acceptance — not a public release

Baseline physical S24 QA: [Issue #98](https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6949610319). Samsung SM-S928B Android17; previous R5 APK SHA256 `193d85676e504d8da978b8557fe837787bc8af8a03cd53dc6af3fb42bf897862`.

| Gate | State |
| --- | --- |
| URL × clear and 48dp toolbar | CODED / HARDWARE TEST OPEN |
| Isolated beta APK `.r5betaverify` port18461, preserve previous app | BUILD/INSTALL OPEN |
| Owner native grant, no bypass, local STOP/403 replay | PREVIOUS BASELINE PASS / NEW BUILD OPEN |
| MCP tabs.new/close/select in background, no Activity activation | CODED / INSTRUMENTED TEST OPEN |
| Real device DOM/screenshot/click/scroll after tab switching | OPEN |
| Login reported successful by owner; cancel/PKCE callback regression | PARTIAL OWNER EVIDENCE / OPEN |
| Lock/call/Doze/network/reboot and Termux re-entry | OPEN |
| 8 P0 audit security, iOS parity or approved exception | BLOCKED |
| Public release | NO_GO |

Never uninstall/wipe `com.kelpie.browser.r5fullverify` during this isolated QA. Keep the existing Termux/tunnel 18460 untouched until separate authorization.
