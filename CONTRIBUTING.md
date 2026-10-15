# Contributing

Thanks for wanting to help. This is a small personal project, so please keep expectations relaxed. I read
everything, but replies can take a while.

## Where things go
| | Where |
|---|---|
| Bugs | [GitHub Issues](https://github.com/KalleBoy39/prismatic-geodes/issues) |
| Code contributions and fixes | [GitHub Pull Requests](https://github.com/KalleBoy39/prismatic-geodes/pulls) |
| Builds, screenshots, addons, forks, ideas and casual contact | Discord: DM **dubbelmonterad** |

Discord is optional and not an official support channel. Everything that needs tracking (bugs, fixes) belongs on
GitHub.

## Bug reports
Use [GitHub Issues](https://github.com/KalleBoy39/prismatic-geodes/issues) and pick **Bug report**. The most useful
things to include:
- Minecraft, Fabric Loader, Fabric API and Prismatic Geodes versions,
- your other mods (the list from the crash report is fine),
- the crash report or `logs/latest.log` (for the locator: `data\logs\locator.log`),
- what you did, what you expected and what happened instead.

Please check [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) first; most "all geodes are purple" reports are explored
chunks or another worldgen mod.

## Ideas
Pick **Feature suggestion** on GitHub, or just tell me on Discord. The project's direction is "vanilla-like": new
things should feel like they could be in Minecraft and must not change how often geodes generate. Ideas that need big
new systems will probably be declined. That's nothing personal; it's about keeping the mod small.

## Pull requests
For anything bigger than a small fix, open an issue first, so we can agree on the idea before you spend time on code.

The code is under the [MIT License](LICENSE). By sending a pull request you agree that your contribution is licensed
under the same MIT terms. Textures and data derived from Minecraft or Vanilla Tweaks are **not** MIT and follow their
own terms (see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)); please don't add third-party assets you don't have
the right to share.

Before you open a pull request:
- run `gradlew build` (it runs the game tests) and, for visual changes, `gradlew runClientGameTest`,
- keep gameplay vanilla-like and keep purple = vanilla amethyst,
- don't commit generated output, world saves, logs or anything from your own `.minecraft`.

## Forks and addons
You are welcome to fork the code, change it or build addons on top of it under the MIT licence. Keep in mind that the
Minecraft- and Vanilla Tweaks-derived textures keep their own terms. If you make something, I'd love to see it on
Discord.

## IDs that must not change
The mod ID `vplus_geodes`, the addon ID `vplus_geodes_miniblocks` and every block, item, feature, recipe, tag and trim
material ID under them are saved inside players' worlds. Renaming any of them makes those blocks turn into air and
those items disappear. They come from the project's working title and stay as they are, even though the public name is
Prismatic Geodes. The Java package `dev.vplus` is internal and stays too.

## AI assistance
The project is developed with extensive help from Claude Code (see [CREDITS.md](CREDITS.md)). Contributions made with
AI tools are welcome. Please say so in the pull request, and test the result in game like any other change.
