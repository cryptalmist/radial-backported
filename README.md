# Radial, ported and maintained

[Radial](https://github.com/velolib/radial) by [velo](https://github.com/velolib), ported and maintained for Minecraft versions that upstream's own releases don't cover.

Currently ported from **Radial 3.2.2**: same features, same config format, nothing dropped. Only the version-specific API calls were re-expressed for the older game versions. These ports aren't frozen — when upstream ships a new Radial version, they're updated to match.

Upstream source, issues and changelog live at [velolib/radial](https://github.com/velolib/radial). This fork exists only to host the ports, so `main` here is documentation — the source for each port is on its own branch, and each one is kept up to date with upstream.

![The Radial menu](assets/propho1.webp)

## Download

All builds are on the **3.2.2** release. Pick the row that matches your Minecraft version.

| Minecraft | Loader | Java | Ported by | Download |
| --- | --- | --- | --- | --- |
| 1.21.8 | Fabric | 21 | [ChenJL66666](https://github.com/ChenJL66666) | [radial-3.2.2+1.21.8.jar](https://github.com/cryptalmist/radial-backported/releases/download/3.2.2/radial-3.2.2%2B1.21.8.jar) |
| 1.21.11 | Fabric | 21 | cryptalmist | [radial-3.2.2+1.21.11.jar](https://github.com/cryptalmist/radial-backported/releases/download/3.2.2/radial-3.2.2%2B1.21.11.jar) |
| 26.1.2 | Fabric | 25 | cryptalmist | [radial-3.2.2+26.1.2.jar](https://github.com/cryptalmist/radial-backported/releases/download/3.2.2/radial-3.2.2%2B26.1.2.jar) |
| 1.21.1 | NeoForge | 21 | cryptalmist | [radial-3.2.2+1.21.1-neoforge.jar](https://github.com/cryptalmist/radial-backported/releases/download/3.2.2/radial-3.2.2%2B1.21.1-neoforge.jar) |

**Required:** Fabric API on the Fabric builds, NeoForge on the NeoForge build.

**Optional:** [YACL](https://modrinth.com/mod/yacl) for the in-game config screen, [MaLiLib](https://modrinth.com/mod/malilib), [ModMenu](https://modrinth.com/mod/modmenu).

Releases are named after the mod version, not the Minecraft version, so every jar for one mod version sits on a single release page.

## Branches

The source for each port is on its own branch. Every branch is maintained: when upstream releases a new Radial version, the port is updated to it and a new release is cut.

| Branch | Target | Notes | Ported by |
| --- | --- | --- | --- |
| `fabric-1.21.8` | Fabric 1.21.8 | Feature-complete port of 3.2.2, built on top of the 1.21.11 port | [ChenJL66666](https://github.com/ChenJL66666) — [PR #1](https://github.com/cryptalmist/radial-backported/pull/1) |
| `fabric-1.21.11` | Fabric 1.21.11 | Base port for the 1.21.x line | cryptalmist |
| `fabric-26.1.2` | Fabric 26.1.2 | Downport of the 26.x line | cryptalmist |
| `neoforge-1.21.1` | NeoForge 1.21.1 | NeoForge port of 3.2.2 | cryptalmist |

Upstream's own 26.3 code was on `main` before it became this page; it's still reachable in the history, and current upstream releases are on [Modrinth](https://modrinth.com/mod/radial).

## Building from source

```sh
git clone -b fabric-1.21.8 https://github.com/cryptalmist/radial-backported
cd radial-backported
./gradlew build
```

Swap `fabric-1.21.8` for the branch you want. The jar lands in `build/libs/`.

Every push to a port branch builds in CI and attaches the jar to the current mod-version release automatically, so the download table above can't go stale.

## AI tooling

These ports were written with AI coding agents rather than by hand. Which agent did what:

- **OpenCode** — the `fabric-1.21.11`, `fabric-26.1.2` and `neoforge-1.21.1` ports, and ongoing maintenance.
- **DeepSeek Harness** — the `fabric-1.21.8` port by [ChenJL66666](https://github.com/ChenJL66666).

Agent output is not hand-verified line by line. The code compiles and the jars are built by CI, but these ports are **not exhaustively tested in-game** — expect rough edges, and please report what breaks.

## Credits

- **Radial** by [velo](https://github.com/velolib) — the upstream project these ports are derived from.
- **Ports and ongoing maintenance by cryptalmist** (`fabric-1.21.11`, `fabric-26.1.2`, `neoforge-1.21.1`), produced with [OpenCode](https://opencode.ai).
- **Fabric 1.21.8 port by [ChenJL66666](https://github.com/ChenJL66666)**, produced with an AI agent and smoke-tested in-game, merged in [PR #1](https://github.com/cryptalmist/radial-backported/pull/1).

## License

GPL-3.0, the same as upstream — see [LICENSE](LICENSE). These ports are derivative works of Radial and remain under its license.