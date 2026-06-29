# Agent-signer flow: end-to-end runbook

This is the authoritative guide for running the full agent-signer flow against
this KMP Compose-Multiplatform demo: an autonomous agent acts under a scoped,
user-delegated Ed25519 authority; its over-limit call is rejected on-chain; it
escalates to a coordination server; the user reviews and approves in the app;
the call is re-submitted under the user's Default rule; and the agent learns the
outcome by polling.

The subsystem is all-Kotlin. Three processes cooperate:

- A Ktor coordination server — `:smart-account-demo:coordination-server`
  (`kotlin("jvm")` + `application`, Ktor CIO, no `:stellar-sdk` dependency; it is
  a pure relay).
- A Kotlin reference agent — `:smart-account-demo:reference-agent`
  (`kotlin("jvm")` + `application`, depends on `:stellar-sdk` and connects
  headlessly via `connectToContract`), run with the Gradle `run` task.
- The Compose Multiplatform demo UI, living in `:smart-account-demo:shared` and
  surfaced by the web, Android, and iOS shells.

Everything below targets Stellar **testnet**. The agent's `AgentDefaults` and the
app's `DemoConfig` share the same testnet network, RPC, verifier, relayer, and
token defaults; they are testnet-only and public by design. Steps 2 and 4 are
device-only: they create the smart account, delegate to the agent, and approve
the escalation with a real passkey (WebAuthn), so they run in the app by hand.

The recommended manual-test target is the **web app**: passkeys work on
`localhost` out of the box, the same way the Flutter web demo does, so no device
or simulator entitlement setup is needed.

## Prerequisites

- A JDK that can run Gradle 9.0 (JDK 17 or newer). Both new modules emit **JVM 17
  bytecode** (`jvmTarget = JVM_17`) to match the `:stellar-sdk` JVM variant; the
  toolchain JDK that runs the build may be newer.
- The repo's Gradle wrapper (Gradle 9.0) and Kotlin 2.2.20 / Compose
  Multiplatform 1.9.1, both pinned by the build. Use `./gradlew` so the wrapper
  version is honoured.
- Node and npm on `PATH` for the web app: `:webApp:viteDev` runs `npx vite dev`.
- **No SDK override is needed.** This is the `sa-improvements` branch, which
  already carries `connectToContract` (the headless smart-account connect). The
  demo and the reference agent depend on `:stellar-sdk` in the same build
  (`implementation(project(":stellar-sdk"))`), so there is no path/version
  override to undo before release.

## Shared configuration

Every value below must agree across the three processes or the flow breaks. The
static network/verifier/token values already share a default between the agent's
`AgentDefaults` and the app's `DemoConfig`; the per-run identity values are
produced during the flow and copied between processes.

| Value | Coordination server | Reference agent | Demo app | Must agree because |
|-------|---------------------|-----------------|----------|--------------------|
| Coordination URL | binds `0.0.0.0:<port>` (`--port`/`PORT`, default 8787) | `AGENT_COORDINATION_URL` | `DemoConfig.COORDINATION_URL` (default `http://localhost:8787`) | agent posts and app polls the same server |
| Coordination token | `--token`/`COORDINATION_TOKEN` (required) | `AGENT_COORDINATION_TOKEN` | `DemoConfig.COORDINATION_TOKEN` (default `dev-token-change-me`) | every `/requests*` call is bearer-authenticated |
| Network passphrase | — | `AGENT_NETWORK_PASSPHRASE` | `DemoConfig.NETWORK_PASSPHRASE` | same network (defaults match) |
| RPC URL | — | `AGENT_RPC_URL` | `DemoConfig.RPC_URL` | same testnet RPC (defaults match) |
| Ed25519 verifier | — | `AGENT_ED25519_VERIFIER` | `DemoConfig.ED25519_VERIFIER_ADDRESS` | the external Ed25519 signer verifies against the same verifier contract |
| Smart account | — | `AGENT_SMART_ACCOUNT` (`C...`) | the connected account's "Contract address" | the agent connects to the account it was delegated on |
| Agent seed | — | `AGENT_SECRET_SEED` (raw 64-char hex, secret) | — | the agent signs with this key |
| Agent public key | — | derived from the seed (raw 64-char hex) | pasted into Delegate to Agent | the app registers it as the Ed25519 external signer |
| Scoped token | — | `AGENT_TOKEN_CONTRACT` (default XLM SAC) | the token chosen in Delegate to Agent | the call must hit the one token the rule scopes |
| Spending cap vs. amount | — | `AGENT_AMOUNT` (default `1`) | the cap entered in Delegate to Agent | **`AGENT_AMOUNT` must EXCEED the cap** so the call is policy-rejected |
| Destination | — | `AGENT_DESTINATION` (`G...`/`C...`) | — | the transfer recipient |
| Relayer URL | — | `AGENT_RELAYER_URL` | `DemoConfig.DEFAULT_RELAYER_URL` | gasless submission via the same relayer (defaults match) |

