import Subscriber from "./subscriber.js";

// ===================== IceDelegate.js (COMPLETO Y CORREGIDO) =====================

class IceDelegate {
  constructor() {
    this.communicator = null;
    this.subject = null;

    this.name = null;
    this.currentCall = null;

    this.subscriber = null;

    // callbacks de audio
    this.callbacks = [];
    this.groupCallbacks = new Map(); // groupId → callbacks[]
        this.groupUsersUpdatedCB = null;
    // callbacks de llamadas
    this.incomingCallCB = null;
    this.callAcceptedCB = null;
    this.callRejectedCB = null;
    this.callColgadaCB = null;

    // callbacks grupales
    this.incomingGroupCallCB = null;
    this.groupUpdatedCB = null;
    this.groupEndedCB = null;
    
    this.isInitialized = false;
  }

  // ============================================================
  // INIT
  // ============================================================
  async init(name) {
    this.name = name;
    
    if (this.subject) {
      console.log("⚠️ IceDelegate ya estaba inicializado");
      return true;
    }

    try {
      console.log("🔌 Inicializando Ice.Communicator...");
      this.communicator = Ice.initialize();
      
      console.log("🔍 Conectando al proxy...");
      const proxy = this.communicator.stringToProxy(
        `AudioService:ws -h localhost -p 9099`
      );

      console.log("✨ Casteando a SubjectPrx...");
      this.subject = await Demo.SubjectPrx.checkedCast(proxy);
      
      if (!this.subject) {
        console.error("❌ No pude castear SubjectPrx");
        return false;
      }

      console.log("📡 Creando adapter...");
      const adapter = await this.communicator.createObjectAdapter("");
      await adapter.activate();

      const conn = this.subject.ice_getCachedConnection();
      conn.setAdapter(adapter);

      console.log("👤 Creando subscriber...");
      this.subscriber = new Subscriber(this);

      const callbackPrx = Demo.ObserverPrx.uncheckedCast(
        adapter.addWithUUID(this.subscriber)
      );

      console.log("📝 Registrando con el servidor...");
      await this.subject.attach(this.name, callbackPrx);

      this.isInitialized = true;
      console.log("✅ ICE Delegate listo como:", this.name);
      return true;
    } catch (error) {
      console.error("❌ Error inicializando IceDelegate:", error);
      return false;
    }
  }

  // ============================================================
  // HEARTBEAT + RECONEXIÓN
  // ============================================================
  startHeartbeat() {
    this.heartbeatInterval = setInterval(async () => {
      if (!this.subject) {
        console.warn("⚠️ Conexión ICE perdida");
        this.reconnect();
        return;
      }

      try {
        await this.subject.ice_ping();
      } catch (error) {
        console.error("❌ Heartbeat falló:", error);
        this.reconnect();
      }
    }, 5000);
  }

  async reconnect() {
    console.log("🔄 Intentando reconectar...");
    clearInterval(this.heartbeatInterval);

    this.isInitialized = false;
    this.subject = null;

    const ok = await this.init(this.name);
    if (ok) {
      console.log("✅ Reconexión exitosa");
      if (window.AppController) window.AppController.setupICECallbacks();
    }
  }
  // ============================================================
  // USUARIOS
  // ============================================================
  async getUsers() {
    if (!this.subject) return [];
    try {
      return await this.subject.getConnectedUsers();
    } catch (error) {
      console.error("Error obteniendo usuarios:", error);
      return [];
    }
  }

  // ============================================================
  // LLAMADAS NORMALES
  // ============================================================
  async startCall(target) {
    if (!this.subject) {
      console.error("❌ No hay conexión ICE");
      return false;
    }
    try {
      console.log(`[ICE] 📞 Iniciando llamada a: ${target}`);
      await this.subject.startCall(this.name, target);
      this.currentCall = target;
      return true;
    } catch (error) {
      console.error("❌ Error iniciando llamada:", error);
      return false;
    }
  }

