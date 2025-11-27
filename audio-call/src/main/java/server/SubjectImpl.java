package server;

import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import Demo.*;
import com.zeroc.Ice.Current;

public class SubjectImpl implements Subject {

    private final Map<String, ObserverPrx> observers = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private final Map<String, String> activeCalls = new ConcurrentHashMap<>();
    
    private final Map<String, Set<String>> groupInvited = new ConcurrentHashMap<>();  // Usuarios invitados
    private final Map<String, Set<String>> groupActive = new ConcurrentHashMap<>();   // Usuarios que aceptaron
     private final Map<String, Set<String>> groupMembers = new ConcurrentHashMap<>();

    private final Map<String, Boolean> activeGroups = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private final long STALE_MS = 300_000;
    private final AtomicLong idGen = new AtomicLong(System.currentTimeMillis());

    public SubjectImpl() {
        cleaner.scheduleAtFixedRate(() -> {
            try {
                cleanupStaleUsers();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void attach(String userId, ObserverPrx obs, Current c) {
        try {
            ObserverPrx proxy = obs.ice_fixed(c.con);
            observers.put(userId, proxy);
            lastSeen.put(userId, System.currentTimeMillis());
            System.out.println("[SERVER] Usuario conectado: " + userId);

            if (c.con != null) {
                c.con.setCloseCallback(con -> handleDisconnection(userId));
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Error en attach: " + e);
        }
    }

    private void handleDisconnection(String userId) {
        System.out.println("[SERVER] Desconexion: " + userId);
        observers.remove(userId);
        lastSeen.remove(userId);
        
        String peer = activeCalls.remove(userId);
        if (peer != null) {
            activeCalls.remove(peer);
            notifyCallEnded(peer, userId);
        }
        
        removeFromAllGroups(userId);
    }

    @Override
    public void sendAudio(String fromUser, byte[] data, Current c) {
        if (fromUser == null || data == null || data.length == 0) return;

        lastSeen.put(fromUser, System.currentTimeMillis());
        
        String target = activeCalls.get(fromUser);
        if (target == null) {
            System.err.println("[SERVER] " + fromUser + " no tiene llamada activa");
            return;
        }

        ObserverPrx prx = observers.get(target);
        if (prx == null) {
            System.err.println("[SERVER] Receptor desconectado: " + target);
            activeCalls.remove(fromUser);
            activeCalls.remove(target);
            return;
        }

        try {
            prx.receiveAudioAsync(data).whenComplete((result, ex) -> {
                if (ex != null) {
                    System.err.println("[SERVER] Error enviando audio: " + ex.getMessage());
                }
            });
            
            lastSeen.put(target, System.currentTimeMillis());
        } catch (Exception e) {
            System.err.println("[SERVER] Error en sendAudio: " + e);
        }
    }

    @Override
    public void sendAudioMessage(String fromUser, String toUser, byte[] data, Current c) {
        if (toUser == null || fromUser == null || data == null || data.length == 0) {
            System.err.println("[SERVER] sendAudioMessage: parametros invalidos");
            return;
        }

        ObserverPrx dest = observers.get(toUser);
        if (dest == null) {
            System.err.println("[SERVER] " + toUser + " no conectado");
            return;
        }

        try {
            dest.receiveAudioMessageAsync(data).whenComplete((result, ex) -> {
                if (ex != null) {
                    System.err.println("[SERVER] Error enviando mensaje de audio: " + ex.getMessage());
                } else {
                    System.out.println("[SERVER] Mensaje de audio enviado: " + fromUser + " -> " + toUser + " (" + data.length + " bytes)");
                }
            });
        } catch (Exception e) {
            System.err.println("[SERVER] Error en sendAudioMessage: " + e);
        }
    }

    @Override
    public void startCall(String fromUser, String toUser, Current c) {
        if (toUser == null || fromUser == null) return;
        
        System.out.println("[SERVER] Iniciando llamada: " + fromUser + " -> " + toUser);

        ObserverPrx dest = observers.get(toUser);
        if (dest == null) {
            System.err.println("[SERVER] " + toUser + " no esta conectado");
            return;
        }

        try {
            dest.incomingCallAsync(fromUser).whenComplete((result, ex) -> {
                if (ex != null) {
                    System.err.println("[SERVER] Error notificando llamada: " + ex.getMessage());
                } else {
                    System.out.println("[SERVER] Notificacion enviada a " + toUser);
                }
            });
        } catch (Exception e) {
            System.err.println("[SERVER] Error en startCall: " + e);
        }
    }

    @Override
    public void acceptCall(String fromUser, String toUser, Current c) {
        if (fromUser == null || toUser == null) return;

        System.out.println("[SERVER] Llamada aceptada: " + fromUser + " <- " + toUser);

        ObserverPrx caller = observers.get(fromUser);
        if (caller == null) {
            System.err.println("[SERVER] Caller desconectado: " + fromUser);
            return;
        }

        try {
            caller.callAcceptedAsync(toUser).whenComplete((result, ex) -> {
                if (ex == null) {
                    activeCalls.put(fromUser, toUser);
                    activeCalls.put(toUser, fromUser);
                    System.out.println("[SERVER] Llamada establecida entre " + fromUser + " y " + toUser);
                } else {
                    System.err.println("[SERVER] Error aceptando llamada: " + ex.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("[SERVER] Error en acceptCall: " + e);
        }
    }

    @Override
    public void rejectCall(String fromUser, String toUser, Current c) {
        System.out.println("[SERVER] Llamada rechazada: " + fromUser + " <- " + toUser);

        if (fromUser == null || toUser == null) return;

        ObserverPrx caller = observers.get(fromUser);
        if (caller != null) {
            try {
                caller.callRejectedAsync(toUser);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void colgar(String fromUser, String toUser, Current c) {
        System.out.println("[SERVER] Colgando: " + fromUser + " -> " + toUser);

        if (fromUser != null) activeCalls.remove(fromUser);
        if (toUser != null) activeCalls.remove(toUser);

        try {
            if (toUser != null) {
                ObserverPrx receiver = observers.get(toUser);
                if (receiver != null) {
                    receiver.callColgadaAsync(fromUser);
                }
            }

            if (fromUser != null) {
                ObserverPrx caller = observers.get(fromUser);
                if (caller != null) {
                    caller.callColgadaAsync(fromUser);
                }
            }
        } catch (Exception ignored) {}
    }

    private void notifyCallEnded(String userId, String byUser) {
        ObserverPrx prx = observers.get(userId);
        if (prx != null) {
            try {
                prx.callColgadaAsync(byUser);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public String createGroupCall(String fromUser, String[] users, Current current) {
        if (fromUser == null || users == null) {
            throw new IllegalArgumentException("fromUser/users no pueden ser null");
        }

        String groupId = "group-" + idGen.incrementAndGet();
        
        Set<String> invited = ConcurrentHashMap.newKeySet();
        for (String u : users) {
            if (u != null && !u.trim().isEmpty() && !u.equals(fromUser)) {
                invited.add(u);
            }
        }
        groupInvited.put(groupId, invited);
        
        Set<String> active = ConcurrentHashMap.newKeySet();
        active.add(fromUser);
        groupActive.put(groupId, active);
        
        activeGroups.put(groupId, true);

        String[] allMembers = getAllGroupMembers(groupId);
        for (String invitee : invited) {
            ObserverPrx prx = observers.get(invitee);
            if (prx != null) {
                try {
                    prx.incomingGroupCallAsync(groupId, fromUser, allMembers);
                    System.out.println("[SERVER] Notificacion enviada a " + invitee);
                } catch (Exception ex) {
                    System.err.println("[SERVER] Error notificando incomingGroupCall a " + invitee);
                }
            }
        }

        System.out.println("[SERVER] Grupo creado: " + groupId + " por " + fromUser + 
                          " | Activos: " + active.size() + " | Invitados: " + invited.size());
        return groupId;
    }

    private String[] getAllGroupMembers(String groupId) {
        Set<String> all = new HashSet<>();
        Set<String> active = groupActive.get(groupId);
        Set<String> invited = groupInvited.get(groupId);
        if (active != null) all.addAll(active);
        if (invited != null) all.addAll(invited);
        return all.toArray(new String[0]);
    }

    @Override
    public void joinGroupCall(String groupId, String user, Current current) {
        if (groupId == null || user == null) return;

        Set<String> active = groupActive.get(groupId);
        Set<String> invited = groupInvited.get(groupId);
        
        if (active == null) {
            System.err.println("[SERVER] Grupo no existe: " + groupId);
            return;
        }

        if (invited != null) {
            invited.remove(user);
        }
        active.add(user);
        
        notifyGroupUpdated(groupId);
        System.out.println("[SERVER] " + user + " se unio activamente a " + groupId + 
                          " | Activos: " + active.size());
    }

    @Override
    public void leaveGroupCall(String groupId, String user, Current current) {
        if (groupId == null || user == null) return;

        Set<String> active = groupActive.get(groupId);
        Set<String> invited = groupInvited.get(groupId);
        
        if (active == null) return;

        active.remove(user);
        if (invited != null) {
            invited.remove(user);
        }
        
        if (active.isEmpty()) {
            groupActive.remove(groupId);
            groupInvited.remove(groupId);
            activeGroups.remove(groupId);
            System.out.println("[SERVER] Grupo vacio eliminado: " + groupId);
            notifyGroupEnded(groupId);
        } else {
            notifyGroupUpdated(groupId);
            System.out.println("[SERVER] " + user + " salio de " + groupId);
        }
    }

    private void notifyGroupEnded(String groupId) {
        for (Map.Entry<String, ObserverPrx> entry : observers.entrySet()) {
            try {
                entry.getValue().groupCallEndedAsync(groupId);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void sendAudioGroup(String groupId, String fromUser, byte[] data, Current current) {
        if (groupId == null || fromUser == null || data == null || data.length == 0) return;

        Set<String> active = groupActive.get(groupId);
        if (active == null || active.isEmpty()) {
            System.err.println("[SERVER] Grupo sin miembros activos: " + groupId);
            return;
        }
        
        if (!Boolean.TRUE.equals(activeGroups.get(groupId))) {
            System.err.println("[SERVER] Grupo inactivo: " + groupId);
            return;
        }

        if (!active.contains(fromUser)) {
            System.err.println("[SERVER] Usuario " + fromUser + " no esta activo en el grupo " + groupId);
            return;
        }

        lastSeen.put(fromUser, System.currentTimeMillis());

        String[] snapshot = active.toArray(new String[0]);
        for (String member : snapshot) {
            if (member.equals(fromUser)) continue;
            
            ObserverPrx prx = observers.get(member);
            if (prx != null) {
                try {
                    prx.receiveAudioAsync(data).whenComplete((result, ex) -> {
                        if (ex != null) {
                            System.err.println("[SERVER] Error enviando audio grupal a " + member + ": " + ex.getMessage());
                        }
                    });
                } catch (Exception ex) {
                    System.err.println("[SERVER] Error enviando audio grupal a " + member);
                }
            }
        }
    }

    @Override
    public void sendAudioMessageGroup(String fromUser, String groupId, byte[] data, Current current) {
        if (groupId == null || fromUser == null || data == null || data.length == 0) return;

        Set<String> members = groupMembers.get(groupId);
        if (members == null || members.isEmpty()) {
            System.err.println("[SERVER] Grupo vacio: " + groupId);
            return;
        }

        String[] snapshot = members.toArray(new String[0]);
        for (String member : snapshot) {
            if (member.equals(fromUser)) continue;
            
            ObserverPrx prx = observers.get(member);
            if (prx != null) {
                try {
                    // ✅ CRÍTICO: Usar método asíncrono para evitar bloqueos
                    prx.receiveAudioMessageGroupAsync(groupId, data);
                } catch (Exception ex) {
                    System.err.println("[SERVER] Error enviando mensaje de audio grupal a " + member);
                }
            }
        }

        lastSeen.put(fromUser, System.currentTimeMillis());
        System.out.println("[SERVER] Mensaje de audio grupal enviado: " + fromUser + " -> " + groupId);
    }

    private void notifyGroupUpdated(String groupId) {
        Set<String> active = groupActive.get(groupId);
        if (active == null) return;
        
        String[] arr = active.toArray(new String[0]);
        for (String member : arr) {
            ObserverPrx prx = observers.get(member);
            if (prx != null) {
                try {
                    prx.groupCallUpdatedAsync(groupId, arr);
                } catch (Exception ignored) {}
            }
        }
    }

    private void removeFromAllGroups(String userId) {
        List<String> groupsToUpdate = new ArrayList<>();
        List<String> groupsToRemove = new ArrayList<>();
        
        for (String groupId : groupActive.keySet()) {
            Set<String> active = groupActive.get(groupId);
            Set<String> invited = groupInvited.get(groupId);
            
            boolean wasActive = active != null && active.remove(userId);
            boolean wasInvited = invited != null && invited.remove(userId);
            
            if (wasActive || wasInvited) {
                if (active == null || active.isEmpty()) {
                    groupsToRemove.add(groupId);
                } else {
                    groupsToUpdate.add(groupId);
                }
            }
        }

        for (String gid : groupsToRemove) {
            groupActive.remove(gid);
            groupInvited.remove(gid);
            activeGroups.remove(gid);
            notifyGroupEnded(gid);
            System.out.println("[SERVER] Grupo eliminado por desconexión: " + gid);
        }

        for (String gid : groupsToUpdate) {
            notifyGroupUpdated(gid);
        }
    }

    @Override
    public String[] getConnectedUsers(Current current) {
        long now = System.currentTimeMillis();
        List<String> alive = new ArrayList<>();

        for (Map.Entry<String, ObserverPrx> e : observers.entrySet()) {
            String user = e.getKey();
            Long seen = lastSeen.get(user);
            
            if (seen == null) {
                lastSeen.put(user, now);
                alive.add(user);
            } else if (now - seen <= STALE_MS) {
                alive.add(user);
            }
        }

        return alive.toArray(new String[0]);
    }

    private void cleanupStaleUsers() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, Long> e : lastSeen.entrySet()) {
            if (now - e.getValue() > STALE_MS) {
                toRemove.add(e.getKey());
            }
        }

        for (String u : toRemove) {
            handleDisconnection(u);
        }
    }

    public void shutdown() {
        cleaner.shutdownNow();
        groupActive.clear();
        groupInvited.clear();
        activeGroups.clear();
    }

      @Override
    public void joinMessagingGroup(String groupId, String[] users, Current current) {
        if (groupId == null || users == null) {
            throw new IllegalArgumentException("groupId/users no pueden ser null");
        }

        groupMembers.putIfAbsent(groupId, ConcurrentHashMap.newKeySet());
        Set<String> members = groupMembers.get(groupId);

        for (String u : users) {
            if (u != null && !u.trim().isEmpty()) {
                members.add(u);
            }
        }

        System.out.println("[SERVER] 💬 Usuarios añadidos al grupo " 
                            + groupId + ": " + members.size() + " miembros");

        String[] arr = members.toArray(new String[0]);
        for (String member : arr) {
            ObserverPrx prx = observers.get(member);
            if (prx != null) {
                try {
                    prx.groupUsersUpdatedAsync(groupId, arr);
                } catch (Exception ex) {
                    System.err.println("[SERVER] Error notificando groupUsersUpdated a " + member);
                }

            }
        }
    }
    private void notifyGroupUsersUpdated(String groupId, Set<String> members) {
    String[] arr = members.toArray(new String[0]);

    for (String member : arr) {
        ObserverPrx prx = observers.get(member);
        if (prx != null) {
            try {
                prx.groupUsersUpdatedAsync(groupId, arr);
            } catch (Exception ex) {
                System.err.println("[SERVER] Error notificando groupUsersUpdated a " + member);
            }
        }
    }
}

}
