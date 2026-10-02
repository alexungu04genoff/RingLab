# Frozen 1.4.1 oracle

The four rule/calculator classes here are copies of production main `5655220`
captured before the CSV runtime switch. Only their package/imports changed.
`RuleFactsParityTest` first passed against the unchanged production classes, then
against the imported detached snapshot. Keep this oracle independent of the CSVs:
do not update it to make a changed fact pass parity.

The oracle is test-only and is never packaged as an application fallback. It
checks exact records (IDs, order, values, labels, explanations, sources, statuses,
coverage and totals), all gadgets and known/unknown racer/machine types, passive
pairs, every supported condition, unknown/false/true contexts, utility effects,
unsupported patches and the reviewed/assumed interaction limits.
