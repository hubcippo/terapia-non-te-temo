# CONTRACT — impianto Android "Terapia non te temo" (Fase B)

Contratto vincolante per chi scrive il codice. **Rispettare alla lettera** package, versioni, nomi e firme: qui non si compila in locale, la compilazione è verificata in CI, quindi la coerenza è tutto.

## 0. Regole d'oro
- **Package base:** `com.carletto.terapianontetemo`
- **Lingua UI e stringhe:** italiano. Testo grande, alto contrasto.
- **Nessuna chiave API nel codice.** La chiave OpenAI si legge a runtime da EncryptedSharedPreferences (classe stub prevista, non popolata dal repo).
- **Niente allarmi, camera, full-screen, boot, storico, onboarding in Fase B** (fasi successive). Solo: skeleton + Room + domain + Home + client vision (classe, non wired) + CI.
- Stile: Kotlin idiomatico, coroutine + Flow, Compose Material3, single-Activity.
- Ogni file Kotlin inizia con `package com.carletto.terapianontetemo.<sotto>`.

## 1. Versioni (bloccate in `gradle/libs.versions.toml`)
```
agp = 8.7.3
kotlin = 2.1.0
ksp = 2.1.0-1.0.29
composeBom = 2024.12.01
room = 2.6.1
lifecycle = 2.8.7
activityCompose = 1.9.3
navigationCompose = 2.8.5
coroutines = 1.9.0
serialization = 1.7.3
securityCrypto = 1.1.0-alpha06
coreKtx = 1.15.0
```
Plugin Compose: `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.x). Room via KSP.
SDK: `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`. `namespace = "com.carletto.terapianontetemo"`. `applicationId` uguale. `versionCode 1`, `versionName "0.1.0"`. Java/JVM target 17.

## 2. Layout file (owner = agente responsabile)
```
android/
  settings.gradle.kts                         [FOUNDATION]
  build.gradle.kts                            [FOUNDATION]
  gradle.properties                           [FOUNDATION]
  gradle/libs.versions.toml                   [FOUNDATION]
  gradle/wrapper/gradle-wrapper.properties    [FOUNDATION]  (distributionUrl gradle-8.11.1-bin.zip)
  gradlew                                     [FOUNDATION]  (script standard)
  .gitignore                                  [FOUNDATION]
  app/build.gradle.kts                        [FOUNDATION]
  app/proguard-rules.pro                      [FOUNDATION]
  app/src/main/AndroidManifest.xml            [FOUNDATION]
  app/src/main/res/values/strings.xml         [FOUNDATION]  (app_name = "Terapia non te temo")
  app/src/main/res/values/themes.xml          [FOUNDATION]  (Theme.Material3 base, no actionbar)
  app/src/main/java/com/carletto/terapianontetemo/
    TerapiaApp.kt                             [FOUNDATION]  Application
    MainActivity.kt                           [FOUNDATION]  ComponentActivity, setContent { TerapiaTheme { AppRoot() } }
    ui/AppRoot.kt                             [UI]          NavHost minimale, start = "home"
    ui/theme/Color.kt                         [FOUNDATION]
    ui/theme/Theme.kt                         [FOUNDATION]  TerapiaTheme(dynamic auto, chiaro/scuro)
    ui/theme/Type.kt                          [FOUNDATION]  Typography con size maggiorati
    ui/home/HomeScreen.kt                     [UI]
    ui/home/HomeViewModel.kt                  [UI]
    data/entity/Farmaco.kt                    [DATA]
    data/entity/Fase.kt                       [DATA]
    data/entity/DoseEvent.kt                  [DATA]
    data/Converters.kt                        [DATA]
    data/dao/FarmacoDao.kt                    [DATA]
    data/dao/FaseDao.kt                       [DATA]
    data/dao/DoseEventDao.kt                  [DATA]
    data/AppDatabase.kt                       [DATA]
    data/TerapiaRepository.kt                 [DATA]
    domain/model/PianoEstratto.kt             [DOMAIN]  mirror di schema.json (@Serializable)
    domain/EstrazioneParser.kt                [DOMAIN]  JSON string -> PianoEstratto
    domain/Srotolatore.kt                     [DOMAIN]  PianoEstratto -> inserimenti Room + DoseEvent
    domain/OrariFissi.kt                      [DOMAIN]  normalizzazione fasce 07/15/19/23
    ai/OpenAiVisionClient.kt                  [DOMAIN]  classe client (non wired in Home); model "gpt-5.4"
    ai/KeyStore.kt                            [DOMAIN]  EncryptedSharedPreferences (get/set apiKey)
  .github/workflows/build.yml                 [CI]
