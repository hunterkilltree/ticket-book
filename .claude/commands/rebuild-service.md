---
description: Rebuild and redeploy one or more services after a code change
argument-hint: <service> [<service>...]
---
Rebuild and roll out the service(s): $ARGUMENTS

Steps:
1. `cd infrastructure`.
2. Rebuild the image(s): `./build-images.sh $ARGUMENTS`. This recompiles (Gradle deps
   are cached, so it is fast) and repackages only the named service runtime image(s).
   If you know the shared code did not change and only want to repackage, use
   `SKIP_BUILDER=1 ./build-images.sh $ARGUMENTS`.
3. Roll the deployment so the new image is picked up:
   `kubectl -n ticketing rollout restart deploy/$ARGUMENTS`
   (run once per service if multiple).
4. Watch `kubectl -n ticketing rollout status deploy/<service>` and confirm it is healthy.

If no service was given in $ARGUMENTS, ask which service(s) to rebuild.
