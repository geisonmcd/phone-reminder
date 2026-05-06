# Play Store Publish Checklist

Local artifacts prepared in this repo:

- Signed bundle: `app/build/outputs/bundle/release/app-release.aab`
- Privacy policy page: `docs/privacy-policy.html`
- Short description: `play-store/short-description.txt`
- Full description: `play-store/full-description.txt`
- Release notes: `play-store/release-notes-en-US.txt`
- Data safety notes: `play-store/data-safety.md`
- App icon: `play-store/graphics/app-icon-512.png`
- Feature graphic: `play-store/graphics/feature-graphic-1024x500.png`
- Phone screenshots: `play-store/screenshots/*.png`

Still required in Play Console:

1. Create or open the app entry in Google Play Console.
2. Add app category and contact details.
3. Paste the short and full descriptions from the repo.
4. Upload the app icon, feature graphic, and phone screenshots.
5. Provide a public privacy policy URL that serves `docs/privacy-policy.html`.
6. Complete App access, Ads, Advertising ID, Government apps, financial features, and health apps declarations as applicable.
7. Complete Data safety using `play-store/data-safety.md`.
8. Complete Content rating questionnaire.
9. Complete Target audience and content.
10. Configure the closed testing track: countries/regions and testers.
11. Create a closed testing release, add the app bundle, paste release notes, review, and save.
12. Open Publishing overview, wait for quick checks, resolve any blocking issues, and submit all changes for review.

Recommended public privacy policy URL:

- GitHub Pages URL after enabling Pages for this repo:
  `https://geisonmcd.github.io/phone-reminder/privacy-policy.html`

See `play-store/release-runbook.md` for the step-by-step release flow.
