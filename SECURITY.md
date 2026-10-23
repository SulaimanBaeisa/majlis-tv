# Security

Mashrabiya changes the TV's home screen. Treat it like any system-level tool.

- Report vulnerabilities privately: GitHub -> Security -> "Report a vulnerability".
- Never post logs containing your TV's IP, Wi-Fi name, or serial number in a public issue.
- The app never uninstalls anything. It disables the stock launcher, recommendations and backdrop
  with `pm disable-user`; `Original` re-enables them.
- Supported: Android TV / Google TV 9-14. Rooted or modified firmware is out of scope.
- Signing: the release key is the app's identity and is never committed. The keystore that signed the
  pre-public builds was never in git; only its historical development password appeared in an old
  `build.sh`. It is public knowledge and is treated as burned: the key file is what protects releases.
  Rotation requires a documented migration because Android refuses updates signed by another key.
