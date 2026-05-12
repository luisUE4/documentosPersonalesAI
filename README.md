Here is the translation into English, preserving your formatting and emojis:
DocumentosPersonalesAI

DocumentosPersonalesAI is an advanced personal assistant application designed for the intelligent analysis and processing of documents, images, and audio, operating efficiently and prioritizing user privacy through local processing.
Key Features

    📸 Photo and PDF Analysis (AI photos): Uses artificial intelligence to analyze and extract relevant information from document photographs or PDF files quickly and easily.

    🎙️ Audio Transcription (AI audio): Transcribes audio files with high precision. It includes a preprocessing engine based on FFmpeg that automatically optimizes the audio format to maximize transcription speed and accuracy.

    📄 Text Management (AI texts): Dedicated tools for handling and analyzing the extracted information, allowing you to easily copy and manage the results.

    🧠 On-Device Artificial Intelligence: Implements local AI models using LiteRT (TensorFlow Lite) and Vosk, ensuring that sensitive information is processed securely without relying on the cloud.

    ⚡ Modern and Elegant Interface: Optimized user experience with Material Design 3, including real-time processing states and a clean, functional design.

Technologies Used

    Kotlin: Robust and modern programming language.

    MVVM Architecture: Clear separation of concerns with ViewModel and LiveData.

    Vosk Android SDK: High-performance offline speech recognition.

    FFmpegKit: Professional multimedia file processing and conversion.

    LiteRT (Google AI Edge): On-device execution of language and vision models.

    Jetpack Navigation: Fluid navigation system between sections.

Requirements and Configuration

    Android SDK: Minimum API level 34.

    Models: Requires the prior download of the Vosk model (vosk-model) and LiteRT models configured in the project for offline operation.

Transforming the management of your personal documents with the power of local Artificial Intelligence.

TODO

    dictation or transcription section and send via email actions

    what happens if the prompt is too long, does the yellow text displace the response text in the layout?

    load the model on the first prompt

    button to dictate prompt using the phone's native capabilities?
