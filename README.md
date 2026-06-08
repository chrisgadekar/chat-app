# 💬 Real-Time Chat

A real-time, multi-channel chat application built with **Spring Boot** and **WebSocket (STOMP over SockJS)**. Messages broadcast instantly to everyone in a channel — with reactions, mentions, typing indicators, presence, and a polished, theme-aware UI.

<!-- Add a screenshot here once you have one:
![Chat screenshot](docs/screenshot.png)
-->

---

## ✨ Features

**Messaging**
- ⚡ Real-time messaging over WebSocket (STOMP + SockJS)
- 📺 Multiple channels — `#general`, `#random`, `#tech-talk`, `#games`
- 🕓 Per-channel message history for late joiners
- 👍 Emoji reactions on any message
- 💬 Typing indicators ("X is typing…")
- @️ `@mention` highlighting with unread tab-title badge

**Presence**
- 🟢 Live online-users list per channel
- 🚪 Join / leave notifications
- 🔒 Unique usernames (no two people share a name)

**UI / UX**
- 🎨 App-shell layout with avatars (colored initials) and a gradient header
- 🌙 Light / dark mode toggle (remembered across sessions)
- 🧵 Discord-style message grouping + Today/Yesterday date separators
- ⏱️ Relative timestamps ("just now", "5m ago")
- ✨ Animated typing dots, message fade-in, scroll-to-bottom pill
- 😊 Emoji picker, auto-growing input (Enter to send · Shift+Enter for newline)
- 🔔 Notification sound (toggleable)
- 🔌 Connection status indicator with automatic reconnect

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.5 (Web, WebSocket, Thymeleaf) |
| Messaging | STOMP over SockJS, Spring's simple in-memory broker |
| Frontend | Thymeleaf template, Bootstrap 5.3 + Bootstrap Icons, `@stomp/stompjs`, SockJS |
| Build | Maven (wrapper included) |
| Boilerplate | Lombok |

---

## 🚀 Getting Started

### Prerequisites
- **Java 17+** (`java -version`)
- No local Maven needed — the project ships with the Maven Wrapper.

### Run it
```bash
git clone https://github.com/chrisgadekar/chat-app.git
cd chat-app/ChatApp/app
./mvnw spring-boot:run
```

Then open **http://localhost:8080/chat** (the bare URL `http://localhost:8080` redirects there too).

> Open it in **two browser tabs** with different names to see messages, presence, reactions, and typing indicators sync live.

---

## 🧭 How It Works

```
Browser ──HTTP GET /chat──────────────▶ ChatController → chat.html (Thymeleaf)
Browser ──SockJS /chat (STOMP)────────▶ WebSocketConfig endpoint
   send  → /app/chat/{room}/send       → broadcast to /topic/room/{room}
   join  → /app/chat/{room}/addUser    → JOIN + online list
   typing→ /app/chat/{room}/typing     → /topic/room/{room}/typing
   react → /app/chat/{room}/react      → REACTION to /topic/room/{room}
   leave → SessionDisconnectEvent      → LEAVE + updated online list
```

REST helpers: `GET /api/rooms`, `GET /api/rooms/{room}/history`, `GET /api/name-available?name=…`.

---

## 📁 Project Structure

```
ChatApp/app/
├── pom.xml
└── src/main/
    ├── java/com/chat/app/
    │   ├── AppApplication.java          # Spring Boot entry point
    │   ├── config/WebSocketConfig.java   # STOMP broker + SockJS endpoint
    │   ├── controller/ChatController.java # message routing, rooms, REST
    │   └── model/
    │       ├── ChatMessage.java          # message DTO
    │       └── MessageType.java          # CHAT / JOIN / LEAVE / TYPING / REACTION
    └── resources/
        ├── application.properties
        └── templates/chat.html           # full single-page UI
```

---

## ⚠️ Notes & Limitations

This is a demo focused on real-time mechanics and UI, so state is kept **in memory**:

- Channels, history, presence, and reactions **reset on restart**.
- It runs as a **single instance** — scaling out would need an external broker (e.g. RabbitMQ) or Redis-backed state.
- Reactions are broadcast live but **not stored in history**, so they don't reappear after a room switch.
- No authentication yet — usernames are self-chosen and trust-based.

## 🗺️ Possible Next Steps
- Authentication + a database (JPA/Postgres) so messages and users persist
- User-created channels & private (1:1) direct messages
- `@mention` autocomplete and persisted reactions
- Dockerfile / docker-compose for one-command deployment
