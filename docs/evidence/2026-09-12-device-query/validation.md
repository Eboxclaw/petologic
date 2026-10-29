# Device skill — device_query cluster (plan 12 step ②, first slice) (2026-09-12)

## What shipped

The Device domain's read tool: `device_query` with subjects `battery` (level + charging/full state), `connectivity` (Wi-Fi / mobile data, metered flag, VPN transport) and `device` (manufacturer, model, Android release, security patch). Pure grammar and formatters live next to the skill definition; `DeviceStatusReader` supplies real BatteryManager/ConnectivityManager values. Registered as a third skill — AUTO default, read-only, one tool — so Orchestration shows it with its own Off/Auto/Always selector and per-tool toggle. New normal permission `ACCESS_NETWORK_STATE` declared; role manifest whitelist and bundled manifest extended with `device.query` (the privilege-expansion guard stays intact).

## Tests

- 69 JVM tests green (7 new): grammar accept/reject, battery formatter (charging/discharging/full/unknown), connectivity formatter (transport, metered, VPN), phone formatter, activation (wakes on battery/wifi questions, silent on unrelated topics, AUTO default), and a scripted Koog loop driving `device_query` end-to-end through `PaladinoAgent` to its envelope.
- Instrumented `DeviceIntegrationTest` on `Paladino_API35`: real battery, connectivity and phone reads return `OK|device.query|…`; unknown subject returns `ERROR|invalid_command|`.

## UI

`skills-list-device.png`: Skills & MCPs now lists Memory (Always), Security Guard (Auto) and Device (Auto).

## Limits

No battery-health/temperature or storage readings yet; no device *action* tool (plan 12's device_action — vibration, settings panels — is a later slice). Router terms are word-boundary matched and may over-trigger rarely ("sinal"); tuning follows real usage.
