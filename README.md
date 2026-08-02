<div align="center">

<img src="docs/kefe-mark.svg" width="100" alt="Kefe" />

# Kefe

**Offline-first net-worth tracker for two people — one Kotlin codebase on Android, iOS and desktop.**

Gold, silver, foreign currency, funds, equities and cash in one portfolio.
Live prices from public sources, everything stored on-device first, cloud sync optional.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-000000?style=flat-square&logo=apple&logoColor=white)
![Desktop](https://img.shields.io/badge/Desktop-JVM-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![SQLDelight](https://img.shields.io/badge/SQLDelight-2.3-FF6F00?style=flat-square&logo=sqlite&logoColor=white)
![Supabase](https://img.shields.io/badge/Supabase-3FCF8E?style=flat-square&logo=supabase&logoColor=white)
![Ktor](https://img.shields.io/badge/Ktor-087CFA?style=flat-square&logo=ktor&logoColor=white)

</div>

---

## Screens

| Summary | Assets | Goals | Market |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/summary.png" width="200" /> | <img src="docs/screenshots/assets.png" width="200" /> | <img src="docs/screenshots/goals.png" width="200" /> | <img src="docs/screenshots/market.png" width="200" /> |

| Asset detail | Goal detail | Biometric lock | Activity |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/asset-detail.png" width="200" /> | <img src="docs/screenshots/goal-detail.png" width="200" /> | <img src="docs/screenshots/lock.png" width="200" /> | <img src="docs/screenshots/activity.png" width="200" /> |

<div align="center">
<img src="docs/screenshots/desktop.png" width="700" /><br/>
<sub>Same code, adaptive layouts — desktop and tablet</sub><br/><br/>
<img src="docs/screenshots/tablet.png" width="420" />
</div>

---

## The mark

<img src="docs/kefe-progress.svg" width="440" alt="Goal progress" align="right" />

A catenary — a chain hanging under load. The deeper it sags, the closer the goal.

It isn't a circular arc: in a catenary the curvature concentrates at the bottom, in an
arc it's constant everywhere. An arc reads as a *smile*, a chain reads as *tension*.
The `cosh`/`sinh` math in `ChainCurve.kt` exists to preserve exactly that difference,
and the mark simplifies in four steps as it shrinks — at 16dp only the load remains.

<br clear="right" />

---

## Architecture

```
composeApp/src/
├── commonMain/          ~90% of the code, shared across all three targets
│   ├── ui/              screens · components · 8 hand-drawn charts · brand
│   ├── domain/          pure Kotlin: Position, Valuation, CostBasis, Returns, Goal
│   ├── data/
│   │   ├── db/          SQLDelight — the single source of truth
│   │   ├── remote/      Ktor: TEFAS, TCMB, free market, equities, Supabase
│   │   └── sync/        PushEngine · PullEngine · SyncCoordinator
│   ├── security/        expect: SecureStore, BiometricGate
│   └── di/              Koin
├── androidMain/         actual: Keystore, BiometricPrompt, OkHttp
├── iosMain/             actual: Keychain, LocalAuthentication, Darwin
└── desktopMain/         actual: JVM SQLite driver, CIO
```

MVI with one immutable `UiState` per screen. **The database is the source of truth, not
the network** — repositories return `Flow` from SQLDelight, and a price refresh is a
*write* to the local DB. That's what makes offline not a special case, just the normal
case with one writer switched off.

---

## Decisions worth reading

**Multi-tenancy was built, then deliberately torn out.** Accounts, invitations,
membership permissions — all of it worked, and all of it went. The real requirement was
one household: *one Supabase account, two devices, each pinned to a profile.* That
deleted the invite flow, the permission matrix and the need for a domain. Generality you
don't need is surface area you have to defend.

**Token encryption shipped *before* sync, on purpose.** The session token sat in the
database in plaintext. Tolerable while the account held nothing — but the moment sync
landed, that token would mean the entire savings history. `SecureStore` came first, and
its `reveal()` returns unrecognised text as-is, so old plaintext sessions keep working
and silently re-encrypt. No migration, no forced logout.

**"Offline" was two different facts wearing one label.** The sync chip was driven by
*price freshness*: when a free price endpoint hiccuped, the app declared itself offline
while cloud sync was working fine — and wrote records to disk stamped *pending*. Price
staleness and cloud reachability are now separate signals (`CloudState` derives from
real Supabase round-trips).

**Four price sources, because one won't do.** TCMB for official FX (keyless, falls back
to the previous bulletin so weekends work) · free market for gold and silver, because a
quarter coin *cannot* be derived from the ounce — it carries mint premium and
craftsmanship, so computed values come out systematically low · TEFAS for funds, fetching
a one-month series since day/week/month change needs consecutive points · one equities
source covering BIST, US and Europe by symbol suffix. All keyless, all fragile: each is
wrapped in retry, and on failure the last known price stays on screen with a staleness
banner.

**Domain modelling follows real life, not the schema.** Karat is asked where the user
knows it and implied where the form dictates it — previously someone holding 22k *gram*
gold had to file it as "jewellery", so the arithmetic was right but the screen lied.
`Lot` is a separate unit from `Piece` because a quarter coin doesn't divide but a share
does. Profit is shown in lira, not percent: a spreadsheet wants percentages, a person
wants "you're up ₺4,200".

**Charts are hand-drawn on Compose `Canvas`.** No chart dependency — the libraries that
ship Android views don't compile to iOS or desktop, and the ones that do meant adopting
someone else's animation and theming model.

---

## Testing

25 test classes, and the project rule is explicit: **a step is done only when verified on
a device *and* by a test.** Looking at a screenshot doesn't count.

Coverage sits where bugs are expensive and silent — money arithmetic (`CostBasis`,
`Valuation`, `Returns`, `Precision`, `ForeignMoney`, `Projection`), the sync engines
(`PushEngine`, `PullEngine`, `SoftDelete`, `AuthSession`, three Realtime suites), and
every fragile third-party parser (`TefasDate`, `StockParse`, `StockDate`,
`StockCurrency`, `PriceFreshness`).

---

## Stack

Kotlin 2.4 Multiplatform · Compose Multiplatform 1.11 + Material 3 + Navigation 3 ·
SQLDelight 2.3 · Ktor 3.5 (OkHttp / Darwin / CIO, WebSockets) · kotlinx.serialization ·
Coroutines + Flow · Koin 4.2 · Supabase (PostgREST, Auth, Realtime, RLS) ·
Android Keystore & iOS Keychain

Versions are pinned in `gradle/libs.versions.toml`, each with a comment explaining *why*
that exact version — which releases were broken, which constraint set the floor.

---

## Running it

Requires JDK 17+, Android SDK 37, Xcode 15+ for iOS.

```bash
git clone https://github.com/BcanGRG/Kefe.git && cd Kefe

./gradlew :composeApp:assembleDebug     # Android
./gradlew :composeApp:run               # Desktop
./gradlew :composeApp:desktopTest       # Tests
```

Cloud sync is optional — skip it and everything stays on device. To enable: create a
Supabase project, run `supabase/schema.sql` (tables, RLS policies, Realtime publication),
add your project URL and anon key, then sign in from Settings.

---

<div align="center">

**Burak Can Görgülü** · Android Developer

[![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)](https://github.com/BcanGRG)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-0A66C2?style=flat-square&logo=linkedin&logoColor=white)](https://linkedin.com/in/burakcangorgulu23)

</div>
