# Frozen Registry v1 reference

Source: https://github.com/alexungu04genoff/RingLab-GameData/tree/8422fb49c30546d83ffda81ee5fd3aad1f4a334c/contracts/v1

`publication.schema.json` and `publication-meta.schema.json` are byte-preserved
copies. Schema version: `1.0.0`. Publication schema file SHA-256:
`f89eb9fc7ec8b26c199e2f30781b2ee90d9e38bbd2dda2289ce44624681f9f8c`.

The consumer verifier under `adapter/out/registry` is adapted from the same
commit's Java contract verifier; canonicalization remains compatible with its
independent ECMAScript golden vectors. These copies never authorize modification
of the authoritative contract. Metadata verification uses the publication schema's
shared definitions without remote schema resolution.

Test resources under `src/test/resources/registry` contain unchanged upstream
`baseline-v1.json` and `golden-vectors.json`. Baseline source: RingLab
`1bb9459fa8caa02e3f536054cb4d61529739dab6`.

- Baseline file SHA-256: `886fd9c13c768c9427f1adf8a0dcdb78b189dcd7d36dfc0e967139783b739cb5`
- Publication checksum: `7fb52fb52848d13f798f290ea83192dea1cbb65212d3d96f7bd885bfc1257bff`
- Dataset fingerprint: `cb43981dee32d70c6f0266da1e002fb4ed55e2215ac9f9a1864d4b11e13c6ab9`

File hash and publication checksum have different preimages. Neither is a RingLab
PLAN/APPLY approval token. `.gitattributes` preserves reference bytes across platforms.
