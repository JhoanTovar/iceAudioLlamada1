package chat.service;

import java.util.List;

import chat.model.Call;

public interface CallService {
    void initiateCall(Call call);
    void acceptCall(int callerId, int receiverId);
    void rejectCall(int callerId, int receiverId);
    void endCall(int userId);
    List<Call> getCallsBetweenUsers(int userId1, int userId2);
}
