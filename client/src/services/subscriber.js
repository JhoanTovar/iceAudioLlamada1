
// Primero definir Subscriber
class Subscriber extends Demo.Observer {
  constructor(delegate) {
    super();
    this.delegate = delegate;
  }

  // =================== AUDIO ===================
  receiveAudio(bytes) {
    console.log("[WEB] Audio recibido:", bytes.length);
    this.delegate.notify(Uint8Array.from(bytes));
  }

  receiveAudioMessage(bytes) {
  console.log(`🎯 [DEBUG] 🔥 RECEIVE_AUDIO_MESSAGE_GROUP EJECUTADO`);
  console.log(`🎯 [DEBUG] Grupo: ${groupId}, Bytes: ${bytes.length}`);
  console.log(`🎯 [DEBUG] Delegate disponible: ${!!this.delegate}`);
  console.log(`🎯 [DEBUG] notifyGroupMessage disponible: ${!!this.delegate.notifyGroupMessage}`);
    console.log("[WEB] Mensaje de audio recibido:", bytes.length);
    this.delegate.notify(Uint8Array.from(bytes));
  }

  receiveAudioMessageGroup(groupId, bytes) {
    console.log(`🎯 [RECEPTOR] 🔥🔥🔥 RECEIVE_AUDIO_MESSAGE_GROUP EJECUTADO EN RECEPTOR`);
    console.log(`🎯 [RECEPTOR] Grupo: ${groupId}, Bytes: ${bytes.length}`);
    console.log(`🎯 [RECEPTOR] Delegate disponible: ${!!this.delegate}`);
    console.log(`🎯 [RECEPTOR] notifyGroupMessage disponible: ${!!this.delegate.notifyGroupMessage}`);
    this.delegate.notifyGroupMessage(groupId, Uint8Array.from(bytes));
  }

  // =================== LLAMADAS 1 a 1 ===================
  incomingCall(fromUser) {
    console.log("📞 incomingCall:", fromUser);
    this.delegate.notifyIncomingCall(fromUser);
  }

  callAccepted(fromUser) {
    console.log("✅ callAccepted:", fromUser);
    this.delegate.notifyCallAccepted(fromUser);
  }

  callRejected(fromUser) {
    console.log("❌ callRejected:", fromUser);
    this.delegate.notifyCallRejected(fromUser);
  }

  callColgada(fromUser) {
    console.log("📴 callColgada:", fromUser);
    this.delegate.notifyCallColgada(fromUser);
  }

  // =================== LLAMADAS GRUPALES ===================
  incomingGroupCall(groupId, fromUser, members) {
    console.log(`📢 incomingGroupCall (${groupId}) de ${fromUser}`);
    this.delegate.notifyIncomingGroupCall(groupId, fromUser, members);
  }

  groupCallUpdated(groupId, members) {
    console.log(`🔄 groupCallUpdated (${groupId})`);
    this.delegate.notifyGroupCallUpdated(groupId, members);
  }

  groupCallEnded(groupId) {
    console.log(`🛑 groupCallEnded (${groupId})`);
    this.delegate.notifyGroupCallEnded(groupId);
  }
}
export default Subscriber;