  async acceptCall(fromUser) {
    if (!this.subject) return false;
    try {
      console.log(`[ICE] ✅ Aceptando llamada de: ${fromUser}`);
      await this.subject.acceptCall(fromUser, this.name);
      this.currentCall = fromUser;
      return true;
    } catch (error) {
      console.error("❌ Error aceptando llamada:", error);
      return false;
    }
  }

  async rejectCall(fromUser) {
    if (!this.subject) return false;
    try {
      console.log(`[ICE] ❌ Rechazando llamada de: ${fromUser}`);
      await this.subject.rejectCall(fromUser, this.name);
      return true;
    } catch (error) {
      console.error("❌ Error rechazando llamada:", error);
      return false;
    }
  }

  async colgar(target) {
    if (!this.subject) return false;
    try {
      console.log(`[ICE] 📴 Colgando con: ${target}`);
      await this.subject.colgar(this.name, target);
      if (this.currentCall === target) this.currentCall = null;
      return true;
    } catch (error) {
      console.error("❌ Error colgando:", error);
      return false;
    }
  }

  // ============================================================
  // AUDIO (LLAMADA 1 A 1)
  // ============================================================
  async sendAudio(byteArray) {
    if (!this.subject) {
      console.error("❌ No hay subject");
      return false;
    }
    try {
      const data = Uint8Array.from(byteArray);
      await this.subject.sendAudio(this.name, data);
      return true;
    } catch (error) {
      console.error("❌ Error enviando audio:", error);
      return false;
    }
  }

  // ============================================================
  // MENSAJES DE AUDIO
  // ============================================================
  async sendAudioMessage(targetUser, byteArray) {
    if (!this.subject) {
      console.error("❌ No hay subject");
      return false;
    }
    try {
      const data = Uint8Array.from(byteArray);
      console.log(`[ICE] 📨 Enviando mensaje de audio a: ${targetUser}, bytes: ${data.length}`);
      await this.subject.sendAudioMessage(this.name, targetUser, data);
      return true;
    } catch (error) {
      console.error("❌ Error enviando mensaje de audio:", error);
      return false;
    }
  }

  // ============================================================
  // GRUPOS: INICIAR / UNIR / SALIR
  // ============================================================
  async createGroupCall(users) {
    if (!this.subject) {
        console.error("❌ No hay subject");
        return false;
    }

    try {
        // Filtrar al iniciador de la lista de invitados
        const otherUsers = users.filter(u => u !== this.name);

        console.log(`[ICE] 📢 Creando llamada grupal con usuarios:`, otherUsers);

        // Crear la llamada en el servidor
        const groupId = await this.subject.createGroupCall(this.name, otherUsers);

        console.log(`[ICE] ✅ Llamada grupal creada con ID: ${groupId}`);

        // El iniciador se une automáticamente
        await this.joinGroupCall(groupId);

        return groupId;
    } catch (error) {
        console.error("❌ Error creando llamada grupal:", error);
        return false;
    }
}


  async joinGroupCall(groupId) {
    if (!this.subject) return false;

    try {
        console.log(`[ICE] 👥 Uniéndose al grupo: ${groupId}`);

        // Unirse al grupo de llamada
        await this.subject.joinGroupCall(groupId, this.name);

        // Actualizar UI a "en llamada"
        if (this.name === this.subject.initiator) {
            // Iniciador
            UI.showInCallScreen(groupId, this.name); // Pantalla de llamada en curso
        } else {
            // Invitado que acepta
            UI.hideIncomingCallModal(groupId);        // Ocultar modal de aceptar/rechazar
            UI.showInCallScreen(groupId, this.name);
        }

        return true;
    } catch (error) {
        console.error("❌ Error uniéndose a llamada grupal:", error);
        return false;
    }
}

  async leaveGroupCall(groupId) {
    if (!this.subject) return false;
    try {
      console.log(`[ICE] 👋 Saliendo del grupo: ${groupId}`);
      await this.subject.leaveGroupCall(groupId, this.name);
      return true;
    } catch (error) {
      console.error("❌ Error saliendo de llamada grupal:", error);
      return false;
    }
  }

