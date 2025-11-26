package chat.service.impl;

import chat.model.Call;
import chat.repository.CallRepository;
import chat.service.CallService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CallServiceImpl implements CallService {
    private final Map<Integer, Call> activeCalls = new ConcurrentHashMap<>();
    private final CallRepository callRepository;

    public CallServiceImpl(CallRepository callRepository) {
        this.callRepository = callRepository;
    }

    @Override
    public void initiateCall(Call call) {
        call.setStatus(Call.CallStatus.RINGING);
        call.setStartTime(LocalDateTime.now()); // IMPORTANTE: Registrar hora de inicio
        
        Call savedCall = callRepository.save(call);
        System.out.println("✅ Llamada iniciada: " + savedCall);
    }

    @Override
    public void acceptCall(int callerId, int receiverId) {
        // Buscar la llamada pendiente en la BD
        Call existingCall = callRepository.findByUserId(callerId).stream()
            .filter(c -> c.getReceiverId() == receiverId && c.getStatus() == Call.CallStatus.RINGING)
            .findFirst()
            .orElse(null);
        
        if (existingCall != null) {
            // Actualizar llamada existente
            existingCall.setStatus(Call.CallStatus.ACTIVE);
            existingCall.setStartTime(LocalDateTime.now());
            callRepository.save(existingCall);
            
            // Registrar en activeCalls
            activeCalls.put(callerId, existingCall);
            activeCalls.put(receiverId, existingCall);
            
            System.out.println("✅ Llamada aceptada: " + existingCall);
        } else {
            System.out.println("⚠️ No se encontró llamada pendiente entre " + callerId + " y " + receiverId);
        }
    }

    @Override
    public void rejectCall(int callerId, int receiverId) {
        callRepository.findByUserId(callerId).stream()
            .filter(c -> c.getReceiverId() == receiverId && c.getStatus() == Call.CallStatus.RINGING)
            .findFirst()
            .ifPresent(call -> callRepository.updateCallStatus(call.getId(), "REJECTED"));
    }

    @Override
    public void endCall(int userId) {
        Call call = activeCalls.remove(userId);

        if (call != null) {
            int otherUserId = (call.getCallerId() == userId)
                    ? call.getReceiverId()
                    : call.getCallerId();

            activeCalls.remove(otherUserId);
            call.setEndTime(LocalDateTime.now());
            call.setStatus(Call.CallStatus.ENDED);

            if (call.getStartTime() != null) {
                // Calcula la duración en segundos entre inicio y fin
                long durationSeconds = ChronoUnit.SECONDS.between(call.getStartTime(), call.getEndTime());
                call.setDurationSeconds((int) durationSeconds);
                
                System.out.println("✅ Llamada terminada: " + call);
                
                // CORRECCIÓN CRÍTICA: Guardar el registro COMPLETO de la llamada
                callRepository.save(call);
                
                // También actualizar si ya existía un registro previo
                if (call.getId() > 0) {
                    callRepository.endCall(call.getId(), call.getDurationSeconds());
                }
            } else {
                System.out.println("⚠️ Llamada sin startTime, no se puede calcular duración");
            }
        } else {
            System.out.println("⚠️ No se encontró llamada activa para userId: " + userId);
        }
    }

    @Override
    public List<Call> getCallsBetweenUsers(int userId1, int userId2) {
        return callRepository.findCallsBetweenUsers(userId1, userId2);
    }

}
