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