  // ============================================================
  // AUDIO GRUPAL
  // ============================================================
  async sendAudioGroup(groupId, byteArray) {
    if (!this.subject) {
      console.error("❌ No hay subject");
      return false;
    }
    try {
      const data = Uint8Array.from(byteArray);
      await this.subject.sendAudioGroup(groupId, this.name, data);
      return true;
    } catch (error) {
      console.error("❌ Error enviando audio grupal:", error);
      return false;
    }
  }

  async sendAudioMessageGroup(groupId, byteArray) {
    if (!this.subject) return false;
    try {
      const data = Uint8Array.from(byteArray);
      await this.subject.sendAudioMessageGroup(this.name, groupId, data);
      return true;
    } catch (e) {
      console.error("Error sendAudioMessageGroup:", e);
      return false;
    }
  }

  
  async joinMessagingGroup(groupId, members) {
    if (!this.subject) return false;
    try {
      await this.subject.joinMessagingGroup(groupId, members);
      return true;
    } catch (e) {
      console.error("Error joinMessagingGroup:", e);
      return false;
    }
  }
  // ============================================================
  // SUBSCRIPCIÓN DE AUDIO
  // ============================================================
   onGroupUsersUpdated(cb) { this.groupUsersUpdatedCB = cb; }

  notifyGroupUsersUpdated(groupId, members) {
    this.groupUsersUpdatedCB?.(groupId, members);
}

  subscribe(cb) {
    this.callbacks.push(cb);
  }

  notify(bytes) {
    this.callbacks.forEach((cb) => cb(bytes));
  }

  subscribeGroup(groupId, cb) {
    if (!this.groupCallbacks.has(groupId))
      this.groupCallbacks.set(groupId, []);
    this.groupCallbacks.get(groupId).push(cb);
  }

  notifyGroupMessage(groupId, bytes) {
    if (this.groupCallbacks.has(groupId)) {
      this.groupCallbacks.get(groupId).forEach((cb) => cb(bytes));
    }

    if (window.AudioManager?.playAudio) {
      window.AudioManager.playAudio(bytes);
    }
  }


  // ============================================================
  // CALLBACKS DE LLAMADAS
  // ============================================================
  onIncomingCall(cb) { this.incomingCallCB = cb; }
  onCallAccepted(cb) { this.callAcceptedCB = cb; }
  onCallRejected(cb) { this.callRejectedCB = cb; }
  onCallEnded(cb) { this.callColgadaCB = cb; }

  notifyIncomingCall(fromUser) {
    if (this.incomingCallCB) this.incomingCallCB(fromUser);
  }

  notifyCallAccepted(fromUser) {
    if (this.callAcceptedCB) this.callAcceptedCB(fromUser);
  }

  notifyCallRejected(fromUser) {
    if (this.callRejectedCB) this.callRejectedCB(fromUser);
  }

  notifyCallColgada(fromUser) {
    if (this.currentCall === fromUser) this.currentCall = null;
    if (this.callColgadaCB) this.callColgadaCB(fromUser);
  }

  // ============================================================
  // CALLBACKS GRUPALES
  // ============================================================
  onIncomingGroupCall(cb) { this.incomingGroupCallCB = cb; }
  onGroupUpdated(cb) { this.groupUpdatedCB = cb; }
  onGroupEnded(cb) { this.groupEndedCB = cb; }

  notifyIncomingGroupCall(groupId, fromUser, members) {
    if (this.incomingGroupCallCB)
      this.incomingGroupCallCB(groupId, fromUser, members);
  }

  notifyGroupCallUpdated(groupId, members) {
    if (this.groupUpdatedCB)
      this.groupUpdatedCB(groupId, members);
  }

  notifyGroupCallEnded(groupId) {
    if (this.groupEndedCB)
      this.groupEndedCB(groupId);
  }
}

// Crear instancia global
if (typeof window !== "undefined") {
  console.log("✅ Creando instancia global de IceDelegate");
  window.IceDelegate = new IceDelegate();
} else {
  console.error("❌ Window no está definido");
}
