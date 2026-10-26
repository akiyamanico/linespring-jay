

**LineSpring** is a high-performance LINE client bot built on **Java 21** and **Spring Boot 3.2.3**, migrated and re-engineered from the Python linepy project. It features native Apache Thrift wire protocols (Compact & Binary), full End-to-End Encryption (E2EE / Letter Sealing), Terminal QR Code login, Google Gemini AI image generation, and multi-platform automation (WhatsApp notifications and media downloading).


---

## Features

- **Flexible Authentication**:
  - **Email & Password Login**: RSA session key encryption with secondary PIN verification via the `/Q` endpoint.
  - **Session Persistence**: Caches authentication tokens (`token.txt`) and device certificates (`certificate.txt`) to skip future PIN prompts.
- **Robust Event Polling Engine**: Dedicated background daemon thread polling LINE sync operations (`SyncRequest`), maintaining revision counters (`lastRevision`, `lastGlobalRevision`, `lastIndividualRevision`).
- **Google Gemini AI Integration**: Generates images on-demand using Google's Gemini multimodal models (`gemini-2.0-flash-exp-image-generation`).
- **WhatsApp Mention Notifications**: Sends instant alerts to your WhatsApp phone number via the TextMeBot API whenever the bot is mentioned in a group, complete with 4-digit code verification.
- **Media Link Downloader**: Detects links from YouTube and Instagram in group chats and downloads them automatically via `yt-dlp`.
- **Interactive Trivia Quiz**: Built-in quiz module with automated 180-second timeout schedulers and answer validation.
- **Group Moderation**: Fast group member management (`kick`, `kickall`, and toggling join-by-ticket/invite links).

---

## Architecture Overview

```
                      ┌──────────────────────────────────────┐
                      │        LinepyJayApplication          │
                      └──────────────────┬───────────────────┘
                                         │ (Bootstrap)
                      ┌──────────────────▼───────────────────┐
                      │          LineClientService           │
                      │  -       Password Auth Flow          │
                      │  - Session Management (token/cert)   │
                      └─────────┬──────────────────┬─────────┘
                                │                  │
                 ┌──────────────▼──────┐    ┌──────▼──────────────┐
                 │     PollService     │    │   LineE2EEService   │
                 │  - fetchOperations  │    │  - X25519 Key Exch. │
                 │  - Revision Sync    │    │  - AES-GCM / AAD    │
                 └──────────────┬──────┘    │  - AES-CBC / HKDF   │
                                │           └─────────────────────┘
                 ┌──────────────▼───────────┐
                 │  MessageHandlerService   │
                 └──────────────┬───────────┘
                                │ (Routes Incoming Ops)
      ┌────────────────┬────────┼────────────────┬────────────────┐
      ▼                ▼        ▼                ▼                ▼
[Group & Member]    [Quiz]  [Media]          [Gemini AI]     [WhatsApp]
- Kick / KickAll   QuizSvc  MediaSvc         AiImageService  WhatsAppService
- GroupLink On/Off          (yt-dlp)         (REST API)      (TextMeBot)

```

This project is distributed for educational and development purposes. Refer to the repository license for additional terms.
