# CleanYourTV Fire

[English](README.md) | **Italiano**

Un launcher Home per Fire TV Stick 4K (2ª generazione, Fire OS 8 / Android 11) che funziona anche su Android TV.
Fork personale di [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv). Su Fire OS il launcher di
Amazon resta la home di sistema protetta; `HomeKeyService` ne copre la finestra con questo launcher e lascia in
pace le Impostazioni. Non viene disattivato né disinstallato nulla.

## Funzioni

- **Dock e griglia** delle app, da 4 a 8 tessere per riga; le app senza banner TV ne ricevono uno ricavato
  dall'icona.
- **Evidenziazione del focus** in cinque stili (vetro, morbido, bordo, alone, neon) che scorre tra le tessere;
  velocità regolabile.
- **Sfondo**: immagini tue, uno del giorno da Picsum o Bing, opacità da 0 a 100%, orologio e meteo.
- **Riga Continua a guardare** sotto il dock (Android TV): le voci Watch Next delle altre app, suggeriti dei
  canali facoltativi e, su Fire OS, le ultime app aperte. Tieni premuto OK su una scheda per nasconderla.
- **Pillola di rete** con Wi-Fi, oppure Ethernet solo quando il cavo ha un collegamento funzionante; badge VPN
  facoltativo.
- **Servizi dei tasti** (Home, Input, tasti app) rimessi a posto dopo un aggiornamento o uno stop forzato.
- **Impostazioni launcher** (tieni premuto OK su Impostazioni in alto): ogni parte qui sopra si può spegnere.

## Sulla TV

- Tieni premuto OK su una tessera per spostarla (frecce, poi OK o Indietro per rilasciarla).
- Tasto Menu: Apri, Sposta, dock, Nascondi dalla Home, Arresta, Info app, Disinstalla.
- Le app nascoste tornano dalla Libreria app (pulsante nella barra in alto).

## Installazione

```bash
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --release <owner>/<repo>   # ultima release
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --apk cleanyourtv-fire.apk # un artefatto
FIRE_HOST=192.168.1.50 launcher/fire-install.sh                           # build locale, launcher/fire-theme
FIRE_HOST=192.168.1.50 launcher/fire-install.sh --restore                  # torna alla home di Amazon
```

L'aggiornamento via adb (`adb install -r`) mantiene le impostazioni.

## Continua a guardare

Su Android TV la riga legge le voci Watch Next delle altre app e richiede `READ_TV_LISTINGS`. Il launcher la
chiede una volta; da adb:

```bash
adb shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS
```

Fire OS non ha il provider TV, quindi la riga mostra le ultime app aperte. Dettagli in
[docs/watch-next.md](docs/watch-next.md) (in inglese).

## Build

`.github/workflows/fire-apk.yml`: ogni push su `main` compila `cleanyourtv-fire.apk` e lo mette nella release
`latest` (`releases/download/latest/cleanyourtv-fire.apk`). Un tag `v*` ha una release sua. Senza segreti di
firma si usa una chiave di debug in cache; per firmare imposta `SIGNING_KEYSTORE_B64` (`base64 -w0 fire.jks`) e
`SIGNING_PASSWORD` in Settings, Secrets and variables, Actions.

## Sincronizzazione con l'originale

```bash
git fetch upstream && git rebase upstream/main && git push --force-with-lease
```

## Licenza

Come il progetto originale, vedi [LICENSE](LICENSE).
