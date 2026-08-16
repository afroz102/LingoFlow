# benchmark/

Tracked evidence for the validation gates in [VALIDATION_PLAN.md](../docs/VALIDATION_PLAN.md). Nothing
in this directory is application code — it's the frozen protocol and the result records the
gates are checked against.

| File/dir | Populated at | Source template |
|---|---|---|
| `compatibility_matrix.csv` | Stage 0A (docs/IMPLEMENTATION_PLAN.md §2 items 10–11) | docs/VALIDATION_PLAN.md §9 "Process Text compatibility row" |
| `corpus/` | Stage 0B (docs/IMPLEMENTATION_PLAN.md §3 item 3) | docs/VALIDATION_PLAN.md §4.1 |
| `gate_0_result.md` | Stage 0A (docs/IMPLEMENTATION_PLAN.md §2 items 12–14) | Narrative findings: bugs, audit results, lifecycle cases |
| `gate_0_scorecard.md` | Stage 0A (docs/IMPLEMENTATION_PLAN.md §2 item 13) | docs/VALIDATION_PLAN.md §2.5 pass criteria, scored one by one |

Do not hand-edit rows to force a pass. A failing or partial row is a product finding
(docs/VALIDATION_PLAN.md §2.3), not a defect in the spreadsheet.
