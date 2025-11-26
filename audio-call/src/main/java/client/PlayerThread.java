package client;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sound.sampled.*;

public class PlayerThread extends Thread {

    private final Queue<byte[]> audioBytes = new ConcurrentLinkedQueue<>();
    private volatile boolean isPlay;
    private final SourceDataLine speaker;
    private volatile boolean running = true;

    public PlayerThread(AudioFormat format) throws Exception {
        DataLine.Info infoSpeaker = new DataLine.Info(SourceDataLine.class, format);
        speaker = (SourceDataLine) AudioSystem.getLine(infoSpeaker);
        speaker.open(format);
        speaker.start();
        System.out.println("[PLAYER] Inicializado correctamente");
    }

    public void setPlay(boolean isPlay) {
        this.isPlay = isPlay;
        System.out.println("[PLAYER] setPlay: " + isPlay);
    }

    public void play(byte[] batch) {
        if (batch != null && batch.length > 0) {
            audioBytes.add(batch);
            System.out.println("[PLAYER] Audio agregado a cola: " + batch.length + " bytes, cola size: " + audioBytes.size());
        }
    }

    @Override
    public void run() {
        System.out.println("[PLAYER] Thread iniciado");
        
        while (running) {
            try {
                // Esto permite reproducir mensajes de audio incluso fuera de llamadas
                if (!audioBytes.isEmpty()) {
                    byte[] current = audioBytes.poll();
                    if (current != null) {
                        speaker.write(current, 0, current.length);
                    }
                } else {
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                System.out.println("[PLAYER] Thread interrumpido");
                running = false;
                break;
            } catch (Exception e) {
                System.err.println("[PLAYER] Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        cleanup();
        System.out.println("[PLAYER] Thread finalizado");
    }

    private void cleanup() {
        try {
            if (speaker != null && speaker.isOpen()) {
                speaker.drain();
                speaker.stop();
                speaker.close();
                System.out.println("[PLAYER] Speaker cerrado");
            }
        } catch (Exception e) {
            System.err.println("[PLAYER] Error cerrando speaker: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        this.interrupt();
    }
}
