# Microsoft authentication setup

Fullmoon is a public desktop client. It uses Microsoft OAuth device code and authorization code
with PKCE, then exchanges the Microsoft token through Xbox Live, XSTS, and Minecraft Services. It
does not use or need a client secret.

## Register the application

1. Open [Microsoft Entra app registrations](https://entra.microsoft.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade)
   and select **New registration**.
2. Name the application `Fullmoon Launcher`.
3. Choose **Personal Microsoft accounts only**. If that choice is unavailable in the tenant, choose
   **Accounts in any organizational directory and personal Microsoft accounts**. The launcher uses
   the `/consumers` authority, so its sign-in screen still accepts personal Microsoft accounts only.
4. Finish registration, then copy the **Application (client) ID** from the Overview page. Do not use
   the Object ID or Directory (tenant) ID.
5. Open **Authentication**, select **Add a platform**, then **Mobile and desktop applications**.
6. Add the custom redirect URI `http://localhost`. The launcher listens on a random loopback port;
   Microsoft ignores the port when matching a `localhost` redirect URI.
7. Under **Advanced settings**, set **Allow public client flows** to **Yes**.
8. Open **API permissions**. If **Xbox Live → XboxLive.signin** (delegated) is not already listed,
   add it. The launcher requests that scope; Microsoft will not mint a token Xbox Live accepts
   without it.
9. Open **Manifest** and confirm these two values. Entra's Authentication Preview can leave them
   inconsistent, which then refuses personal Microsoft accounts:

```json
"signInAudience": "PersonalMicrosoftAccount",
"api": {
  "requestedAccessTokenVersion": 2
}
```

`requestedAccessTokenVersion` must be the number `2`, not the string `"2"`.

Microsoft's reference procedures are [desktop application configuration](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-configuration),
[redirect URI restrictions](https://learn.microsoft.com/en-us/entra/identity-platform/reply-url), and
[device authorization](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code).

## Configure Fullmoon

For distributable builds, place the Application (client) ID in the repository root `brand.json`:

```json
{
  "msClientId": "00000000-0000-0000-0000-000000000000"
}
```

Rebuild the launcher after changing `brand.json`; the build script embeds the value in the binary.
The client ID is a public application identifier and is safe to ship. Never add a client secret to
the repository or launcher.

For a local test without rebuilding, start the existing launcher from a shell that defines the
runtime override:

```powershell
$env:PINION_MS_CLIENT_ID = "00000000-0000-0000-0000-000000000000"
& ".\Fullmoon.exe"
```

```bash
PINION_MS_CLIENT_ID="00000000-0000-0000-0000-000000000000" ./fullmoon
```

## Verify the complete sign-in chain

Test both **Browser sign-in** and **Device code**, then confirm that the launcher returns the owned
Minecraft Java profile. A successful Microsoft or Xbox sign-in alone does not prove that Minecraft
Services accepts the application registration.

If the final Minecraft token exchange returns HTTP 403 with `Invalid app registration`, changing
the redirect URI or adding a client secret will not fix it. No Entra setting substitutes for
acceptance: the application has to be reviewed and allowlisted by Mojang. Do not copy another
launcher's client ID. New applications are submitted through the form at
`https://aka.ms/mce-reviewappid` (measured 2026-09-12: 200, a Microsoft Forms page);
`https://aka.ms/AppRegInfo` now redirects to Minecraft Help (article 16254801392141). Mojang answers
by email, and the message does not always state the decision — the token exchange itself is the
verdict.

Fullmoon's registration is accepted. On 2026-09-12 the client ID
`f5ccd6e4-9e7b-411f-ab67-21e8202adb93` completed the whole chain against the live services: device
code → Xbox Live 200 → XSTS 200 → `login_with_xbox` 200 → `profile` 200.
`launcher/scripts/check-app-registration.mjs` re-runs that check on demand — it signs in with the
device code flow and prints each step's HTTP status, never a token. Run it before a release, and
after a rebrand; a fork still needs its own accepted application.

## Local testing before Microsoft setup

On a clean profile, open **Accounts** and select **Create local test account**. Fullmoon creates the
deterministic offline identity `FullmoonTest` and selects it automatically. It works for launcher UI,
installation, launch, singleplayer, and offline/LAN testing. It cannot authenticate to online-mode
servers such as the production Fullmoon network.

The regular **Add account** dialog also accepts a custom offline name containing 1–16 ASCII letters,
digits, or underscores.
