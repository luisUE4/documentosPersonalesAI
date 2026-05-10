# DocumentosPersonalesAI

**DocumentosPersonalesAI** es una aplicación avanzada de asistencia personal diseñada para el análisis inteligente y procesamiento de documentos, imágenes y audio, funcionando de manera eficiente y priorizando la privacidad del usuario mediante el procesamiento local.

## Características Principales

*   **📸 Análisis de Fotos y PDFs (fotos IA):** Utiliza inteligencia artificial para analizar y extraer información relevante de fotografías de documentos o archivos PDF de manera rápida y sencilla.
*   **🎙️ Transcripción de Audio (audio IA):** Transcribe archivos de audio con alta precisión. Incluye un motor de preprocesamiento basado en **FFmpeg** que optimiza automáticamente el formato del audio para maximizar la velocidad y exactitud de la transcripción.
*   **📄 Gestión de Textos (textos IA):** Herramientas dedicadas para el manejo y análisis de la información extraída, permitiendo copiar y gestionar los resultados fácilmente.
*   **🧠 Inteligencia Artificial On-Device:** Implementa modelos de IA locales utilizando **LiteRT (TensorFlow Lite)** y **Vosk**, garantizando que la información sensible se procese de forma segura sin depender de la nube.
*   **⚡ Interfaz Moderna y Elegante:** Experiencia de usuario optimizada con **Material Design 3**, que incluye estados de procesamiento en tiempo real y un diseño limpio y funcional.

## Tecnologías Utilizadas

*   **Kotlin**: Lenguaje de programación robusto y moderno.
*   **Arquitectura MVVM**: Separación clara de responsabilidades con ViewModel y LiveData.
*   **Vosk Android SDK**: Reconocimiento de voz offline de alto rendimiento.
*   **FFmpegKit**: Procesamiento y conversión profesional de archivos multimedia.
*   **LiteRT (Google AI Edge)**: Ejecución de modelos de lenguaje y visión en el dispositivo.
*   **Jetpack Navigation**: Sistema de navegación fluido entre secciones.

## Requisitos y Configuración

*   **Android SDK**: Nivel de API mínimo 34.
*   **Modelos**: Requiere la descarga previa del modelo Vosk (`vosk-model`) y modelos LiteRT configurados en el proyecto para su funcionamiento offline.

---
*Transformando la gestión de tus documentos personales con el poder de la Inteligencia Artificial local.*


TODO
*seccion dictado o transcripcion  y acciones de enviar usando email
*que pasa si el prompt es muy largo, el texto amarillo desplaza el texto de respuesta en el layout?
*que el modelo se cargue en el primer prompt
*boton para dictar prompt usando las capacidades nativas del telefono ?

