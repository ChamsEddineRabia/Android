# Streamloom product and player design brief

Design Streamloom as a serious, independent Android television player for 2027. It should feel authored by a small product studio with taste, not generated from a streaming-app template. The product is for people who already have access to a TV service and want a reliable, quiet way to use it.

## Product experience

- Make the first action obvious: connect a provider portal and the device address registered with that provider.
- Show only channels and categories actually returned by the connected service. Never invent sample programs, counts, profiles, premium entitlements, or guide data.
- Keep Home, Live, Saved, and Settings shallow and predictable. Search belongs directly in Live. A person should never lose the main navigation behind Android's status or gesture bars.
- Explain connection errors in plain language and preserve the portal details on the device so the owner can reconnect.
- Use generous touch targets, readable labels, clear focus/pressed states, and layouts that work one-handed on a phone.

## Visual direction

- Build a dark, inky interface with cool neutral surfaces, precise hairline borders, off-white type, and one restrained signal-coral accent.
- Use typography, spacing, and small identity details to create character. Avoid neon gradients, glassmorphism, floating blobs, stock-poster grids, fake dashboards, marketing copy, and decorative controls without behavior.
- The logo is a compact woven signal: one continuous pale ribbon crossed by a coral thread. It must remain legible at launcher-icon size and as a small header mark. Draw it as a clean vector, with no wordmark baked into the icon.
- Treat empty states as useful product states: explain what is missing and provide one direct next action.

## Player direction

- Make playback feel immersive and current: hide system bars while video is playing, keep the picture unobstructed, and reveal a custom control layer on tap that fades away again.
- The controls should include a clear close action, channel identity, live state, a large play/pause target, audio and captions access, and a fit/fill choice. Do not show a fake seek bar for live television.
- Use accessible contrast, generous controls, clean motion, and familiar behavior. Recover from playback errors without trapping the viewer.
- Preserve Android back, rotation, screen sizing, and transient system-bar gestures.

## Technical boundaries

- Use only the provider-issued portal and MAC address supplied by the device owner. Do not bypass device authorization, paywalls, DRM, or provider restrictions.
- Keep network and playback work off the UI thread. Use native Android video playback for the resolved stream.
- Handle Android 15+ edge-to-edge insets explicitly so the app header, bottom navigation, dialogs, and player controls do not sit beneath system UI.
- Keep the code small, inspectable, and straightforward to maintain.

Deliver a real Android APK. The connection and playback must use actual provider responses; visual polish must not depend on demo data.
