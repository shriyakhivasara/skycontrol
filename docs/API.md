# API contract

The web pages fetch `/api/state` every 500 ms. All responses are JSON unless noted.

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/state` | | clock, policy, flights[], runways[], gates[], queue[] |
| POST | `/api/event` | `{"type":"EMERGENCY","flight":"EK512"}` or `{"type":"CLOSE_RUNWAY","runway":"12R"}` or `{"type":"OPEN_RUNWAY","runway":"12R"}` or `{"type":"WEATHER","minutes":20}` | `{"ok":true,"message":"..."}` or `{"ok":false,"error":"..."}` |
| POST | `/api/policy` | `{"policy":"FCFS"}` or `{"policy":"PRIORITY_FUEL"}` | `{"ok":true}` |
| GET | `/api/report` | | avgDelay, onTimePct, diversions, runwayUtilisation, landings, departures |
| GET | `/api/export` | | CSV file download |

## `/api/state` shape

```json
{
  "clock": "07:15",
  "minute": 75,
  "policy": "PRIORITY_FUEL",
  "flights": [
    {"flight":"EK512","airline":"Emirates","type":"ARRIVAL","other":"BOM",
     "aircraft":"A380","category":"Widebody","status":"HOLDING","priority":"LOW_FUEL",
     "fuel":17.0,"scheduled":"07:10","actual":null,"delay":null,"runway":null,"gate":null}
  ],
  "runways": [{"name":"12L","open":true,"lastFlight":"FZ201","operations":14}],
  "gates": [{"id":"A1","size":"LARGE","occupant":"EK512"}],
  "queue": ["EK512","FZ201"]
}
```
