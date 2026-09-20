# Workflow State — MyTube

- **Active Stage:** Loop Execution Cycle 1
- **Phase Target:** Phase 4: NewPipeExtractor Integration & Multi-Source Fallback Pipeline
- **Policy:**
  - Build strictly on GitHub Actions CI (No local APK building).
  - Standalone `arm64-v8a` APK artifacts only.
  - Unified Shared Cache (`mytube-shared-cache-`).
  - Strict cycle: `team-exec -> team-verify -> team-fix`.
