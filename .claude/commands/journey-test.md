---
name: journey-test
description: Run the Verification recipe of a docs/journeys/ file and report PASS / DRIFT / SKIP. Arg is a journey id (e.g. silent-auto-hide), "all", or a keyword; empty defaults to "all".
---

Invoke the `journey-tester` subagent to verify a user journey against the running emulator. Pass the user's argument verbatim as the target:

- If the argument is empty, tell the subagent `target: all`.
- Otherwise pass `target: <argument>` verbatim.

Relay the subagent's final report back to the user without paraphrasing. If the subagent opened a PR, include the URL in the last line.
