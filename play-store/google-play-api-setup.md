# Google Play API Setup

Use this once to let GitHub Actions upload releases to the Play Console closed testing track.

## 1. Create or link an API project

1. Open Play Console.
2. Go to `Setup > API access`.
3. Link an existing Google Cloud project or create a new one.
4. Enable the Google Play Android Developer API if Play Console asks for it.

## 2. Create a service account

1. In the linked Google Cloud project, open `IAM & Admin > Service accounts`.
2. Create a service account.
3. Create a JSON key for it.
4. Save the downloaded JSON outside the repository.

## 3. Grant Play Console permissions

1. In Play Console, open `Setup > Users and permissions`.
2. Invite the service account email from the JSON field `client_email`.
3. Grant app access for `Smart Random Reminder`.
4. Grant release permissions for testing tracks.

## 4. Store the JSON in GitHub

Run this from the repository root, replacing the path with the downloaded JSON key:

```sh
gh secret set PLAY_SERVICE_ACCOUNT_JSON --repo geisonmcd/phone-reminder < path/to/service-account.json
```

The existing release workflow will then upload new builds to the closed testing `alpha` track.