Because the agent's static defaults already mirror the app, a normal run only
sets the per-run identity values (`AGENT_SMART_ACCOUNT`, `AGENT_SECRET_SEED`,
`AGENT_DESTINATION`) plus an over-cap `AGENT_AMOUNT`.

Run the Gradle commands below from the repository root (the directory that holds
`gradlew`).

## Step 0 — Start the coordination server (preflight)

Pick a token (for local development the app and agent default to
`dev-token-change-me`). The server binds `0.0.0.0` and is reachable from the same
host and from other hosts on the LAN. Run it with the `run` task:

```sh
./gradlew :smart-account-demo:coordination-server:run \
  --args="--token dev-token-change-me --port 8787"
```

A bearer token is mandatory; the server exits with code 64 on a configuration
error (including a missing/empty token) and with code 70 if a configured store
cannot be loaded. `--port` defaults to 8787. For a request set that survives a
restart, add `--store` (loaded on start if present, written atomically after
every mutation):

```sh
./gradlew :smart-account-demo:coordination-server:run \
  --args="--token dev-token-change-me --port 8787 --store ./requests.json"
```

The equivalent environment variables are `COORDINATION_TOKEN`, `PORT`, and
`COORDINATION_STORE` (CLI flags take precedence). Setting them in the shell and
running the task without `--args` works too, because the Gradle `run` task
(a `JavaExec`) inherits the launching environment:

```sh
COORDINATION_TOKEN=dev-token-change-me PORT=8787 \
  ./gradlew :smart-account-demo:coordination-server:run
```

Confirm the server is up:

```sh
curl http://localhost:8787/health   # -> {"status":"ok"}
```

## Step 1 — Get the agent's public key (bootstrap)

Before a full live config exists, obtain the agent's identity. Print-key mode is
selected by `AGENT_PRINT_KEY=true` in the environment or by the `--print-key`
CLI flag. With no seed set, it generates a fresh keypair and prints BOTH the seed
(to copy into the agent config, keep secret) and the 64-hex public key (to paste
into the app):

```sh
AGENT_PRINT_KEY=true ./gradlew :smart-account-demo:reference-agent:run
```

The `--print-key` flag is equivalent and does not depend on environment
forwarding:

```sh
./gradlew :smart-account-demo:reference-agent:run --args="--print-key"
```

Look for the `[agent] [KEY]` lines:

```
[agent] [KEY] Generated a new agent Ed25519 keypair.
[agent] [KEY] AGENT_SECRET_SEED (copy into the agent config, keep secret): <64-hex>
[agent] [KEY] Agent public key (paste into Delegate-to-agent): <64-hex>
```

To re-derive the public key for a seed you already hold (the secret is never
printed back), supply the seed via `AGENT_SECRET_SEED` — print-key mode reads the
seed from the environment only:

