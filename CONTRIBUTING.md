# Contributing

1. `bash tests/run.sh` -> `ALL-PASS`
2. `bash tests/check_surface.sh` -> `SURFACE-OK` (permissions and hosts are frozen on purpose)
3. `python3 -m unittest discover -s tests/tools -v` -> OK
4. One change per commit; explain *what the TV does differently*, not which file changed.
5. New network host or permission = automatic rejection unless the PR explains why and updates
   `tests/check_surface.sh`.
6. Never commit a keystore, an APK, `config.properties`, or Thmanyah font files.
