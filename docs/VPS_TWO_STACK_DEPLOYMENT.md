# VPS two-stack deployment

The VPS deployment runs two complete BBC application stacks from the same
commit. Each stack has its own backend, frontend, PostgreSQL database volume,
document volume, and TLS certificate directory.

| Stack | Purpose | Frontend | Backend (localhost) | PostgreSQL (localhost) |
|---|---|---|---|---|
| `bbc-working` | working/test data | `https://HOST:20443` | `28080` | `25432` |
| `bbc-production` | clean production starting point | `https://HOST:20444` | `28081` | `25433` |

Switching databases is done by opening the other frontend URL. The browser
does not dynamically change a live database connection.

The stack definitions are `docker-compose.vps-working.yml` and
`docker-compose.vps-production.yml`. Secrets live only in the VPS `.env`
files and must never be committed.
