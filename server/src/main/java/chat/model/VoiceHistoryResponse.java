package chat.model;

import java.util.List;

public class VoiceHistoryResponse {
    private List<Message> voiceMessages;
    private List<Call> calls;

    public VoiceHistoryResponse(List<Message> voiceMessages, List<Call> calls) {
        this.voiceMessages = voiceMessages;
        this.calls = calls;
    }

    public List<Message> getVoiceMessages() { return voiceMessages; }
    public List<Call> getCalls() { return calls; }
}