```sh
AGENT_PRINT_KEY=true AGENT_SECRET_SEED=<64-hex> \
  ./gradlew :smart-account-demo:reference-agent:run
```

Keep the seed secret; share only the 64-hex public key with the wallet.

## Step 2 — Create/connect the account, then delegate to the agent (device-only)

Run the web app and delegate to the agent from inside it:

```sh
./gradlew :smart-account-demo:webApp:viteDev
```

The Vite dev server serves at `http://localhost:9003` by default (the port is
`VITE_PORT`, default `9003`, in `webApp/vite.config.js`; set `VITE_PORT` to
change it). Passkeys work on `localhost` with no extra setup.

The app reads the coordination URL and token from `DemoConfig.COORDINATION_URL`
/ `DemoConfig.COORDINATION_TOKEN`, which default to `http://localhost:8787` /
`dev-token-change-me` — the same defaults the server and agent use, so a local
run needs no override.

In the app:

1. **Create or connect a smart account** with a passkey.
2. From the **Context Rules** screen, tap **Delegate to Agent** and:
   - paste the agent's 64-hex public key from step 1 into the
     **Agent Ed25519 Public Key (hex)** field;
   - set the **Token Contract** to the scoped token (defaults to the demo token);
   - set a **small** spending cap under **Spending Limit** (this is what the
     agent must exceed), and pick a period;
   - choose an expiry under **Rule Expires In** (for example 1 day);
   - submit with the passkey.

This installs one on-chain context rule that scopes the agent to one token via a
`CallContract(token)` scope plus an Ed25519 external signer (the pasted key),
caps its spend with a spending-limit policy, and expires on its own via
`validUntil`. The coordination server is not involved in this step.

After the rule is installed, the delegate result card shows the **Agent Key**
and exposes the scoped **Token Contract** (tap to copy — this is the agent's
`AGENT_TOKEN_CONTRACT`) and the transaction **Hash** as copyable values. Copy
the value the agent needs from the app's wallet status card: the account
**Contract address** (`C...`). The agent connects headlessly by contract address
alone — it holds no passkey credential.

## Step 3 — Configure and run the agent so its call is rejected

Set `AGENT_AMOUNT` ABOVE the cap from step 2 so the spending-limit policy rejects
the call. The agent connects headlessly via `connectToContract`, registers its
Ed25519 key as an external signer, attempts the scoped `transfer` through the
multi-signer pipeline, classifies the rejection, escalates it (`POST /requests`),
and polls. The live run is gated by `AGENT_RUN_LIVE=true` **in the environment**,
so set the values as environment variables; the `run` task forwards them:

```sh
AGENT_RUN_LIVE=true \
AGENT_SMART_ACCOUNT=C...           # account "Contract address" from step 2 \
AGENT_SECRET_SEED=<64-hex>         # the agent seed from step 1 \
AGENT_DESTINATION=G...             # transfer recipient (G... or C...) \
AGENT_AMOUNT=1000                  # MUST exceed the delegated cap \
AGENT_COORDINATION_URL=http://localhost:8787 \
AGENT_COORDINATION_TOKEN=dev-token-change-me \
  ./gradlew :smart-account-demo:reference-agent:run
```

Without `AGENT_RUN_LIVE=true` the executable prints usage and touches nothing. If
the scoped token is not the default XLM SAC, also set `AGENT_TOKEN_CONTRACT` to
the same token chosen in step 2 (and `AGENT_TOKEN_DECIMALS` if it is not 7) — a
call to a different token is not governed by that rule. The agent logs the
rejection code, posts the escalation, prints the request id, and begins polling
(`AGENT_POLL_INTERVAL_SECONDS` × `AGENT_POLL_MAX_ATTEMPTS`, default 3s × 40).

## Step 4 — Review and approve in the app (device-only)

Open the approval inbox from the badged **bell** in the app's top bar — it shows
the pending count and is kept current by an 8-second poll while a wallet is
connected. Tapping it pushes the **Approval Inbox** screen:

1. The pending escalation appears with the decoded call (target, function,
   recipient, amount) and the rejection reason. The recipient and amount are
   decoded from the call arguments that actually execute and are authoritative;
   the server's `amount` field is display-only.
2. **Approve** it. The app rebuilds the exact same call and re-submits it under
   the user's **Default rule** (single-signer passkey, gasless via the relayer),
   then reports the resulting transaction hash back to the coordination server
   (`POST /requests/{id}/approve`). Rejecting instead opens a note dialog and
   posts `POST /requests/{id}/reject`. If the on-chain submission confirms but
   reporting it back fails, the card offers **Retry report** — the call is never
   re-submitted, only the report-back is retried.

## Step 5 — The agent learns the outcome

The agent's poll sees the request resolve to `approved` with the `resultHash` and
returns `AgentResult.EscalationApproved(requestId, resultHash, errorCode)`. The
agent does NOT re-submit — the app did, under the Default rule. A rejection in the
inbox returns `AgentResult.EscalationRejected(...)`; no resolution within the poll
budget returns `AgentResult.EscalationPending(...)`. A scoped call that is not
policy-rejected returns `AgentResult.CallSucceeded(hash)` or
`AgentResult.CallFailed(message)` without any escalation.

## Where the UI lives

The agent-flow UI — the **Delegate to Agent** screen, the **Approval Inbox**, and
the badged bell in the top bar — lives in the Compose Multiplatform
`:smart-account-demo:shared` module and is surfaced by the shells that host
Compose: web, Android, and iOS. The pending-count poller runs in the shared
`App()` composable.

The macOS app (`macosApp/`) is a native SwiftUI shell that bridges to the shared
business logic but renders its own SwiftUI screens; it does not host these Compose
screens, so the bell and inbox may not appear there. Drive the device-only steps
(2 and 4) from the web app (recommended), or from the Android or iOS shell.

## Troubleshooting

- **Server unreachable / connection refused.** Confirm step 0 is running and
  `curl http://localhost:8787/health` returns `{"status":"ok"}`. On a physical
  device, `localhost` is the device itself — point the app at the host machine's
  LAN IP (override `DemoConfig.COORDINATION_URL`, or run the server and app on the
  same host) and check the host firewall allows the port. The server already binds
  `0.0.0.0`.
- **`401 Unauthorized` on `/requests*`.** The token differs across processes. The
  server's `--token`/`COORDINATION_TOKEN`, the agent's `AGENT_COORDINATION_TOKEN`,
  and the app's `DemoConfig.COORDINATION_TOKEN` must be identical.
- **No rejection — the call succeeds instead of escalating.** `AGENT_AMOUNT` is at
  or below the delegated cap, so the spending-limit policy permits it. Raise
  `AGENT_AMOUNT` above the cap, or lower the cap in a new delegation. Also confirm
  `AGENT_TOKEN_CONTRACT` matches the token the rule scopes; a call to a different
  token is not governed by that rule.
- **Account mismatch / agent cannot connect or sign.** `AGENT_SMART_ACCOUNT` must
  be the exact "Contract address" of the account you delegated on in step 2, and
  the agent's 64-hex public key (derived from `AGENT_SECRET_SEED`) must be the key
  you pasted into Delegate to Agent. Re-derive it with
  `AGENT_PRINT_KEY=true AGENT_SECRET_SEED=<64-hex> ./gradlew :smart-account-demo:reference-agent:run`
  (step 1).
- **The agent ignores a changed environment variable.** The Gradle daemon can
  reuse a cached environment between runs. If a re-exported value does not take
  effect, pass it via `--args` where the mode supports it, or run the task with
  `--no-daemon`.
- **Agent stops with `EscalationPending`.** No one approved within
  `AGENT_POLL_INTERVAL_SECONDS` × `AGENT_POLL_MAX_ATTEMPTS` (default 3s × 40 ≈
  2 min). Approve sooner, or raise the poll budget.
