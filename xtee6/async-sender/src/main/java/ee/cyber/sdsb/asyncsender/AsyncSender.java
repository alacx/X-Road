package ee.cyber.sdsb.asyncsender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.asyncdb.AsyncDB;
import ee.cyber.sdsb.asyncdb.AsyncSenderConf;
import ee.cyber.sdsb.asyncdb.messagequeue.MessageQueue;
import ee.cyber.sdsb.asyncdb.messagequeue.QueueInfo;
import ee.cyber.sdsb.common.identifier.ClientId;

@Slf4j
class AsyncSender {

    private static final int WORKER_UPDATE_INTERVAL = 5000; // ms

    private ExecutorService executor;

    private Map<ClientId, MessageQueueWorker> activeWorkers = new HashMap<>();

    private boolean shouldQuitWhenAllWorkersDone = false;

    void startUp() throws Exception {
        startUp(false);
    }

    void startUp(boolean quitWhenAllWorkersDone) throws Exception {
        this.shouldQuitWhenAllWorkersDone = quitWhenAllWorkersDone;
        this.executor = createExecutor();

        log.trace("Starting Async-Sender...");

        ProxyClient client = ProxyClient.getInstance();
        client.start();
        try {
            runMainLoop();
        } finally {
            client.stop();
            executor.shutdown();
        }
    }

    void runMainLoop() {
        while (true) {
            activeWorkers = updateWorkers();

            if (activeWorkers.isEmpty() && shouldQuitWhenAllWorkersDone) {
                break;
            }

            try {
                Thread.sleep(WORKER_UPDATE_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    Map<ClientId, MessageQueueWorker> updateWorkers() {
        Map<ClientId, MessageQueueWorker> workersToKeep = new HashMap<>();

        // Create workers for new queues
        List<MessageQueue> queues = null;
        try {
            queues = getMessageQueues();
        } catch (Exception e) {
            log.error("Failed to get message queues from AsyncDB", e);
            return workersToKeep;
        }

        log.trace("Got {} message queues from AsyncDB", queues.size());
        for (MessageQueue queue : queues) {
            QueueInfo info = null;
            try {
                info = queue.getQueueInfo();
            } catch (Exception e) {
                log.error("Failed to get queue info", e);
                continue;
            }

            final ClientId queueName = info.getName();
            if (!activeWorkers.containsKey(queueName)) {
                activeWorkers.put(queueName, startWorker(info, queue));
            }
        }

        // Retain active workers
        for (Entry<ClientId, MessageQueueWorker> entry
                : activeWorkers.entrySet()) {
            if (entry.getValue().isRunning()) {
                workersToKeep.put(entry.getKey(), entry.getValue());
            } else {
                log.trace("Sender for queue '{}' has finished!", entry.getKey());
            }
        }

        return workersToKeep;
    }

    MessageQueueWorker startWorker(QueueInfo info, MessageQueue queue) {
        final ClientId queueName = info.getName();
        log.trace("Starting sender for queue '{}'...", queueName);

        MessageQueueWorker worker = new MessageQueueWorker(queue);

        executor.execute(worker);

        return worker;
    }

    List<MessageQueue> getMessageQueues() throws Exception {
        return AsyncDB.getMessageQueues();
    }

    private static ExecutorService createExecutor() throws Exception {
        AsyncSenderConf conf = new AsyncSenderConf();

        log.trace("Creating ExecutorService with max {} senders...",
                conf.getMaxSenders());
        return Executors.newFixedThreadPool(conf.getMaxSenders());
    }
}