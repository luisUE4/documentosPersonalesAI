# DocumentosPersonalesAI

> *Transforming the management of your personal documents with the power of local Artificial Intelligence.*

**DocumentosPersonalesAI** is a **Android AI application assistant** designed for the intelligent analysis and processing of documents, images, and audio. Prioritizes user privacy through strict, on-device local OFFLINE processing with embedded AI model (google gemma 4).

This application can work with the mobile android device completely offline (no internet) and documents are restricted to safe storage of the device. 


---

## ✨ Key Features

* **📸 Photo and PDF Analysis (AI Vision):** Uses artificial intelligence to analyze and extract relevant information from document photographs or PDF files quickly and easily.
* **🎙️ Audio Transcription (AI Audio):** Transcribes audio files with high precision. It includes a preprocessing engine based on **FFmpeg** that automatically optimizes the audio format to maximize transcription speed and accuracy.
* **📄 Text Management (AI Text):** Dedicated tools for handling and analyzing the extracted information, allowing you to easily copy, format, and manage the results.
* **🧠 On-Device Artificial Intelligence:** Implements local AI models using **LiteRT (TensorFlow Lite)** and **Vosk**, ensuring that sensitive information is processed securely without ever leaving your device or relying on the cloud.
* **⚡ Modern and Elegant Interface:** Optimized user experience built with **Material Design 3**, featuring real-time processing states and a clean, highly functional layout.

---

## 🛠 Technologies Used

* **Kotlin:** Robust and modern programming language for Android development.
* **MVVM Architecture:** Clear separation of concerns utilizing `ViewModel` and `LiveData`.
* **Vosk Android SDK:** High-performance offline speech recognition.
* **FFmpegKit:** Professional multimedia file processing and format conversion.
* **LiteRT (Google AI Edge):** On-device execution of language and vision models.
* **Jetpack Navigation:** Fluid and predictable navigation system between app sections.

---

## ⚙️ Requirements and Setup

To build and run this project, you will need:

* **Android SDK:** Minimum API level **34**.
* **AI Models:** The app requires the prior download and configuration of the Vosk model (`vosk-model`) and the LiteRT models within the project directory for offline operation to work correctly.

---

## 🚀 Roadmap / TODOs

- [ ] Create a dedicated dictation/transcription section with "send via email" actions.
- [ ] UI Fix: Prevent long prompts (yellow text) from displacing the response text in the layout.
- [ ] Optimize performance: Load the
