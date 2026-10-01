# fixtures/ — synthetic only

`synthetic-only`

Never real child/family names, numbers, photos, GPS, or addresses.

Every text fixture in this tree carries the literal token `synthetic-only`;
`tools/verify_project.py` enforces it, so a real identifier pasted in here
fails the gate rather than becoming a committed device identity.

Conventions: identities `DAD_TEST` / `KID_TEST`, numbers `+1-555-0100/0101`, fake geo `44.9778,-93.2650` (obviously-fake constant). Photo fixtures: generated placeholders (e.g., solid-color WEBP), never real camera files.
`inbox/` (gitignored tray for human device pulls) — do not commit.