```

## 3. Tipi ed enum condivisi (definizione UNICA — non ridefinire)
In `data/entity/`:
```kotlin
enum class Forma { COMPRESSA, INIEZIONE, ALTRO }
enum class StatoDose { ATTESA, PRESO, SALTATO, RIMANDATO }
enum class SchemaDose { INTERVALLO, ORARI }
enum class Fascia { MATTINA, PRANZO, POMERIGGIO, SERA, NOTTE }
```
Le date/ora in Room come `Long` (epoch millis, UTC) + campo separato quando serve; in domain usare `java.time.LocalDateTime`/`LocalTime`. Converters gestiscono `List<Fascia>`, `List<LocalTime>`, enum.

## 4. Firme di interfaccia vincolanti (i confini tra agenti)
```kotlin
// data/entity/Farmaco.kt
@Entity data class Farmaco(@PrimaryKey(autoGenerate=true) val id: Long=0, val nome: String, val forma: Forma, val note: String?=null)

// data/entity/Fase.kt
@Entity data class Fase(
  @PrimaryKey(autoGenerate=true) val id: Long=0, val farmacoId: Long, val ordine: Int,
  val dose: String?, val doseMattina: String?, val doseSera: String?,
  val schema: SchemaDose, val intervalloOre: Int?, val quando: List<Fascia>,
  val durataGiorni: Int?, val mantenimento: Boolean, val dopoFasePrecedente: Boolean)

// data/entity/DoseEvent.kt
@Entity data class DoseEvent(
  @PrimaryKey(autoGenerate=true) val id: Long=0, val farmacoId: Long,
  val dataOraMillis: Long, val dose: String, val forma: Forma,
  val stato: StatoDose = StatoDose.ATTESA, val tsAzioneMillis: Long? = null)

// data/TerapiaRepository.kt (contratto usato dalla Home)
class TerapiaRepository(db: AppDatabase) {
  fun doseDiOggi(): Flow<List<DoseEvent>>
  fun prossimaDose(oraMillis: Long): Flow<DoseEvent?>
  suspend fun segnaFatto(doseId: Long, tsMillis: Long)
  suspend fun rimanda(doseId: Long, nuovoOraMillis: Long)
  fun farmaco(id: Long): Flow<Farmaco?>
}

// domain/Srotolatore.kt
object Srotolatore {
  /** Inserisce farmaco+fasi e genera i DoseEvent concreti a partire da 'inizio'. */
  suspend fun applica(piano: PianoEstratto, inizio: LocalDate, repo: TerapiaRepository, db: AppDatabase)
}

