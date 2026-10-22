# Security

## Reporting a vulnerability

Please don't open a public issue for a security problem. Use GitHub's private vulnerability reporting instead: open the repository's **Security** tab and choose **Report a vulnerability**.

Include what you found, how to reproduce it, and which version of Plasma you used. Never include your provider's address, your username, your password or a portal MAC address.

## What counts

Plasma runs servers on the home network (the phone pages on port 8484) and on the TV itself (the stream hub on 127.0.0.1), stores provider logins, and can hold an ADB pairing with the TV. Problems in any of these are in scope, for example:

- a way for another device on the network, or another app on the TV, to control Plasma, read its streams or reach its ADB pairing without being paired;
- credentials, tokens or MAC addresses appearing in logs, diagnostics exports or backups without encryption;
- an update path that would install an APK not signed with Plasma's key.
