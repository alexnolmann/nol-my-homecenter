# Third-party components

The MIT license in this repository covers the project's own code and documentation. It does not replace the following upstream licenses or the terms of external data/media providers.

## Bundled files

- **Roboto Condensed Black** — Copyright 2011 The Roboto Project Authors, SIL Open Font License 1.1. The static font is derived from the variable upstream font. See `android/app/src/main/assets/fonts/OFL-RobotoCondensed.txt` and `README.md` in the same directory for the source, digest and reproduction tool.
- **Anton Regular** — Copyright 2020 The Anton Project Authors, SIL Open Font License 1.1. See `android/app/src/main/assets/fonts/Anton-OFL.txt`.
- **Gradle Wrapper** — upstream Gradle tooling. The generated wrapper script retains its Apache License 2.0 header and copyright notices. Its JAR is a build tool, not a private binary or signing key.
- **HARICA TLS RSA Root CA 2021** — public trust anchor in `android/app/src/main/res/raw/harica_tls_rsa_root_2021.der`, used for TLS compatibility on the legacy device. It contains a public certificate, not a private key or this installation's local CA.

The application's illustrations and the example concept are project assets; they do not contain uploaded user photographs. Keep upstream notices when distributing bundled fonts or build tooling.

## Installed dependencies

`server/composer.lock` records exact package versions and licenses. The current set is `nikic/fast-route` (BSD-3-Clause), `opis/json-schema`, `opis/string` and `opis/uri` (Apache-2.0). Composer installs their license files in `server/vendor`; this directory is not committed. Android's JUnit and JSON test dependencies and the Android/Gradle toolchains keep their own licenses.

Weather, CBR exchange rates, MOEX quotes, internet-radio streams and a user's MP3/photo library are external content, not licensed by this project's MIT license. Configure your own provider credentials and observe the applicable attribution and usage terms.
