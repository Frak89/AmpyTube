# AmpyTube 0.1.1

Progetto Android Studio per Pixel 7 Pro e altri dispositivi Android (minSdk 26).

**Funzione principale**: riproduzione audio/video YouTube con audio in background e display spento, grazie a Media3 e a un MediaSessionService. Il player è indipendente dalla schermata dell'app. Controlli dalla notifica, schermata di blocco, cuffie/Bluetooth (quando Android li espone).

## Interfaccia

Genie mesh, menu Reside con prospettiva, rotazione 3D, Flip, Liquid, Elastic, morphing organico; intensità animazioni regolabile. **Nessun effetto glitch.**

## Come ottenere l'APK

Apri **Actions > Android APK** e scegli l'esecuzione riuscita più recente. Scarica l'artifact `AmpyTube-0.1.1-debug-apk` e installa l'APK debug sul telefono. Puoi anche usare Android Studio con AGP 8.11.1, Gradle 8.13, JDK 17, Android SDK 36. Il workflow utilizza `gradle` direttamente; non serve il wrapper JAR.

## Limiti importanti

La risoluzione YouTube usa **NewPipeExtractor v0.26.5** (GPLv3) e potrebbe interrompersi se YouTube modifica i propri sistemi; video privati e protetti possono non funzionare. Questa modalità potrebbe essere incompatibile con i termini di YouTube. Rispetta le licenze del software e le condizioni applicabili. Il video è visibile soltanto a schermo acceso; a schermo spento continua il suono.

**Stato**: revisione sperimentale. La presenza del workflow non garantisce una build riuscita: verificare i log di GitHub Actions e poi provare su dispositivo reale. Nessuna repository esterna coinvolta.

## Il mio YouTube (0.1.1)

Il pulsante **IL MIO YOUTUBE** apre la scheda *Tu* di YouTube in una Custom Tab del browser predefinito, che riutilizza la sessione Google del browser. Sono disponibili collegamenti a **Iscrizioni**, **Cronologia** e **Playlist**. Se non hai gia effettuato l'accesso nel browser, YouTube potrebbe chiederlo la prima volta; AmpyTube non vede e non salva la password, i cookie o i token Google.

Per riprodurre un video nel player AmpyTube: copia il link del video dalla pagina YouTube, torna all'app e premi **RIPRODUCI LINK COPIATO**. Il servizio Media3 continua a gestire la riproduzione con schermo spento; la Custom Tab, invece, e gestita dal browser e non comunica direttamente con il lettore nativo. La sincronizzazione nativa di profilo, playlist e iscrizioni richiederebbe OAuth 2.0 e YouTube Data API, non inclusi in questa revisione.
