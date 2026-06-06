---
description: Stop the local stack — full teardown or pause to free resources
---
Stop the Concert Ticket Booking System running on local Kubernetes.

Ask the user which they want (or infer from their wording):
- **Full teardown** (delete everything, including ephemeral data):
  `cd infrastructure && ./teardown.sh`  (runs `kubectl delete namespace ticketing`).
- **Pause** (stop pods, keep the setup for a fast restart):
  `kubectl -n ticketing scale deploy --all --replicas=0`
  Resume later with `kubectl -n ticketing scale deploy --all --replicas=1`.

Then remind the user: the `VmmemWSL` memory belongs to the WSL2 / Docker Desktop VM,
which keeps running after teardown. To release it, quit Docker Desktop and, if needed,
run `wsl --shutdown` in PowerShell. Built images stay cached, so the next start is quick.

Confirm with `kubectl get ns ticketing` (should report not found after a full teardown).
