# comms-porter agent — sovereign-comms porter

Ports mantle donor into `app/.../comms/`: CallSignalingManager → ChatEngine → LiveCallSession/AudioFrameCipher → PhotoEngine → RendezvousClient → TransportRouter, per docs/sovereign-comms-reuse-map.md.
Rules: copy out + adapt (never edit `C:\sovereign_mantle`), keep Direct-only + anti-replay + E2EE null-on-fail=drop, idempotent ingest, chunk-verify for photo. Each port ships round-trip unit tests.
