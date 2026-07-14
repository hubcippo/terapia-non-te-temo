# Terapia non te temo 💊

App Android nativa (Kotlin + Jetpack Compose) di promemoria terapia, costruita su misura per un uso personale. KISS: 3 funzioni fatte bene.

**Foto della prescrizione → piano estratto dall'AI → conferma → allarmi con voce → segna "fatto".**

## Come funziona
1. **Foto → piano**: scatti la foto delle indicazioni del medico; un modello vision (OpenAI `gpt-5.4`) estrae il piano in JSON — anche schemi a scalare e titolazioni — e tu **confermi/correggi** prima che diventi attivo.
2. **Allarmi insistenti**: `AlarmManager.setAlarmClock` + schermata a tutto schermo + suoneria; il **TTS legge la dose ad alta voce**.
3. **Segna fatto**: tasti grandi, storico e aderenza.

## Struttura
- `android/` — progetto Android (Compose, Room, minSdk 26 / target 35)
- `prototipo-estrazione/` — schema JSON + prompt di estrazione validati su prescrizioni reali + runner di prova
- `.github/workflows/build.yml` — CI: unit test + APK debug

## Note
- Nessuna chiave API nel repo: la chiave si incolla nell'app e resta cifrata on-device (EncryptedSharedPreferences).
- Progetto personale, non è un dispositivo medico e non sostituisce il parere del medico.

🤖 Sviluppata con [Claude Code](https://claude.com/claude-code)
