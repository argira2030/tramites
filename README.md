# Trámites (Android) · v0.1

Versión básica en Kotlin: un `WebView` que aloja la web **Trámites · webs oficiales** (`app/src/main/assets/www/index.html`) y funciona sin conexión.

**No es una app oficial** de ninguna administración. Solo enlaza a webs oficiales.

- Identificador: `eus.argira.tramites` (no se puede cambiar una vez publicado en Play).
- Sin permisos: sin INTERNET, cámara ni llamadas.

## Qué se ha adaptado respecto a la web
| Cambio | Cómo |
|---|---|
| **Llamar** | `tel:` abre el marcador con el número escrito (`ACTION_DIAL`); no llama solo ni pide permiso. |
| **Mapa** | El enlace «Ver ubicación» (OpenStreetMap) se abre en la app de mapas del móvil con `geo:`; si no hay, en el navegador. |
| **Voz** | La web usa `speechSynthesis`; dentro de la app un pequeño script (al principio de `index.html`) lo sustituye por el TextToSpeech nativo (`TtsBridge.kt`). En el navegador no hace nada. |
| **Quitado** | Enlace a la extensión de Chrome, enlace a la web de Trámites en «Apps» y «Volver a IDEAKEY». |
| **Añadido** | Aviso «Trámites no es una app oficial» en la ayuda y al final de la página. |
| **Modo oscuro** | Sigue el del móvil (Android 10+). |

Los enlaces a webs oficiales, correo y las apps de la familia Argira (IDEAKEY, IMAGINE) se abren fuera de la app.

## Compilar
Sube el proyecto a GitHub: el workflow `.github/workflows/android.yml` genera el APK de pruebas.
Para el AAB firmado añade los secretos `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.
En local: `gradle :app:assembleDebug` (Gradle 8.11.1, JDK 17).

## Pendiente (no hecho en v0.1)
- **No se ha compilado** en este entorno (sin Android SDK): hay que lanzar el workflow y probar el APK en el móvil.
- Icono definitivo y capturas para Play.
- Política de privacidad y ficha de Play (declarar «sin recogida de datos»).
- **Actualizar los datos:** el catálogo va dentro de la app, así que cada corrección exige una versión nueva. Mostrar siempre la fecha de revisión y nombrar a una persona responsable.
- Revisar los datos de las fichas (teléfonos, requisitos, enlaces): vienen de la web, no se han contrastado aquí.