// ai/OpenAiVisionClient.kt
class OpenAiVisionClient(private val apiKey: String) {
  /** Ritorna il JSON grezzo conforme a schema.json. model = "gpt-5.4". */
  suspend fun estraiPiano(imageBytes: ByteArray): String
}
```

## 5. Modello estrazione (deve rispecchiare `../prototipo-estrazione/schema.json`)
`domain/model/PianoEstratto.kt` con @Serializable: `PianoEstratto(eRicetta: Boolean, avvisi: List<String>, terapie: List<TerapiaEstratta>)`, `TerapiaEstratta(farmaco, forma, note, confidenza, illeggibile, fasi)`, `FaseEstratta(ordine, dose, doseMattina, doseSera, schema, intervalloOre, quando, durataGiorni, mantenimento, dopoFasePrecedente)`. Nomi JSON come nello schema (`e_ricetta`, `intervalloOre`, ecc.) via @SerialName dove differiscono.

## 6. Srotolamento — regole
- `schema=INTERVALLO` con `intervalloOre` → mappa alle fasce fisse: 24h→[07:00]; 12h→[07:00,19:00]; 8h→[07:00,15:00,23:00]; 6h→[07,13,19,23]. Altrimenti distribuisci sulle 4 fasce default 07/15/19/23.
- `schema=ORARI` con `quando` → LocalTime: MATTINA 07:00, PRANZO 13:00, POMERIGGIO 15:00, SERA 19:00, NOTTE 23:00.
- Titolazione: se `doseMattina/doseSera` valorizzati, la dose del DoseEvent dipende dalla fascia (mattina→doseMattina, sera→doseSera).
- Fasi sequenziali: la fase con `dopoFasePrecedente=true` inizia il giorno dopo la fine della precedente. `durataGiorni=null`+`mantenimento=true` → genera N giorni di orizzonte (usa 30 giorni come orizzonte di default, TODO reschedule).
- Genera un `DoseEvent` per ogni (giorno × orario × dose) nell'orizzonte della fase.

## 7. Home
- "Prossima dose": card grande con farmaco + dose + ora + forma (💉 se INIEZIONE), bottone **Fatto**.
- Sotto: lista dosi di oggi con stato (attesa/preso/saltato) e spunta Fatto per riga.
- ViewModel: `androidx.lifecycle.ViewModel`, espone `StateFlow<HomeUiState>`; usa il Repository. Factory semplice o istanziazione via `viewModel { }` con AppDatabase da `TerapiaApp`.

## 8. CI (`.github/workflows/build.yml`)
- Trigger: push su main + workflow_dispatch. `defaults.run.working-directory: android`.
- Step: checkout, set-up JDK 17 (temurin), setup Android SDK, `./gradlew :app:assembleDebug`, upload artifact `app-debug.apk`.
- (Firma release + Obtainium → Fase D, non ora.)

---

# CONTRACT — Fase C: foto → estrazione → Conferma (aggiunta 2026-07-14)

Regole d'oro invariate (sez.0). Nuovi file, un solo autore.

## 9. File Fase C
```
app/src/main/res/xml/file_paths.xml                 FileProvider paths (cache-path per foto camera)
app/src/main/AndroidManifest.xml                    MODIFICA: aggiungi <provider> FileProvider (authority "${applicationId}.fileprovider")
app/src/main/java/com/carletto/terapianontetemo/
  util/ImmaginePerVision.kt        object: fun prepara(context, uri): ByteArray — decodifica Uri, ruota per EXIF se serve,
                                   downscale lato lungo max 2048px, comprime JPEG q85. Lancia IOException con msg italiano se illeggibile.
  ui/aggiungi/PianoEditabile.kt    Modello UI mutabile-friendly del piano: data class con List di TerapiaEditabile
                                   (farmaco:String, forma:FormaEstratta, note:String?, confidenza, illeggibile, inclusa:Boolean=true,
                                    fasi: List<FaseEditabile> con campi String editabili per dose/doseMattina/doseSera/durataGiorni e List<FasciaEstratta> quando).
                                   Funzioni: fun daEstratto(p: PianoEstratto): PianoEditabile; fun versoEstratto(): PianoEstratto
                                   (solo terapie inclusa=true; durataGiorni parse Int?; stringhe vuote -> null). PURE, testabili.
  ui/aggiungi/AggiungiViewModel.kt sealed interface AggiungiStato { Idle; ServeChiave; InCorso; Errore(val messaggio:String);
                                   Conferma(val piano: PianoEditabile, val fotoUri: Uri?) }
                                   class AggiungiViewModel(app: TerapiaApp) : ViewModel — espone StateFlow<AggiungiStato>;
                                   fun avvia(context, uri) [controlla KeyStore.getApiKey -> ServeChiave se null; altrimenti IO: ImmaginePerVision.prepara
                                   -> OpenAiVisionClient(chiave).estraiPiano -> EstrazioneParser.parse; e_ricetta=false -> Errore("...non sembra una prescrizione...");
                                   catch IOException/SerializationException -> Errore msg italiano; 401 nel messaggio -> "Chiave non valida"];
                                   fun salvaChiave(context, chiave) -> KeyStore.setApiKey e ritenta; fun aggiorna(piano) [update stato Conferma];
                                   suspend fun conferma() -> Srotolatore.applica(piano.versoEstratto(), LocalDate.now(), app.repository, app.database); fun reset().
                                   Factory(app: TerapiaApp) come HomeViewModel.
  ui/aggiungi/AggiungiScreen.kt    UI stato-driven: Idle -> 2 bottoni GRANDI "📷 Scatta foto" e "🖼 Scegli dalla galleria"
                                   (rememberLauncherForActivityResult: TakePicture con Uri da FileProvider su cacheDir; PickVisualMedia ImageOnly);
                                   ServeChiave -> ChiaveApiCard inline (campo OutlinedTextField password + bottone Salva, testo guida breve);
                                   InCorso -> spinner + "Sto leggendo la ricetta…"; Errore -> messaggio + bottone "Riprova";
                                   Conferma -> naviga/mostra ConfermaContent.
  ui/aggiungi/ConfermaScreen.kt    ConfermaContent(piano, fotoUri, onModifica, onRimuoviTerapia, onConferma):
                                   avvisi in Card warning in cima; per terapia: Card (bordo/label GIALLO se confidenza!=ALTA o illeggibile)
                                   con nome editabile, per fase: dose (o doseMattina+doseSera) editabili, durata giorni editabile,
                                   FilterChip per fasce quando (MATTINA/PRANZO/POMERIGGIO/SERA/NOTTE) se schema=ORARI,
                                   switch/icona elimina per escludere la terapia; miniatura foto (AsyncImage no — usa Image con
                                   rememberAsyncImagePainter NO, niente Coil: usa BitmapFactory via remember su fotoUri, piccola);
                                   bottone grande "✅ Conferma piano" -> onConferma -> nav Home (popUpTo home inclusive=false, launchSingleTop).
  ui/AppRoot.kt                    MODIFICA: route "aggiungi" (AggiungiScreen); la Conferma è uno stato interno di AggiungiScreen (niente route separata: il piano non è serializzabile in nav args).
  ui/home/HomeScreen.kt            MODIFICA: aggiungi FAB esteso "➕ Aggiungi da foto" (Scaffold floatingActionButton) -> onAggiungi: () -> Unit; HomeScreen prende anche onAggiungi.
