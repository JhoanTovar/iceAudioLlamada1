package chat.model;

import java.util.Base64;

public class VoiceNoteData {
    private int senderId;
    private int receiverId;       // -1 si es grupal
    private Integer groupId;      // null si es privado
    private String audioData; 
    private int durationSeconds;

    // Constructor para mensajes privados
    public VoiceNoteData(int senderId, int receiverId, byte[] audioBytes, int durationSeconds) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.groupId = null;  // es privado
        this.audioData = Base64.getEncoder().encodeToString(audioBytes);
        this.durationSeconds = durationSeconds;
    }

    // Constructor para mensajes grupales
    public VoiceNoteData(int senderId, int groupId, byte[] audioBytes, int durationSeconds, boolean isGroup) {
        this.senderId = senderId;
        this.groupId = groupId;
        this.receiverId = -1;  // indica que es grupal
        this.audioData = Base64.getEncoder().encodeToString(audioBytes);
        this.durationSeconds = durationSeconds;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public byte[] getAudioBytes() {
        return Base64.getDecoder().decode(audioData);
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isGroupMessage() {
        return groupId != null;
    }
}
