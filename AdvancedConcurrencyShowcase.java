
package com.linkedin.advancedconcurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================
 * Advanced Concurrency Showcase - Govinda Kale
 * ============================================================
 * Topics Covered:
 * 1. Deadlock-Safe Bank Transfer System
 * 2. Custom Thread Pool Implementation
 * 3. CompletableFuture Async Processing Pipeline
 * 4. Structured Logging (Production Style)
 * ============================================================
 */

public class AdvancedConcurrencyShowcase {

    private static final Logger logger = Logger.getLogger(AdvancedConcurrencyShowcase.class.getName());

    // ============================================================
    // REGION 1: Deadlock-Safe Bank Transfer System
    // ============================================================

    static class BankAccount {
        private final int id;
        private double balance;
        private final ReentrantLock lock = new ReentrantLock();

        public BankAccount(int id, double balance) {
            this.id = id;
            this.balance = balance;
        }

        public int getId() { return id; }

        public double getBalance() { return balance; }

        public void deposit(double amount) {
            balance += amount;
        }

        public void withdraw(double amount) {
            balance -= amount;
        }

        public ReentrantLock getLock() { return lock; }
    }

    static class BankService {

        public static void transfer(BankAccount from, BankAccount to, double amount) {
            BankAccount first = from.getId() < to.getId() ? from : to;
            BankAccount second = from.getId() < to.getId() ? to : from;

            first.getLock().lock();
            second.getLock().lock();

            try {
                if (from.getBalance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                    logger.info("Transferred " + amount + " from A" + from.getId() + " to A" + to.getId());
                } else {
                    logger.warning("Insufficient balance in account " + from.getId());
                }
            } finally {
                second.getLock().unlock();
                first.getLock().unlock();
            }
        }
    }

    // ============================================================
    // REGION 2: Custom Thread Pool
    // ============================================================

    static class CustomThreadPool {

        private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
        private final List<Worker> workers = new ArrayList<>();
        private volatile boolean isRunning = true;

        public CustomThreadPool(int poolSize) {
            for (int i = 0; i < poolSize; i++) {
                Worker worker = new Worker();
                workers.add(worker);
                worker.start();
            }
        }

        public void submit(Runnable task) {
            if (isRunning) {
                taskQueue.offer(task);
            }
        }

        public void shutdown() {
            isRunning = false;
            workers.forEach(Thread::interrupt);
        }

        private class Worker extends Thread {
            @Override
            public void run() {
                while (isRunning || !taskQueue.isEmpty()) {
                    try {
                        Runnable task = taskQueue.poll(1, TimeUnit.SECONDS);
                        if (task != null) {
                            task.run();
                        }
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    // ============================================================
    // REGION 3: CompletableFuture Async Pipeline
    // ============================================================

    static class AsyncService {

        static CompletableFuture<String> fetchUser() {
            return CompletableFuture.supplyAsync(() -> {
                sleep(500);
                return "UserData";
            });
        }

        static CompletableFuture<String> fetchOrders() {
            return CompletableFuture.supplyAsync(() -> {
                sleep(700);
                return "OrderData";
            });
        }

        static CompletableFuture<String> combine(String user, String orders) {
            return CompletableFuture.supplyAsync(() ->
                    "Combined Result -> " + user + " + " + orders
            );
        }

        private static void sleep(int ms) {
            try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
        }
    }

    // ============================================================
    // MAIN METHOD
    // ============================================================

    public static void main(String[] args) throws Exception {

        logger.info("===== BANK TRANSFER DEMO =====");
        BankAccount acc1 = new BankAccount(1, 1000);
        BankAccount acc2 = new BankAccount(2, 1000);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> BankService.transfer(acc1, acc2, 200));
        executor.submit(() -> BankService.transfer(acc2, acc1, 300));
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        logger.info("Final Balance A1: " + acc1.getBalance());
        logger.info("Final Balance A2: " + acc2.getBalance());

        logger.info("===== CUSTOM THREAD POOL DEMO =====");
        CustomThreadPool pool = new CustomThreadPool(2);
        AtomicInteger counter = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            pool.submit(() -> {
                counter.incrementAndGet();
                logger.info("Task executed by " + Thread.currentThread().getName());
            });
        }

        Thread.sleep(2000);
        pool.shutdown();
        logger.info("Total Tasks Executed: " + counter.get());

        logger.info("===== COMPLETABLE FUTURE PIPELINE =====");
        CompletableFuture<String> result =
                AsyncService.fetchUser()
                        .thenCombine(AsyncService.fetchOrders(), AsyncService::combine)
                        .thenCompose(f -> f);

        logger.info(result.get());

        logger.info("===== EXECUTION COMPLETE =====");
    }
}