app/src/test/java/com/carletto/terapianontetemo/ui/aggiungi/PianoEditabileTest.kt
                                   round-trip daEstratto/versoEstratto sul caso2_titolazione.json (risorse test esistenti),
                                   esclusione terapia (inclusa=false), durata vuota -> null, dose vuota -> null.
```

## 10. Regole Fase C
- Niente Coil/librerie nuove: solo AndroidX già nel catalog + activity-compose (rememberLauncherForActivityResult è in activity-compose, già presente).
- Foto camera: file in context.cacheDir/"foto_ricetta.jpg" esposto via FileProvider (authority "${applicationId}.fileprovider").
- NIENTE permesso CAMERA nel manifest (TakePicture usa l'app fotocamera di sistema).
- Tutte le stringhe in italiano, testo grande come in Home.
- Nessuna auto-attivazione: il piano entra in Room SOLO dal bottone Conferma.

---

# CONTRACT — Fase D: allarme insistente + voce (aggiunta 2026-07-14)

Regole d'oro invariate. Un solo autore. KISS: AlarmManager + receiver + activity, NIENTE WorkManager/foreground service persistenti.

## 11. File Fase D
```
app/src/main/AndroidManifest.xml   MODIFICA: permessi USE_EXACT_ALARM, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED,
                                   USE_FULL_SCREEN_INTENT, VIBRATE; receiver AlarmReceiver (exported=false) e BootReceiver
                                   (exported=false, intent-filter BOOT_COMPLETED); activity AlarmActivity
                                   (exported=false, showWhenLocked=true, turnScreenOn=true, launchMode=singleInstance,
                                   excludeFromRecents=true, theme dell'app).
app/src/main/java/com/carletto/terapianontetemo/
  allarme/ProssimoAllarme.kt       LOGICA PURA (testabile JVM, zero Android):
                                   object ProssimoAllarme {
                                     const val SCADENZA_MILLIS = 2*60*60*1000L  // 2h -> SALTATO
                                     /** id delle dosi ATTESA con dataOra <= adesso-2h (da marcare SALTATO). */
                                     fun scadute(dosi: List<DoseEvent>, adessoMillis: Long): List<Long>
                                     /** millis della prossima fascia: la MIN dataOraMillis tra le ATTESA con dataOra > adesso-2h... 
                                         semplificazione: MIN dataOraMillis tra le ATTESA non scadute (può essere nel passato recente <2h: l'allarme scatta subito, ok). null se nessuna. */
                                     fun prossimaFascia(dosi: List<DoseEvent>, adessoMillis: Long): Long?
                                     /** tutte le dosi ATTESA con dataOraMillis == fasciaMillis (raggruppamento). */
                                     fun dosiDellaFascia(dosi: List<DoseEvent>, fasciaMillis: Long): List<DoseEvent>
                                   }
  allarme/AlarmScheduler.kt        object AlarmScheduler {
                                     const val EXTRA_FASCIA = "fasciaMillis"; const val EXTRA_PROVA = "prova"
                                     /** Marca SALTATO le scadute, poi setAlarmClock sulla prossima fascia (o cancella se nulla).
                                         Un solo PendingIntent (requestCode fisso 1001, FLAG_IMMUTABLE|FLAG_UPDATE_CURRENT) verso AlarmReceiver. */
                                     suspend fun ripianifica(context: Context)
                                     /** Allarme di PROVA tra delaySecondi (default 60): PendingIntent requestCode 1002 con EXTRA_PROVA=true.
                                         NON tocca il DB. */
                                     fun programmaProva(context: Context, delaySecondi: Int = 60)
                                   }
                                   Usa context.getSystemService(AlarmManager::class.java); se (SDK>=31 && !canScheduleExactAlarms()) fallback setExactAndAllowWhileIdle? NO: con USE_EXACT_ALARM canScheduleExactAlarms è true; metti comunque il check difensivo con fallback a setAlarmClock comunque tentato in try/catch SecurityException -> setWindow.
                                   Serve leggere le dosi: DoseEventDao nuovo metodo suspend `tutteAttesa(): List<DoseEvent>` (query stato=ATTESA) e `marcaSaltate(ids: List<Long>, ts: Long)` (UPDATE ... WHERE id IN (:ids)).
  allarme/AlarmReceiver.kt         BroadcastReceiver. onReceive: goAsync() + CoroutineScope(Dispatchers.IO):
                                   se EXTRA_PROVA -> mostra SOLO la notifica full-screen con fascia=-1 (nessun DB);
                                   altrimenti: marca scadute, legge dosi della fascia (EXTRA_FASCIA); se vuota -> ripianifica e stop;
                                   mostra notifica full-screen; poi ripianifica (per la fascia successiva).
                                   MAI startActivity diretto. Notifica: canale "allarme_terapia" IMPORTANCE_HIGH (creato in TerapiaApp.onCreate),
                                   setCategory(CATEGORY_ALARM), setFullScreenIntent(PendingIntent verso AlarmActivity con EXTRA_FASCIA/EXTRA_PROVA, IMMUTABLE),
                                   ongoing, azioni "Fatto" e "Rimanda 10 min" (PendingIntent verso AzioneReceiver), setSound(null) (il suono lo fa l'activity; ma
                                   la notifica resta se il full-screen è soppresso -> setSound su canale: usa suono default allarme sul CANALE con AudioAttributes USAGE_ALARM così suona anche senza full-screen).
                                   NotificationId fisso 2001.
  allarme/AzioneReceiver.kt        BroadcastReceiver per le azioni della notifica: ACTION_FATTO -> marca PRESO tutte le dosi della fascia
                                   (o niente se prova); ACTION_RIMANDA -> dataOraMillis += 10min per le dosi della fascia; entrambe:
                                   cancella notifica 2001, AlarmScheduler.ripianifica. goAsync+IO.
  allarme/AlarmActivity.kt         ComponentActivity Compose. onCreate: setShowWhenLocked(true), setTurnScreenOn(true),
                                   (SDK>=27 API dedicate; il manifest ha già i flag), keyguardManager.requestDismissKeyguard.
                                   Legge EXTRA_FASCIA/EXTRA_PROVA; se prova -> dose fittizia ("Farmaco di prova", "1 compressa").
                                   UI: sfondo primario pieno, ora gigante, lista farmaci+dosi (testo displayMedium),
                                   💉 se iniezione, bottoni giganti (min 88dp) "✅ Fatto" e "⏰ Rimanda 10 minuti".
                                   SUONO: Ringtone di RingtoneManager.getDefaultUri(TYPE_ALARM) con AudioAttributes USAGE_ALARM, loop
                                   (SDK>=28 ringtone.isLooping=true), start in onStart, stop in onStop/onDestroy e su Fatto/Rimanda.
                                   VIBRAZIONE: VibratorManager/Vibrator waveform ripetuto, cancel su stop.
                                   TTS: TextToSpeech(context, listener); in onInit SUCCESS -> setLanguage(Locale.ITALIAN); se
                                   LANG_MISSING/NOT_SUPPORTED -> non parlare (nessun crash). Parla dopo 1s di suoneria (handler):
                                   "È ora delle medicine. <per ogni dose: NomeFarmaco, dose X>. [se cambioDose: 'Attenzione: da oggi la dose di <nome> cambia.']"
                                   con QUEUE_FLUSH + pitch/rate normali. shutdown() in onDestroy.
                                   CAMBIO DOSE: query: per ogni farmacoId della fascia, esiste DoseEvent ieri (stesso farmaco, dataOra in [ieri00, oggi00))
                                   con dose diversa dalla dose di oggi? -> annuncio. DoseEventDao nuovo metodo `doseDiFarmacoInRange(farmacoId, da, a): List<DoseEvent>`.
                                   Bottoni: Fatto -> marca PRESO tutte (ts adesso), Rimanda -> +10min; entrambe: stop suono, cancella notifica, ripianifica, finish().
                                   Se prova: i bottoni chiudono soltanto (niente DB).
  allarme/BootReceiver.kt          BroadcastReceiver BOOT_COMPLETED -> goAsync+IO -> AlarmScheduler.ripianifica.
  TerapiaApp.kt                    MODIFICA: onCreate crea il NotificationChannel "allarme_terapia" (IMPORTANCE_HIGH,
                                   suono allarme default con USAGE_ALARM, vibrazione on).
  ui/home/HomeScreen.kt            MODIFICA: TopAppBar semplice con titolo "Terapia non te temo" e icona campanella 🔔
                                   (IconButton) -> onProvaAllarme; snackbar "Allarme di prova tra 1 minuto: blocca lo schermo".
                                   HomeScreen(viewModel, onAggiungi, onProvaAllarme).
                                   PERMESSO: all'avvio della Home, se SDK>=33 e POST_NOTIFICATIONS non concesso ->
                                   rememberLauncherForActivityResult(RequestPermission) lanciato da LaunchedEffect una volta.
  ui/AppRoot.kt                    MODIFICA: passa onProvaAllarme = { AlarmScheduler.programmaProva(context) } alla Home.
  ui/home/HomeViewModel.kt         MODIFICA: segnaFatto -> dopo repository.segnaFatto chiama AlarmScheduler.ripianifica(context di app).
                                   (Il VM ha TerapiaApp? No: HomeViewModel riceve repository. SOLUZIONE: HomeViewModel.Factory già riceve
                                   TerapiaApp -> passa anche l'app al VM (application context) SENZA cambiare la firma pubblica del costruttore?
                                   Cambiala pure: HomeViewModel(app: TerapiaApp) con repository = app.repository — aggiorna Factory. È interno a ui/home.)
  ui/aggiungi/AggiungiViewModel.kt MODIFICA: conferma() -> dopo Srotolatore.applica chiama AlarmScheduler.ripianifica(app).
app/src/test/java/com/carletto/terapianontetemo/allarme/ProssimoAllarmeTest.kt
                                   Test JVM: scadute (limite esatto 2h, dosi PRESO ignorate), prossimaFascia (min futura, ignora scadute,
                                   null se vuoto), dosiDellaFascia (raggruppa solo stessa fascia e stato ATTESA).
```

## 12. Regole Fase D
- PendingIntent SEMPRE FLAG_IMMUTABLE. requestCode: 1001 allarme reale, 1002 prova, 2001 notifica, 3001/3002 azioni.
- Il DB si tocca SOLO via DAO/Repository esistenti + i 3 metodi DAO nuovi (tutteAttesa, marcaSaltate, doseDiFarmacoInRange).
- Nessuna nuova dipendenza Gradle. Icona notifica: android.R.drawable.ic_lock_idle_alarm (KISS, niente asset nuovi).
- versionCode 3, versionName "0.3.0" in app/build.gradle.kts.
- Stringhe italiane; l'allarme deve essere leggibile A DISTANZA (display sizes).

---

# CONTRACT — Fase D.1: suono robusto via foreground service (aggiunta 2026-07-14, da test reale su HyperOS)

PROBLEMA EMERSO SUL DEVICE: in silenzioso la notifica non suona (HyperOS muta i suoni di canale) e il full-screen può essere soppresso → l'allarme resta muto finché non tocchi la notifica. FIX: il suono NON dipende più da notifica/full-screen.

## 13. Modifiche Fase D.1
```
allarme/SuonoAllarmeService.kt   NUOVO. Foreground service che POSSIEDE suoneria+vibrazione+TTS dell'allarme.
                                 - onStartCommand: legge EXTRA_FASCIA/EXTRA_PROVA; startForeground(AlarmReceiver.NOTIFICA_ID, <la stessa notifica full-screen costruita da AlarmReceiver.mostraNotifica -> estrai la costruzione in una funzione condivisa che RITORNA la Notification senza pubblicarla>). foregroundServiceType mediaPlayback.
                                 - Suona Ringtone TYPE_ALARM con USAGE_ALARM in loop (28+) + vibrazione waveform ripetuta (stesso codice oggi in AlarmActivity: SPOSTALO qui).
                                 - TTS: init sicura, italiano; dopo ~1.5s pronuncia fraseTts(voci) (voci lette via repository come fa il receiver; per prova usa VOCE_DI_PROVA); pausa suoneria durante il parlato e riprendi (sposta la logica dall'Activity).
                                 - ACTION_STOP: interrompe suono/vibrazione/TTS, stopForeground(REMOVE la notifica? NO: stopForeground(STOP_FOREGROUND_DETACH) così la notifica resta se non gestita) e stopSelf. Decisione: su Fatto/Rimanda la notifica va comunque cancellata dai chiamanti già esistenti.
                                 - Timeout di sicurezza: dopo 10 minuti di suono continuo, ferma suono/vibrazione (la notifica resta; la dose diventerà SALTATA a 2h dalla fascia). Handler nel service.
                                 - onDestroy: rilascia tutto (ringtone stop, vibrator cancel, tts shutdown, handler callbacks).
AlarmReceiver.kt                 MODIFICA: costruisci la notifica con la funzione condivisa; NON pubblicarla via NotificationManagerCompat quando avvii il service (la pubblica startForeground). Avvia SEMPRE context.startForegroundService(Intent(SuonoAllarmeService)) con gli stessi extra. Mantieni il fallback: se startForegroundService lancia (ForegroundServiceStartNotAllowedException o altro) -> pubblica la notifica come oggi (il suono almeno da canale).
AlarmActivity.kt                 MODIFICA: RIMUOVI Ringtone/Vibrator/TTS (ora nel service). L'activity è SOLO UI: mostra le voci, e su Fatto/Rimanda/chiusura manda ACTION_STOP al service (context.startService con action) oltre a fare ciò che già fa (repository + cancel notifica + ripianifica + finish). All'apertura NON avvia suoni.
AzioneReceiver.kt                MODIFICA: oltre a cancellare la notifica, manda ACTION_STOP al service.
AndroidManifest.xml              MODIFICA: permessi FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK; <service android:name=".allarme.SuonoAllarmeService" android:exported="false" android:foregroundServiceType="mediaPlayback"/>.
app/build.gradle.kts             versionCode 4, versionName "0.3.1".
```
Regole: startForegroundService da un allarme setAlarmClock è ESENTE dalle restrizioni background (l'app è temporaneamente whitelisted dal fire dell'allarme). Il service chiama startForeground ENTRO onStartCommand immediatamente. Nessuna nuova dipendenza.
