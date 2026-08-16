# Section 28.3 browser E2E runner

This is isolated acceptance test infrastructure. It launches Chromium against
the `bbc-full-e2e` stack, logs in through the real UI, creates a uniquely
prefixed student/family through the supported registrar API, and removes the
fixture through supported soft-delete/deactivation endpoints in `finally`.

Every persona gets a new Playwright browser context and a temporary storage
state file. Storage states are never written to the repository or evidence.
The output contains only route results, status/diagnostic counts, and
non-secret fixture IDs; it never contains passwords, access tokens, or mailbox
bodies. The default persona set is bootstrap admin, Primary teacher, Secondary
subject teacher, Secondary titular, accountant, cashier, bursar, and a
disposable parent. The nurse persona is intentionally not included.

```powershell
Set-Location qa/browser-e2e
npm install
npm test
```

Defaults target `http://localhost:8100`, `http://localhost:8101/api`, and
Mailpit `http://localhost:8125/api/v1`. Override with `BBC_E2E_UI_URL`,
`BBC_E2E_API_URL`, `BBC_E2E_MAILPIT_URL`, or the persona-specific password
environment variables when running against another isolated fixture.

The runner uses the installed system Chrome when available. Override it with
`BBC_E2E_CHROME_PATH` if needed. It must only be run against the isolated E2E
tenant; it is not a production tenant-provisioning tool.

Set `BBC_E2E_PERSONAS=accountant,bursar,parent` for a bounded subset. Set
`BBC_E2E_ROUTE_INDEX=1` to visit the 35-route index. The route report keeps
protected optional 403s, missing optional photos, absent unpublished bulletins,
and no-teacher-identity probes as explicit classifications; unlisted
application diagnostics remain blocking.
