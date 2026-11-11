# Fullmoon 1.0.2 beta RC1 integration

Prepared on 2026-11-11 KST. This branch is a **release candidate**, not an
approved tag, deployed backend or public release.

## Branch provenance

- Base: upstream Fullmoon Client `origin/main` at `bfe89dc`.
- Imported the existing, uncommitted launcher changes from
  `feat/beta102-launcher` without changing that checkout.
- Imported the existing, uncommitted Fabric mod changes from `feat/beta102-mod`
  without changing that checkout.
- Resolved the combined cross-subsystem HUD contract: the mod's newly
  registered `season` element is represented in launcher ordering,
  `catalog.json`, the Rust HUD contract, the standalone mock, both translations
  and the editor's visible sample. The mod's player-facing HUD label is
  localized. This prevents the 3 frontend and 3 Rust regressions observed
  immediately after integrating the two independent worktrees.

## Verified commands

From this checkout, on Fedora 45:

- `cd i3/mod && ./gradlew --no-daemon --max-workers=2 --rerun-tasks
  build check jacocoTestReport --console=plain`: success, 503 JUnit tests,
  zero failures/errors/skips; Java compile and coverage gates passed.
- `cd launcher && npm ci && npm test && npm run build`: success, 85
  frontend tests, zero failures, TypeScript check and Vite build passed.
- `cd launcher/src-tauri && cargo test --workspace`: 128 Rust tests passed,
  zero failures, one ignored.
- `cargo clippy --workspace --all-targets --all-features -- -D warnings`
  and `cargo fmt --all -- --check`: success.
- `git diff --check`: success.

The pinned development artifact bundle is maintained outside Git at
`~/.local/state/fullmoon-beta102/rc1-candidate/` with `SHA256SUMS`
for the combined mod, rebuilt FullmoonBridge, beta CoinBridge and beta proxy.
The mod's declared version remains `3.3.0-beta.1`; the launcher is
`1.3.0-beta.1`. The network version is independently `1.0.2-beta.1`.

## Outstanding release gates

1. An approved feature-branch push and manual GitHub Actions `CI` run on
   `windows-latest` must produce an NSIS installer and pass
   `.github/scripts/windows-smoke.ps1`. A successful local Linux build
   is **not** a Windows installer test.
2. Confirm a real authenticated Minecraft client's lobby -> isolated beta ->
   lobby round trip, native season UI, zero stale bridge/HUD state and
   resource pack detection on the actual approved beta service.
3. The beta service must install the reviewed FullmoonBridge candidate and
   retain a documented beta-only rollback before claiming native season
   support. The isolated Paper/PostgreSQL rehearsal succeeded but did not
   deploy to the running beta backend.
4. Review the final changelog, commit/tag naming, binary checksums and
   GitHub Release assets before public publication.

**Do not announce RC1 while any of these gates remain unverified.**
