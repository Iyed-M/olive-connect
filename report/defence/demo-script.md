# Ten-minute defence demo

Use the production-shaped stack (`docker compose -f deploy/docker-compose.yml
up --build`) and a seeded Admin plus prepared accounts. Keep a browser window
at 1280 px and one at 390 px. The timings are a guide, not extra narration.

| Time | Demonstration | Alternative / proof point |
|---:|---|---|
| 0:00–0:45 | Open the public map, filter the Sfax area, and open the registration form. | Submit an invalid CIN and show the field error; submit a valid request. |
| 0:45–1:30 | Log in as Admin and approve the registration request with member number and Delegation. | Show that a disabled User cannot log in and that the Admin page is role-protected. |
| 1:30–2:30 | Log in as Farmer, complete the forced password change, open the guided tour, and use contextual help. | Open another Farmer's orchard URL and show the ownership-safe 404. |
| 2:30–3:20 | Create an Orchard with GPS/map position and two Plots; show computed totals. Declare an URGENT HarvestRequest. | Use an out-of-range coordinate or invalid plot and show 400 validation. |
| 3:20–4:10 | Switch to Collection manager, inspect the priority queue, reject with a reason, then validate a second request. | Attempt a duplicate active request and show 409; inspect the notification timeline. |
| 4:10–5:00 | Plan a CollectionRound with Workers, Equipment and a Mill; show the map and booking warning. | Choose a conflicting resource or low-capacity Mill and show the actionable warning. |
| 5:00–5:50 | Log in as Team leader on the phone, start the round, navigate a stop, record weight, and complete it. | Disconnect the network, create a stop draft, reconnect, and show FIFO retry. |
| 5:50–6:35 | Log in as Mill manager, open incoming rounds, record a MillReception with bin, gross/tare and quality. | Exceed capacity or submit a duplicate bin weighing and show the API error. |
| 6:35–7:20 | Log in as Logistics manager, show Worker/Equipment lists, Unavailability, calendar and SupplyOrder. | Mark a supply received or try a duplicate resource code. |
| 7:20–8:05 | As Farmer, submit a geolocated FieldAlert; as Collection manager, acknowledge/resolve it and show the live event. | Deny geolocation and show orchard-position fallback; upload an oversized photo. |
| 8:05–8:45 | Open Admin imports/settings and show rejected rows; open the dashboard as Collection or Mill manager. | Use a bad import row and show `ImportError`; show Mill manager KPI scope. |
| 8:45–9:30 | Open Grafana, Prometheus health and the backup/restore evidence. | Explain the scratch-container restore and the backup metric. |
| 9:30–10:00 | Point to the report, traceability matrix, class views and API catalogue. | Answer one architecture question and one security question from the oral pack. |

## Setup checklist

1. Copy `deploy/.env.example` to `deploy/.env` and use non-production demo
   values.
2. Start the production-shaped Compose stack and wait for the API healthcheck.
3. Seed or create one account for each Role; prepare one second Farmer for the
   ownership alternative.
4. If the demo includes offline execution, open the Team leader page before
   disabling the network so the service worker/browser state is visible.
5. Run `scripts/collect-evidence.sh --base-url http://localhost` after the
   walkthrough if screenshots and API evidence are required.
