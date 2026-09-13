# Touch Grass

Android app that blocks your chosen apps once their daily time budget runs
out, and only unblocks them when you photograph the mission Gemini set for
you that day.

The Android project lives in [`touch-the-grass/`](touch-the-grass/) - open
that folder in Android Studio, not the repository root.

**[Full documentation, setup and API key instructions →](touch-the-grass/README.md)**

## Quick start

```bash
cp touch-the-grass/local.properties.example touch-the-grass/local.properties
# add GEMINI_API_KEY=your_key_here, then open touch-the-grass/ in Android Studio
```

A free key comes from https://aistudio.google.com/apikey. `local.properties`
is gitignored, so the key is never committed.

## Licence

MIT - see [LICENSE](LICENSE).
