package chat.controller;

import chat.model.*;
import chat.protocol.Protocol;
import chat.protocol.Protocol.Command;
import chat.protocol.Protocol.Packet;
import chat.service.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatController {
    private final UserService userService;
    private final MessageService messageService;
    private final GroupService groupService;
    private final CallService callService;
    
    public ChatController(UserService userService, MessageService messageService, 
                         GroupService groupService, CallService callService) {
        this.userService = userService;
        this.messageService = messageService;
        this.groupService = groupService;
        this.callService = callService;
    }
    
    public Packet handleRegister(String username) {
        try {
            User user = userService.register(username);
            return new Packet(Command.SUCCESS, Protocol.toJson(user));
        } catch (Exception e) {
            return createErrorPacket(e.getMessage());
        }
    }
    
    public Packet handleGetVoiceHistory(int userId1, int userId2) {
        try {
            List<Message> voiceMessages = messageService.getVoiceMessages(userId1, userId2);
            List<Call> callHistory = callService.getCallsBetweenUsers(userId1, userId2);

            VoiceHistoryResponse response = new VoiceHistoryResponse(voiceMessages, callHistory);

            return new Packet(Command.SUCCESS, Protocol.toJson(response));
        } catch (Exception e) {
            return createErrorPacket(e.getMessage());
        }
    }

    public Packet handleLogin(String username) {
        try {
            User user = userService.login(username);
            return new Packet(Command.SUCCESS, Protocol.toJson(user));
        } catch (Exception e) {
            return createErrorPacket(e.getMessage());
        }
    }
    
    public void handleLogout(int userId) {
        userService.logout(userId);
    }
    
    public Packet handleSendMessage(Message message) {
        try {
            Message savedMessage = messageService.sendPrivateMessage(
                message.getSenderId(),
                message.getSenderUsername(),
                message.getReceiverId(),
                message.getContent()
            );
            return new Packet(Command.SUCCESS, "Mensaje enviado");
        } catch (Exception e) {
            return createErrorPacket(e.getMessage());
        }
    }
    
    public Packet handleSendGroupMessage(Message message) {
        try {
            Message savedMessage = messageService.sendGroupMessage(
                message.getSenderId(),
                message.getSenderUsername(),
                message.getGroupId(),
                message.getContent()
            );
            return new Packet(Command.SUCCESS, "Mensaje enviado al grupo");
        } catch (Exception e) {
            return createErrorPacket(e.getMessage());
        }
    }

    // NUEVO: Handler para mensajes de audio privados
public Packet handleSendAudioMessage(int senderId, String senderUsername, int receiverId, int duration) {
    try {
        Message audioMessage = messageService.sendVoiceNote(
            senderId, 
            senderUsername, 
            receiverId, 
            0, // groupId = 0 para mensajes privados
            duration
        );
        
        System.out.println("✅ Audio guardado: " + audioMessage);
        return new Packet(Command.SUCCESS, "Audio guardado");
    } catch (Exception e) {
        return createErrorPacket(e.getMessage());
    }
}

// NUEVO: Handler para mensajes de audio grupales
public Packet handleSendGroupAudioMessage(int senderId, String senderUsername, int groupId, int duration) {
    try {
        Message audioMessage = messageService.sendVoiceNote(
            senderId, 
            senderUsername, 
            0, // receiverId = 0 para mensajes grupales
            groupId,
            duration
        );
        
        System.out.println("✅ Audio grupal guardado: " + audioMessage);
        return new Packet(Command.SUCCESS, "Audio grupal guardado");
    } catch (Exception e) {
        return createErrorPacket(e.getMessage());
    }
}
    
   public Packet handleGetHistory(int userId1, int userId2) {
        // 1. Obtener mensajes
        List<Message> messages = messageService.getChatHistory(userId1, userId2); // tu método ya existente

        // 2. Obtener llamadas
        List<Call> calls = callService.getCallsBetweenUsers(userId1, userId2); // tu método ya existente

        // 3. Lista unificada en formato compatible con cliente
        List<Map<String, Object>> unified = new ArrayList<>();

        // 3a. Mapear mensajes
        for (Message msg : messages) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", msg.getId());
            map.put("type", msg.getType()); // TEXT, VOICE, etc.
            map.put("senderId", msg.getSenderId());
            map.put("senderUsername", msg.getSenderUsername());
            map.put("receiverId", msg.getReceiverId());
            map.put("receiverUsername", null); // no lo tienes en Message
            map.put("groupId", msg.getGroupId());
            map.put("content", msg.getContent());
            map.put("timestamp", msg.getTimestamp());
            unified.add(map);
        }

        // 3b. Mapear llamadas
        for (Call call : calls) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", call.getId());
            map.put("type", "CALL");
            map.put("senderId", call.getCallerId());
            map.put("senderUsername", call.getCallerUsername());
            map.put("receiverId", call.getReceiverId());
            map.put("receiverUsername", call.getReceiverUsername());
            map.put("groupId", call.isGroupCall() ? call.getReceiverId() : null); // si es grupal, guarda groupId
            map.put("status", call.getStatus().toString());
            map.put("startTime", call.getStartTime());
            map.put("endTime", call.getEndTime());
            map.put("durationSeconds", call.getDurationSeconds());
            unified.add(map);
        }

        // 4. Ordenar por fecha (timestamp para mensajes, startTime para llamadas)
        unified.sort((a, b) -> {
            LocalDateTime t1 = a.containsKey("timestamp") ? (LocalDateTime) a.get("timestamp") : (LocalDateTime) a.get("startTime");
            LocalDateTime t2 = b.containsKey("timestamp") ? (LocalDateTime) b.get("timestamp") : (LocalDateTime) b.get("startTime");
            return t1.compareTo(t2);
        });

        // 5. Convertir a JSON para el cliente
        String json = Protocol.toJson(Map.of(
                "success", true,
                "messages", unified
        ));

        return new Packet(Command.SUCCESS, json);
    }



    
    public Packet handleGetGroupMessages(int groupId) {
        List<Message> messages = messageService.getGroupMessages(groupId);
        return new Packet(Command.SUCCESS, Protocol.toJson(messages));
    }
    
    public Packet handleCreateGroup(String name, int creatorId) {
        try {
            Group group = groupService.createGroup(name, creatorId);
            return new Packet(Command.SUCCESS, Protocol.toJson(group));
        } catch (Exception e) {
            return createErrorPacket(e.getMessage());
        }
    }
    
    public Packet handleGetUserGroups(int userId) {
        List<Group> groups = groupService.getUserGroups(userId);
        return new Packet(Command.SUCCESS, Protocol.toJson(groups));
    }
    
    public Packet handleAddToGroup(int groupId, int userId) {
        try {
            groupService.addMemberToGroup(groupId, userId);
            return new Packet(Command.SUCCESS, "Usuario agregado al grupo");
        } catch (Exception e) {
            return createErrorPacket(e.getMessage());
        }
    }
    
    public Packet handleGetUsers() {
        List<User> users = userService.getAllUsers();
        return new Packet(Command.SUCCESS, Protocol.toJson(users));
    }
    
    public void handleCallRequest(Call call) {
        callService.initiateCall(call);
    }
    
    public Packet handleCallAccept(int callerId, int receiverId) {
        callService.acceptCall(callerId, receiverId);
        return new Packet(Command.SUCCESS, "Llamada aceptada");
    }
    
    public Packet handleCallReject(int callerId, int receiverId) {
        callService.rejectCall(callerId, receiverId);
        return new Packet(Command.SUCCESS, "Llamada rechazada");
    }
    
    public Packet handleCallEnd(int userId) {
        callService.endCall(userId);
        return new Packet(Command.SUCCESS, "Llamada finalizada");
    }
    
    private Packet createErrorPacket(String error) {
        Packet packet = new Packet(Command.ERROR);
        packet.setError(error);
        return packet;
    }
}
