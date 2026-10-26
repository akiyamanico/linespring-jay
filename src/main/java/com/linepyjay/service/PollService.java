package com.linepyjay.service;

import com.linepyjay.model.LineOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Long-polling loop — maps oepoll.py and the while-loop in main.py.
 * Runs in a dedicated thread so it doesn't block Spring's startup.
 */
@Service
public class PollService {

    private static final Logger log = LoggerFactory.getLogger(PollService.class);

    private static final int[] HANDLED_OP_TYPES = {25, 50, 26}; // RECEIVE_MESSAGE, etc.

    private final LineClientService    lineClient;
    private final MessageHandlerService messageHandler;

    public PollService(LineClientService lineClient, MessageHandlerService messageHandler) {
        this.lineClient     = lineClient;
        this.messageHandler = messageHandler;
    }

    /**
     * Starts the polling loop in a dedicated daemon thread.
     * Maps: main.py :: main()
     */
    public void startPolling() {
        Thread pollThread = new Thread(this::loop, "line-poll-thread");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void loop() {
        while (true) {
            try {
                List<LineOperation> ops = lineClient.fetchOps(50);
                for (LineOperation op : ops) {
                    if (isHandledType(op.getType())) {
                        messageHandler.handle(op);
                    }
                    lineClient.setRevision(op.getRevision());
                }
                if (ops.isEmpty()) {
                    Thread.sleep(500); // avoid busy-spinning when no ops
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.info("Poll loop interrupted, shutting down.");
                break;
            } catch (Exception e) {
                log.warn("Poll error: {} — retrying in 5s", e.getMessage());
                try { Thread.sleep(5_000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private boolean isHandledType(int type) {
        for (int t : HANDLED_OP_TYPES) {
            if (t == type) return true;
        }
        return false;
    }
}
