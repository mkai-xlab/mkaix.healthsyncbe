package com.g93.be.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class KnowledgeDocumentOperationCoordinator {

    private static final int LOCK_COUNT = 256;

    private final ReentrantLock[] locks = createLocks();

    public <T> T executeExclusively(Long documentId, Supplier<T> operation) {
        ReentrantLock lock = locks[Math.floorMod(Long.hashCode(documentId), LOCK_COUNT)];
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock[] createLocks() {
        ReentrantLock[] result = new ReentrantLock[LOCK_COUNT];
        for (int index = 0; index < result.length; index++) {
            result[index] = new ReentrantLock();
        }
        return result;
    }
}
