# Password recovery

`POST /api/auth/forgot-password` accepts `{email}`. The response is always HTTP 200
with the same generic confirmation for verified local accounts, unknown addresses,
Google-only accounts and unverified accounts. Email is lowercased with `Locale.ROOT`,
as during registration; it is not trimmed or otherwise rewritten. Only verified
accounts with an existing local password can receive a reset email. Linked Google
identities are preserved. Email verification remains a separate flow.

`POST /api/auth/reset-password` accepts `{token,password,confirmPassword}`. Registration
and recovery share `PasswordPolicy`: 8–72 characters and no more than 72 UTF-8 bytes,
without trimming. Successful reset returns a message, never a session. Unknown,
expired, consumed and replaced links receive the same safe HTTP 400 error.

## Persistence and concurrency

Flyway V29 adds `users.auth_version`, initially zero, and `password_reset_tokens`.
The token table has one row per user, a unique SHA-256 digest, creation/expiry times,
and cascading deletion on account removal. Tokens contain 32 cryptographically
random bytes encoded as 43 URL-safe characters. Only the digest is persisted.

Both issuance and consumption lock the user row. Consumption looks up the digest,
locks its user, then rechecks the digest and expiry after acquiring that lock.
Password change, version increment and token deletion share one transaction.
This also serializes two requests for an account that has no token yet. Concurrent
consumption has exactly one winner; replacement invalidates the old link.

Every normal JWT issuer includes `authVersion`. `CurrentUser` compares it with the
database on protected account operations. A missing claim deliberately means version
zero: existing sessions remain usable after migration until the first password reset.
Reset increments the version, invalidating old local, Google and legacy sessions.
Requesting an email does not invalidate sessions. Normal logout remains client-side.

## Mail and abuse controls

The separate `PasswordResetSender` port uses the existing Quarkus Mailer and SMTP
settings. Links use `PUBLIC_BASE_URL`, and messages include their expiry and an
instruction to ignore an unsolicited request. Development/tests use mock mail.
Mock-mail body logging is suppressed so reset and verification links do not enter logs.

| Setting | Default | Purpose |
| --- | --- | --- |
| `PASSWORD_RESET_TTL` | `PT30M` | Link lifetime; positive and at most 24 hours |
| `PASSWORD_RESET_COOLDOWN` | `PT1M` | Minimum time before an outstanding token can be replaced; less than TTL |
| `RATE_LIMIT_FORGOT_PASSWORD` | `5/PT1H` | Dedicated client-IP token bucket |
| `RATE_LIMIT_RESET_PASSWORD` | `10/PT1M` | Dedicated client-IP token bucket |

During cooldown, the same confirmation is returned without another email. After it,
a request replaces the previous token. Rate-limit rejection uses the existing 429
and `Retry-After` contract. These buckets are process-local, like existing limits.

Delivery runs inside the issuance transaction. A mailer failure raises the existing
external-service exception, rolling back the new token or restoring its predecessor.
The REST boundary logs only a constant warning and returns the generic confirmation:
returning 503 only for eligible addresses would disclose account eligibility.
There is no artificial timing delay; synchronous mail can still affect response time.
SMTP and PostgreSQL cannot commit atomically: a delivered message can contain an
unusable link if the database subsequently fails. The user can request another link.

## Browser flow

Login links to `/forgot-password`; emailed links open `/reset-password?token=...`.
Both pages use existing auth styling, accessible status/errors and duplicate-submit
guards. The reset token is never put in localStorage/sessionStorage or rendered as
page text. It remains in the incoming URL until success so refresh/session loading
does not destroy the link. Success replaces that history entry, clears any current
client session, and offers normal login. The HTML referrer policy is `no-referrer`.
Keep reset URLs/query strings out of proxy access logs and analytics; do not enable
request-body logging for auth endpoints. No analytics are added by this feature.

## Safe professor demonstration

Use only a local disposable account and local database. Keep
`QUARKUS_MAILER_MOCK=true`; never configure a real SMTP account for the demonstration.
The automated `PasswordResetIntegrationTest` creates unique `@example.test` accounts,
captures messages through `MockMailbox`, uses an offline Google verifier and removes
only records that it created. It proves the entire flow without external delivery.

For an interactive demo with the local dev stack, create a disposable account through
registration, open the captured verification message in the Quarkus Dev UI mailbox,
and verify it. Use a password chosen for this demonstration, not real credentials.

1. Log in with the original password; create a build and save it.
2. Use **Forgot password?** and submit the disposable email address.
3. Open the captured reset message in the local dev mailbox (never copy it to logs).
4. Open its local reset link and enter a new password twice.
5. Show the success screen and normal **Log in** action.
6. Show that the old password fails and the new password succeeds.
7. Open the existing build and Saved Builds to show their contents are unchanged.
8. Reopen the same link and submit again: it is no longer usable.
9. A second browser session opened before reset must also reauthenticate.

If the Dev UI mailbox is unavailable, run/debug the integration test and inspect
`MockMailbox.getMailsSentTo(user.email())` at the `resetToken` helper; do not enable
body logging as a workaround. The test provides the same demonstration assertions.

## Verification and rollout

Point both Quarkus test datasource variables and packaged `DB_URL`, `DB_USER`,
`DB_PASSWORD` at a disposable database; disable Dev Services if supplying one.
Never run acceptance tests against a shared or production database.

```text
mvn -f backend/pom.xml -Dtest=PasswordResetServiceTest,CurrentUserTest,RateLimitTest,PasswordResetIntegrationTest test
mvn -f backend/pom.xml verify
cd frontend
npm run test:coverage
npm run build
```

V29 must migrate before the new application serves requests. Existing identities and
content need no rewrite or fixture regeneration. Deploy backend and frontend together
when approved, with the correct public frontend URL and existing SMTP credentials.
Do not roll back to a backend that ignores `authVersion` after resets have occurred:
it would accept older sessions until their original JWT expiry.
