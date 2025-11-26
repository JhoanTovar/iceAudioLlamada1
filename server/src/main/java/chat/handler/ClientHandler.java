package chat.handler;

import chat.controller.ChatController;
import chat.model.*;
import chat.protocol.Protocol;
import chat.protocol.Protocol.Command;
import chat.protocol.Protocol.Packet;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatController controller;
    private final ClientRegistry clientRegistry;

    private BufferedReader in;
    private PrintWriter out;
    private User currentUser;

    public ClientHandler(Socket socket, ChatController controller, ClientRegistry clientRegistry) {
        this.socket = socket;
        this.controller = controller;
        this.clientRegistry = clientRegistry;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while ((line = in.readLine()) != null) {
                Packet packet = Protocol.deserialize(line);
                handlePacket(packet);
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " +
                    (currentUser != null ? currentUser.getUsername() : "desconocido"));
        } finally {
            disconnect();
        }
    }

    private void handlePacket(Packet packet) {
        try {
            Packet response;

            switch (packet.getCommand()) {
                case REGISTER:
                    response = controller.handleRegister(packet.getData());
                    if (response.getCommand() == Command.SUCCESS) {
                        currentUser = Protocol.fromJson(response.getData(), User.class);
                        clientRegistry.register(currentUser.getId(), this);
                        System.out.println("Nuevo usuario registrado: " + currentUser.getUsername());
                    }
                    send(response);
                    break;

                case LOGIN:
                    response = controller.handleLogin(packet.getData());
                    if (response.getCommand() == Command.SUCCESS) {
                        currentUser = Protocol.fromJson(response.getData(), User.class);
                        clientRegistry.register(currentUser.getId(), this);
                        System.out.println("Usuario conectado: " + currentUser.getUsername());
                    }
                    send(response);
                    break;

                case LOGOUT:
                    controller.handleLogout(currentUser.getId());
                    send(new Packet(Command.SUCCESS, "Sesion cerrada"));
                    break;

                case SEND_MESSAGE:
                    Message message = Protocol.fromJson(packet.getData(), Message.class);
                    message.setSenderId(currentUser.getId());
                    message.setSenderUsername(currentUser.getUsername());
                    response = controller.handleSendMessage(message);
                    send(response);

                    // Notificar al destinatario
                    if (message.getReceiverId() != null) {
                        ClientHandler receiver = clientRegistry.get(message.getReceiverId());
                        if (receiver != null) {
                            Packet notification = new Packet(Command.RECEIVE_MESSAGE, Protocol.toJson(message));
                            receiver.send(notification);
                        }
                    }
                    break;

                case SEND_GROUP_MESSAGE:
                    Message groupMessage = Protocol.fromJson(packet.getData(), Message.class);
                    groupMessage.setSenderId(currentUser.getId());
                    groupMessage.setSenderUsername(currentUser.getUsername());
                    response = controller.handleSendGroupMessage(groupMessage);
                    send(response);

                    // Notificar a miembros del grupo 
                    clientRegistry.notifyGroupMessage(groupMessage, currentUser.getId());
                    break;

                // ========== NUEVO: CASOS PARA MENSAJES DE AUDIO ==========
                case SEND_AUDIO_MESSAGE:
                    handleSendAudioMessage(packet);
                    break;

                case SEND_GROUP_AUDIO_MESSAGE:
                    handleSendGroupAudioMessage(packet);
                    break;
                // =========================================================

                case GET_HISTORY:
                    int otherUserId = Integer.parseInt(packet.getData());
                    response = controller.handleGetHistory(currentUser.getId(), otherUserId);
                    send(response);
                    break;

                case GET_GROUP_MESSAGES:
                    int groupId = Integer.parseInt(packet.getData());
                    response = controller.handleGetGroupMessages(groupId);
                    send(response);
                    break;

                case CREATE_GROUP:
                    response = controller.handleCreateGroup(packet.getData(), currentUser.getId());
                    send(response);
                    break;

                case GET_USER_GROUPS:
                    response = controller.handleGetUserGroups(currentUser.getId());
                    send(response);
                    break;

                case ADD_TO_GROUP:
                    String[] parts = packet.getData().split(",");
                    response = controller.handleAddToGroup(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1])
                    );
                    send(response);
                    break;

                case GET_USERS:
                    response = controller.handleGetUsers();
                    send(response);
                    break;

                case VOICE_NOTE_DATA:
                    handleVoiceNoteData(packet);
                    break;

                case CALL_REQUEST:
                    handleCallRequest(packet);
                    break;

                case CALL_ACCEPT:
                    handleCallAccept(packet);
                    break;

                case CALL_REJECT:
                    handleCallReject(packet);
                    break;

                case CALL_END:
                    handleCallEnd(packet);
                    break;

                default:
                    sendError("Comando no reconocido");
            }
        } catch (Exception e) {
            sendError("Error procesando comando: " + e.getMessage());
            e.printStackTrace(); // Para debug
        }
    }

    // ========== NUEVO: MÉTODO PARA MENSAJES DE AUDIO PRIVADOS ==========
    @SuppressWarnings("unchecked")
    private void handleSendAudioMessage(Packet packet) {
        try {
            Map<String, Object> audioData = Protocol.fromJson(packet.getData(), Map.class);
            
            int senderId = ((Number) audioData.get("senderId")).intValue();
            String senderUsername = (String) audioData.get("senderUsername");
            int receiverId = ((Number) audioData.get("receiverId")).intValue();
            int duration = ((Number) audioData.get("duration")).intValue();
            
            System.out.println("📝 Guardando audio: " + senderUsername + " -> userId:" + receiverId + " (" + duration + "s)");
            
            Packet response = controller.handleSendAudioMessage(senderId, senderUsername, receiverId, duration);
            send(response);
            
            if (response.getCommand() == Command.SUCCESS) {
                System.out.println("✅ Audio guardado exitosamente");
            }
        } catch (Exception e) {
            System.err.println("❌ Error guardando audio: " + e.getMessage());
            e.printStackTrace();
            sendError("Error guardando mensaje de audio");
        }
    }

    // ========== NUEVO: MÉTODO PARA MENSAJES DE AUDIO GRUPALES ==========
    @SuppressWarnings("unchecked")
    private void handleSendGroupAudioMessage(Packet packet) {
        try {
            Map<String, Object> audioData = Protocol.fromJson(packet.getData(), Map.class);
            
            int senderId = ((Number) audioData.get("senderId")).intValue();
            String senderUsername = (String) audioData.get("senderUsername");
            int groupId = ((Number) audioData.get("groupId")).intValue();
            int duration = ((Number) audioData.get("duration")).intValue();
            
            System.out.println("📝 Guardando audio grupal: " + senderUsername + " -> grupo:" + groupId + " (" + duration + "s)");
            
            Packet response = controller.handleSendGroupAudioMessage(senderId, senderUsername, groupId, duration);
            send(response);
            
            if (response.getCommand() == Command.SUCCESS) {
                System.out.println("✅ Audio grupal guardado exitosamente");
            }
        } catch (Exception e) {
            System.err.println("❌ Error guardando audio grupal: " + e.getMessage());
            e.printStackTrace();
            sendError("Error guardando mensaje de audio grupal");
        }
    }
    // ====================================================================

    private void handleVoiceNoteData(Packet packet) {
        try {
            VoiceNoteData voiceNote = Protocol.fromJson(packet.getData(), VoiceNoteData.class);

            System.out.println("Nota de voz recibida de usuario " + voiceNote.getSenderId() +
                    " (" + voiceNote.getAudioBytes().length + " bytes)");

            if (voiceNote.isGroupMessage()) {
                // 1. Guardar la nota de voz en el historial del grupo
                controller.handleSendGroupAudioMessage(
                        voiceNote.getSenderId(),
                        currentUser.getUsername(),
                        voiceNote.getGroupId(),
                        voiceNote.getDurationSeconds()
                );

                // 2. Enviar a todos los miembros del grupo excepto el remitente
                clientRegistry.notifyGroupVoiceNote(voiceNote, currentUser.getId());

                // 3. Confirmación al remitente
                send(new Packet(Command.SUCCESS, "Nota de voz enviada al grupo y registrada en historial"));

            } else {
                // Mensaje privado
                if (voiceNote.getReceiverId() != 0) {
                    ClientHandler receiver = clientRegistry.get(voiceNote.getReceiverId());
                    if (receiver != null) {
                        // Guardar en historial
                        controller.handleSendAudioMessage(
                                voiceNote.getSenderId(),
                                currentUser.getUsername(),
                                voiceNote.getReceiverId(),
                                voiceNote.getDurationSeconds()
                        );

                        // Notificar al destinatario
                        Packet notification = new Packet(Command.VOICE_NOTE_DATA, Protocol.toJson(voiceNote));
                        receiver.send(notification);
                        send(new Packet(Command.SUCCESS, "Nota de voz enviada"));

                    } else {
                        sendError("Usuario no disponible");
                    }
                } else {
                    sendError("ID de destinatario inválido");
                }
            }

        } catch (Exception e) {
            System.err.println("Error procesando nota de voz: " + e.getMessage());
            sendError("Error procesando nota de voz");
            e.printStackTrace();
        }
    }


    private void handleCallRequest(Packet packet) {
        Call call = Protocol.fromJson(packet.getData(), Call.class);
        call.setCallerId(currentUser.getId());
        call.setCallerUsername(currentUser.getUsername());

        controller.handleCallRequest(call);

        if (call.isGroupCall()) {
            clientRegistry.notifyGroupCall(call, currentUser.getId());
        } else {
            ClientHandler receiver = clientRegistry.get(call.getReceiverId());
            if (receiver != null) {
                Packet notification = new Packet(Command.CALL_REQUEST, Protocol.toJson(call));
                receiver.send(notification);
                send(new Packet(Command.SUCCESS, "Llamada iniciada"));
            } else {
                sendError("Usuario no disponible");
            }
        }
    }

    private void handleCallAccept(Packet packet) {
        Call call = Protocol.fromJson(packet.getData(), Call.class);
        Packet response = controller.handleCallAccept(call.getCallerId(), call.getReceiverId());
        send(response);

        ClientHandler caller = clientRegistry.get(call.getCallerId());
        if (caller != null) {
            Packet notification = new Packet(Command.CALL_ACCEPT, Protocol.toJson(call));
            caller.send(notification);
        }
    }

    private void handleCallReject(Packet packet) {
        Call call = Protocol.fromJson(packet.getData(), Call.class);
        Packet response = controller.handleCallReject(call.getCallerId(), call.getReceiverId());
        send(response);

        ClientHandler caller = clientRegistry.get(call.getCallerId());
        if (caller != null) {
            Packet notification = new Packet(Command.CALL_REJECT, Protocol.toJson(call));
            caller.send(notification);
        }
    }

    private void handleCallEnd(Packet packet) {
        // CORRECCIÓN: Manejar cuando packet.getData() es solo el userId (desde proxy)
        int userId;
        Call call = null;
        
        try {
            // Intentar parsear como Call primero (desde ICE)
            call = Protocol.fromJson(packet.getData(), Call.class);
            userId = currentUser.getId();
        } catch (Exception e) {
            // Si falla, es solo el userId (desde proxy)
            userId = Integer.parseInt(packet.getData());
        }
        
        System.out.println("📴 Finalizando llamada para userId: " + userId);
        
        Packet response = controller.handleCallEnd(userId);
        send(response);

        // Si tenemos el objeto Call, notificar al otro usuario
        if (call != null) {
            int otherUserId = call.getCallerId() == currentUser.getId() ?
                    call.getReceiverId() : call.getCallerId();

            ClientHandler other = clientRegistry.get(otherUserId);
            if (other != null) {
                Packet notification = new Packet(Command.CALL_END, Protocol.toJson(call));
                other.send(notification);
            }
        }
    }

    public void send(Packet packet) {
        out.println(Protocol.serialize(packet));
    }

    private void sendError(String error) {
        Packet packet = new Packet(Command.ERROR);
        packet.setError(error);
        send(packet);
    }

    private void disconnect() {
        try {
            if (currentUser != null) {
                controller.handleLogout(currentUser.getId());
                clientRegistry.unregister(currentUser.getId());
            }
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}