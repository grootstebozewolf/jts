# What is the current SoT SHA and live named mint?

Research for [grootstebozewolf/jts#106](https://github.com/grootstebozewolf/jts/issues/106).
Inventory only. Not a mint. Does not pick which mint the bar pins to ([#109](https://github.com/grootstebozewolf/jts/issues/109)).

Fetched `origin` on 2026-08-22. `gh` against `grootstebozewolf/jts`.

## Short answer

| Question | Fact |
|---|---|
| SoT commit on `origin/feature/sfa-curve-rgr` | [`b7394fd4ae6ec3b2e65f1c65d5872456d674f865`](https://github.com/grootstebozewolf/jts/commit/b7394fd4ae6ec3b2e65f1c65d5872456d674f865) (`b7394fd4a`) |
| Same SHA as [PR #7](https://github.com/grootstebozewolf/jts/pull/7) `headRefOid` | yes |
| Latest named mint | [RC3](https://github.com/grootstebozewolf/jts/releases/tag/RC3) — `JTSTestBuilder-RC3.jar` SHA-256 `2c0431e970cfe26476c325bdf25e184bf24a450bdc912e37654aef1960d4efc7` @ [`569e64cf`](https://github.com/grootstebozewolf/jts/commit/569e64cff19d8d08b9db55ce8d01f82953ee59b8) |
| Live status of that mint | **RC3 UX HOLD.** No later mint (no RC4 tag, no RC4 release). |

SoT is two commits **ahead** of RC3 (merged [PR #98](https://github.com/grootstebozewolf/jts/pull/98) / [#97](https://github.com/grootstebozewolf/jts/issues/97)). RC3 remains the newest published named mint and is HOLD.

## SoT pin

`git ls-remote --heads origin feature/sfa-curve-rgr` and `gh pr view 7 --json headRefOid` both return:

```
b7394fd4ae6ec3b2e65f1c65d5872456d674f865
```

| | |
|---|---|
| Branch | `origin/feature/sfa-curve-rgr` |
| PR | [PR #7](https://github.com/grootstebozewolf/jts/pull/7) (open, base `master`) |
| Commit | [`b7394fd4ae6ec3b2e65f1c65d5872456d674f865`](https://github.com/grootstebozewolf/jts/commit/b7394fd4ae6ec3b2e65f1c65d5872456d674f865) |
| Subject | Merge pull request #98 from grootstebozewolf/fix/edit-vertex-cs-delete-phantom |
| Date | 2026-08-22T14:20:24+02:00 |
| Body | fix: CircularString vertex delete drops two controls so count stays odd (#97) |

`569e64cf` is an ancestor of `b7394fd4` (`git merge-base --is-ancestor` exit 0).

Commits on SoT not in RC3:

```
b7394fd4a Merge pull request #98 from grootstebozewolf/fix/edit-vertex-cs-delete-phantom
57bbf13f9 fix: CircularString vertex delete drops two controls so count stays odd (#97)
```

[PR #102](https://github.com/grootstebozewolf/jts/pull/102) (`fix/edit-vertex-first-contact`, #99 / #101 / #100) is **open**, not on SoT.

## Named mints (GitHub releases)

Four pre-releases, all on `grootstebozewolf/jts`. No drafts. No later tag than `RC3`. Maven coordinates stay `1.21.0-SNAPSHOT`. Not LocationTech JTS releases.

`JTSTestBuilder.jar` on each release is byte-identical to the versioned JAR (same SHA-256 in `SHA256SUMS.txt`).

### RC3 — latest published, UX HOLD

| | |
|---|---|
| Release | https://github.com/grootstebozewolf/jts/releases/tag/RC3 |
| Tag | `RC3` (annotated; peels to the commit below). Branch `mint/rc3` is the same SHA. |
| Published | 2026-08-22T11:57:06Z (prerelease) |
| Commit | [`569e64cff19d8d08b9db55ce8d01f82953ee59b8`](https://github.com/grootstebozewolf/jts/commit/569e64cff19d8d08b9db55ce8d01f82953ee59b8) — Merge pull request #96 |
| JAR | `JTSTestBuilder-RC3.jar` (5 703 832 bytes) — https://github.com/grootstebozewolf/jts/releases/download/RC3/JTSTestBuilder-RC3.jar |
| SHA-256 | `2c0431e970cfe26476c325bdf25e184bf24a450bdc912e37654aef1960d4efc7` ([SHA256SUMS.txt](https://github.com/grootstebozewolf/jts/releases/download/RC3/SHA256SUMS.txt)) |
| Notes | RC0 HOLD; RC1 and RC2 superseded. SIGN corpus grows (RC0+RC1+RC2 cases, then [#95](https://github.com/grootstebozewolf/jts/issues/95)). |

HOLD source: [#68 comment 5380341538](https://github.com/grootstebozewolf/jts/issues/68#issuecomment-5380341538) — **RC3 UX HOLD** on leftover [#97](https://github.com/grootstebozewolf/jts/issues/97) (Ctrl-right-click delete phantom vertex). “Do not SIGN RC3.” Reaffirmed [#68 comment 5380473426](https://github.com/grootstebozewolf/jts/issues/68#issuecomment-5380473426) after #98: first-contact leftovers #99/#101/#100 (PR #102) and #94 still open; **RC3 remains HOLD.**

### RC2 — superseded by RC3

| | |
|---|---|
| Release | https://github.com/grootstebozewolf/jts/releases/tag/RC2 |
| Tag | `RC2` (annotated). Branch `mint/rc2` is the same SHA. |
| Published | 2026-08-22T11:01:12Z (prerelease) |
| Commit | [`7cd60011ad882c5e8c26bb6aad828e3130571a7f`](https://github.com/grootstebozewolf/jts/commit/7cd60011ad882c5e8c26bb6aad828e3130571a7f) |
| JAR | `JTSTestBuilder-RC2.jar` (5 702 352 bytes) — https://github.com/grootstebozewolf/jts/releases/download/RC2/JTSTestBuilder-RC2.jar |
| SHA-256 | `65c1b94a2de5198b024e1ca7bc417ad81b6683cc6820fe88dcfbd1c57fea83ec` ([SHA256SUMS.txt](https://github.com/grootstebozewolf/jts/releases/download/RC2/SHA256SUMS.txt)) |

Minted on [#68 comment 5379878505](https://github.com/grootstebozewolf/jts/issues/68#issuecomment-5379878505). Superseded by RC3 ([#68 comment 5380239386](https://github.com/grootstebozewolf/jts/issues/68#issuecomment-5380239386)).

### RC1 — superseded by RC2

| | |
|---|---|
| Release | https://github.com/grootstebozewolf/jts/releases/tag/RC1 |
| Tag | `RC1` (lightweight) |
| Published | 2026-08-22T08:27:42Z (prerelease) |
| Commit | [`e78a5fb6f1130483461074dc55870d7dd62b3db4`](https://github.com/grootstebozewolf/jts/commit/e78a5fb6f1130483461074dc55870d7dd62b3db4) — Merge pull request #79 |
| JAR | `JTSTestBuilder-RC1.jar` (5 697 305 bytes) — https://github.com/grootstebozewolf/jts/releases/download/RC1/JTSTestBuilder-RC1.jar |
| SHA-256 | `26cad875b3626c937a43e7a1c17e51cc6ac5977c8a661cfd339a0d8cb7212d2b` ([SHA256SUMS.txt](https://github.com/grootstebozewolf/jts/releases/download/RC1/SHA256SUMS.txt)) |

### RC0 — HOLD (superseded as SIGN surface by RC1)

| | |
|---|---|
| Release | https://github.com/grootstebozewolf/jts/releases/tag/RC0 |
| Tag | `RC0` (lightweight) |
| Published | 2026-08-22T07:02:24Z (prerelease; tag created 2026-08-18T05:54:43Z) |
| Commit | [`a10708ed32eb85bfac6875912aea5c5ef3903262`](https://github.com/grootstebozewolf/jts/commit/a10708ed32eb85bfac6875912aea5c5ef3903262) — Merge pull request #67 |
| JAR | `JTSTestBuilder-RC0.jar` (5 692 645 bytes) — https://github.com/grootstebozewolf/jts/releases/download/RC0/JTSTestBuilder-RC0.jar |
| SHA-256 | `ca087c34750409daa9952ee4b1de392fe95a1dba40fb25ee42ab75ec8ce6a5a6` ([SHA256SUMS.txt](https://github.com/grootstebozewolf/jts/releases/download/RC0/SHA256SUMS.txt)) |

Built as the resolution of [#69](https://github.com/grootstebozewolf/jts/issues/69). Put **HOLD** when #74 / #76 / #78 landed as leftovers (see SIGN comments). Map [#68](https://github.com/grootstebozewolf/jts/issues/68) still says “RC0 remains HOLD” after later mints.

### Not a GitHub named mint

Guide pin `JTSTestBuilder-pr7.jar` @ `61eb3377` is the pre-RC0 SIGN surface named in closed `[visual-qa]` SIGNs. RC0 release notes: “Supersedes the guide pin `JTSTestBuilder-pr7.jar` @ `61eb3377`.” No GitHub release for that pin.

## SIGN comments

Search: `gh search issues --repo grootstebozewolf/jts --match comments SIGN` (and `HOLD`). PRs have no SIGN/HOLD comments. No comment on any RC mint is an actual **UX SIGN pass** (human visual close on that JAR/SHA). Closed `[visual-qa]` SIGNs name the **guide pin** `61eb3377`, not RC0–RC3.

### Map: Empty TestBuilder known-queue (#68)

| When | Comment | What it says |
|---|---|---|
| 2026-08-22T11:01:31Z | [5379878505](https://github.com/grootstebozewolf/jts/issues/68#issuecomment-5379878505) | RC2 minted. RC1 superseded. RC0 remains HOLD. SHA `7cd60011`, JAR SHA-256 `65c1b94a…ea83ec`. |
| 2026-08-22T11:57:41Z | [5380239386](https://github.com/grootstebozewolf/jts/issues/68#issuecomment-5380239386) | **RC3 minted.** SIGN surface RC3 `569e64cf` / `2c0431e9…d4efc7`. RC2 superseded. |
| 2026-08-22T12:07:05Z | [5380341538](https://github.com/grootstebozewolf/jts/issues/68#issuecomment-5380341538) | **RC3 UX HOLD** on [#97](https://github.com/grootstebozewolf/jts/issues/97). Do not SIGN RC3. |
| 2026-08-22T12:41:37Z | [5380473426](https://github.com/grootstebozewolf/jts/issues/68#issuecomment-5380473426) | After #98: PR #102 for #99/#101/#100; #94 still open. **RC3 remains HOLD.** |

Map body SoT pin is stale (still names RC1 `e78a5fb6`). Live pin is the comments above.

### SIGN-surface fan-out (RC0 → RC1 HOLD → RC2 → RC3)

Same mint-move text on the two SIGN tasks and the two original `[visual-qa]` tickets:

| Issue | RC0 surface | RC0 HOLD → RC1 |
|---|---|---|
| [#71](https://github.com/grootstebozewolf/jts/issues/71) UX SIGN Silent CurvePolygon drop | [5378856933](https://github.com/grootstebozewolf/jts/issues/71#issuecomment-5378856933) | [5379304283](https://github.com/grootstebozewolf/jts/issues/71#issuecomment-5379304283) |
| [#72](https://github.com/grootstebozewolf/jts/issues/72) UX SIGN Function param focus leak | [5378857234](https://github.com/grootstebozewolf/jts/issues/72#issuecomment-5378857234) | [5379304370](https://github.com/grootstebozewolf/jts/issues/72#issuecomment-5379304370) |
| [#56](https://github.com/grootstebozewolf/jts/issues/56) Silent CurvePolygon drop | [5378857501](https://github.com/grootstebozewolf/jts/issues/56#issuecomment-5378857501) | [5379304124](https://github.com/grootstebozewolf/jts/issues/56#issuecomment-5379304124) |
| [#60](https://github.com/grootstebozewolf/jts/issues/60) TB-FN | [5378857776](https://github.com/grootstebozewolf/jts/issues/60#issuecomment-5378857776) | [5379304212](https://github.com/grootstebozewolf/jts/issues/60#issuecomment-5379304212) |

RC2 / RC3 moves on the SIGN tasks (not repeated on #56/#60):

| Issue | RC2 | RC3 |
|---|---|---|
| #71 | [5379878504](https://github.com/grootstebozewolf/jts/issues/71#issuecomment-5379878504) | [5380239380](https://github.com/grootstebozewolf/jts/issues/71#issuecomment-5380239380) — re-run on RC3; do not SIGN RC2 |
| #72 | [5379878506](https://github.com/grootstebozewolf/jts/issues/72#issuecomment-5379878506) | [5380239421](https://github.com/grootstebozewolf/jts/issues/72#issuecomment-5380239421) — same |

RC0 HOLD → RC1 was also copied onto leftover tickets [#74](https://github.com/grootstebozewolf/jts/issues/74#issuecomment-5379304506), [#76](https://github.com/grootstebozewolf/jts/issues/76#issuecomment-5379304649), [#78](https://github.com/grootstebozewolf/jts/issues/78#issuecomment-5379304783). Those comments say “Do not SIGN RC0.”

[#95](https://github.com/grootstebozewolf/jts/issues/95#issuecomment-5380239376) names RC3 as the SIGN JAR for the new extract-clothoid case (`569e64cf` / `2c0431e9…d4efc7`). [#97](https://github.com/grootstebozewolf/jts/issues/97#issuecomment-5380340966) is the HOLD witness: RC3 screenshot is 6-control leftover; “RC3 remains HOLD.”

### Closed `[visual-qa]` UX SIGN passes (guide pin, not an RC)

These are SIGNs, but they name `JTSTestBuilder-pr7.jar` @ `61eb3377`, not RC0–RC3:

| Issue | SIGN comment |
|---|---|
| [#4](https://github.com/grootstebozewolf/jts/issues/4) FCP-H | [5308622242](https://github.com/grootstebozewolf/jts/issues/4#issuecomment-5308622242) UX SIGN 2026-08-16 on pr7 JAR `@ 61eb3377` |
| [#5](https://github.com/grootstebozewolf/jts/issues/5) FCP-MEM | [5309039016](https://github.com/grootstebozewolf/jts/issues/5#issuecomment-5309039016) UX SIGN 2026-08-16 on JAR 61eb3377 |
| [#6](https://github.com/grootstebozewolf/jts/issues/6) H-CC | [5308592805](https://github.com/grootstebozewolf/jts/issues/6#issuecomment-5308592805) UX SIGN 2026-08-16 on pr7 JAR `@ 61eb3377` |
| [#33](https://github.com/grootstebozewolf/jts/issues/33) TB-IN | [5309057495](https://github.com/grootstebozewolf/jts/issues/33#issuecomment-5309057495) UX SIGN 2026-08-16 on JAR 61eb3377 |

[#3](https://github.com/grootstebozewolf/jts/issues/3) close is “CCE only”; remaining FCP-S cases wait for UX SIGN. [#70](https://github.com/grootstebozewolf/jts/issues/70) is code research, explicitly not a UX SIGN.

Open `[visual-qa]` / SIGN-task comments that say “Needs UX SIGN” / “Do not close without UX SIGN” (no pass): #73, #80, #82, #84, #86, #88, #90, #92, #94, #95, #97, #99, #100, #101, plus #56/#60/#71/#72.

## What this does not claim

- Which named mint the MMF release bar should pin numbers to (open grilling [#109](https://github.com/grootstebozewolf/jts/issues/109)).
- That RC3 HOLD is cleared by SoT `b7394fd4` (PR #98 is on SoT, not minted).
- Any UX SIGN pass on RC0, RC1, RC2, or RC3.
- A new mint / JAR / tag.
