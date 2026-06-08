package com.chat.app.controller;

import com.chat.app.model.ChatMessage;
import com.chat.app.model.MessageType;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Controller
public class ChatController {

    /** Predefined channels. Messages can only flow through these. */
    private static final List<String> ROOMS = List.of("general", "random", "tech-talk", "games");

    /** How many recent messages to keep per room for late joiners. */
    private static final int HISTORY_LIMIT = 50;

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicLong idSequence = new AtomicLong();

    // Per-room recent message history.
    private final Map<String, List<ChatMessage>> roomHistory = new ConcurrentHashMap<>();
    // Per-room set of currently-present usernames.
    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();
    // All usernames currently connected anywhere (enforces global uniqueness).
    private final Set<String> activeUsernames = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/{room}/send")
    public void send(@DestinationVariable String room, ChatMessage message) {
        if (!ROOMS.contains(room)) {
            return;
        }
        message.setId("m" + idSequence.incrementAndGet());
        message.setType(MessageType.CHAT);
        message.setRoom(room);
        message.setTimestamp(System.currentTimeMillis());
        addToHistory(room, message);
        messagingTemplate.convertAndSend("/topic/room/" + room, message);
    }

    @MessageMapping("/chat/{room}/addUser")
    public void addUser(@DestinationVariable String room, ChatMessage message,
                        SimpMessageHeaderAccessor headerAccessor) {
        if (!ROOMS.contains(room) || headerAccessor.getSessionAttributes() == null) {
            return;
        }
        String username = message.getSender();
        headerAccessor.getSessionAttributes().put("username", username);
        headerAccessor.getSessionAttributes().put("room", room);

        activeUsernames.add(username);
        usersOf(room).add(username);

        ChatMessage join = system(MessageType.JOIN, username, username + " joined #" + room, room);
        addToHistory(room, join);
        messagingTemplate.convertAndSend("/topic/room/" + room, join);
        broadcastUsers(room);
    }

    /** Sent by a client when it switches away from a room (stays connected). */
    @MessageMapping("/chat/{room}/leave")
    public void leave(@DestinationVariable String room, ChatMessage message,
                      SimpMessageHeaderAccessor headerAccessor) {
        if (!ROOMS.contains(room)) {
            return;
        }
        String username = message.getSender();
        usersOf(room).remove(username);

        ChatMessage leave = system(MessageType.LEAVE, username, username + " left #" + room, room);
        addToHistory(room, leave);
        messagingTemplate.convertAndSend("/topic/room/" + room, leave);
        broadcastUsers(room);
    }

    @MessageMapping("/chat/{room}/typing")
    public void typing(@DestinationVariable String room, ChatMessage message) {
        if (!ROOMS.contains(room)) {
            return;
        }
        message.setType(MessageType.TYPING);
        message.setRoom(room);
        messagingTemplate.convertAndSend("/topic/room/" + room + "/typing", message);
    }

    @MessageMapping("/chat/{room}/react")
    public void react(@DestinationVariable String room, ChatMessage message) {
        if (!ROOMS.contains(room)) {
            return;
        }
        message.setType(MessageType.REACTION);
        message.setRoom(room);
        messagingTemplate.convertAndSend("/topic/room/" + room, message);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) {
            return;
        }
        Object username = attrs.get("username");
        Object room = attrs.get("room");
        if (username == null) {
            return;
        }
        activeUsernames.remove(username.toString());
        if (room != null) {
            usersOf(room.toString()).remove(username.toString());
            ChatMessage leave = system(MessageType.LEAVE, username.toString(),
                    username + " left #" + room, room.toString());
            addToHistory(room.toString(), leave);
            messagingTemplate.convertAndSend("/topic/room/" + room, leave);
            broadcastUsers(room.toString());
        }
    }

    // ----- REST -----

    @GetMapping("/api/rooms")
    @ResponseBody
    public List<String> rooms() {
        return ROOMS;
    }

    @GetMapping("/api/rooms/{room}/history")
    @ResponseBody
    public List<ChatMessage> history(@PathVariable String room) {
        return roomHistory.getOrDefault(room, Collections.emptyList());
    }

    @GetMapping("/api/name-available")
    @ResponseBody
    public Map<String, Boolean> nameAvailable(@RequestParam String name) {
        return Map.of("available", !activeUsernames.contains(name.trim()));
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/chat";
    }

    @GetMapping("chat")
    public String chat() {
        return "chat";
    }

    // ----- helpers -----

    private Set<String> usersOf(String room) {
        return roomUsers.computeIfAbsent(room, k -> ConcurrentHashMap.newKeySet());
    }

    private void broadcastUsers(String room) {
        messagingTemplate.convertAndSend("/topic/room/" + room + "/users", usersOf(room));
    }

    private void addToHistory(String room, ChatMessage message) {
        List<ChatMessage> messages = roomHistory.computeIfAbsent(room, k -> new CopyOnWriteArrayList<>());
        messages.add(message);
        while (messages.size() > HISTORY_LIMIT) {
            messages.remove(0);
        }
    }

    private ChatMessage system(MessageType type, String sender, String content, String room) {
        ChatMessage message = new ChatMessage();
        message.setType(type);
        message.setSender(sender);
        message.setContent(content);
        message.setRoom(room);
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
}
