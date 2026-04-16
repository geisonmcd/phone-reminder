# Play Store Publish Checklist

Local artifacts prepared in this repo:

- Signed bundle: `app/build/outputs/bundle/release/app-release.aab`
- Privacy policy page: `docs/privacy-policy.html`
- Short description: `play-store/short-description.txt`
- Full description: `play-store/full-description.txt`
- Release notes: `play-store/release-notes-en-US.txt`
- Data safety notes: `play-store/data-safety.md`

Still required in Play Console:

1. Create or open the app entry in Google Play Console.
2. Upload `app-release.aab` to an internal, closed, or production track.
3. Add app category and contact details.
4. Paste the short and full descriptions from the repo.
5. Provide a public privacy policy URL that serves `docs/privacy-policy.html`.
6. Upload phone screenshots.
7. Complete Data safety using `play-store/data-safety.md`.
8. Complete Content rating questionnaire.
9. Complete App access, Ads, and Government apps declarations as applicable.
10. Review release notes and submit for review.

Recommended public privacy policy URL:

- GitHub Pages URL after enabling Pages for this repo:
  `https://geisonmcd.github.io/phone-reminder/privacy-policy.html`
