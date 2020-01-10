package bp.algorithms;

import bp.MRF.MRF;
import bp.MRF.Message;
import bp.MRF.Utils;
import bp.algorithms.queues.*;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by vaksenov on 01.08.2019.
 */
public class SmartSplashBP extends BPAlgorithm {
    int splashH;
    int threads;
    boolean fair;
    boolean relaxed;
    double sensitivity;

    public class Vertex extends IdentifiedClass {
        int v;

        public Vertex(int v) {
            this.v = v;
            this.id = v;
        }

        public String toString() {
            return "" + v;
        }
    }

    public SmartSplashBP(MRF mrf, int splashH, int threads, boolean fair, boolean relaxed, double sensitivity) {
        super(mrf);
        this.splashH = splashH;
        this.threads = threads;
        this.fair = fair;
        this.relaxed = relaxed;
        this.sensitivity = sensitivity;
    }

    public double getPriority(Vertex v) {
        double priority = 0;
        for (Message in : mrf.getMessagesTo(v.v)) {
            priority = Math.max(priority, Utils.distance(in.logMu, mrf.getFutureMessage(in)));
        }
        return priority;
    }

    public void updateMessage(ReentrantLock[] locks, Message m) {
        int mi = Math.min(m.i, m.j);
        int mj = Math.max(m.i, m.j);
        if (fair) {
            locks[mi].lock();
            locks[mj].lock();
        }
        mrf.updateMessage(m);
        if (fair) {
            locks[mj].unlock();
            locks[mi].unlock();
        }
    }

    public double[][] solve() {
        ReentrantLock[] locks = new ReentrantLock[mrf.getNodes()];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }

        final PQ<Vertex> pq = relaxed ?
                new MultiPQ<>(mrf.getNodes(), 4 * threads) :
                new ConcurrentPQ<>(mrf.getNodes());
        Vertex[] vertices = new Vertex[mrf.getNodes()];
        for (int i = 0; i < mrf.getNodes(); i++) {
            Vertex v = new Vertex(i);
            vertices[i] = v;
            pq.insert(v, getPriority(v));
        }

        AtomicInteger updates = new AtomicInteger();
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                int it = 0;
                int updatesLocal = 0;
                int[] visited = new int[mrf.getNodes()];
                int[] distance = new int[mrf.getNodes()];
                ArrayList<Message> order = new ArrayList<>(mrf.getNodes());
                Queue<Integer> queue = new ArrayDeque<>(mrf.getNodes());
                ArrayList<Integer> affected = new ArrayList<>();
                while (true) {
                    if (++it % 1000 == 0) {
                        if (pq.peek().priority < sensitivity) {
                            updates.addAndGet(updatesLocal);
                            return;
                        }
                    }

                    Vertex v = pq.extractMin();
                    order.clear();
                    queue.clear();
                    affected.clear();
                    visited[v.v] = it;
                    distance[v.v] = 0;
                    queue.add(v.v);
                    while (!queue.isEmpty()) {
                        int u = queue.poll();
                        if (visited[u] == it && distance[u] >= splashH + 1) {
                            break;
                        }
                        for (Message m : mrf.getMessagesFrom(u)) {
                            if (visited[m.j] == it) {
                                continue;
                            }
                            visited[m.j] = it;
                            distance[m.j] = distance[u] + 1;
                            queue.add(m.j);
                            if (distance[m.j] <= splashH) {
                                order.add(m);
                            }
                            affected.add(m.j);
                        }
                    }

                    for (int j = 0; j < order.size(); j++) {
                        Message m = order.get(order.size() - j - 1).reverse;
                        updateMessage(locks, m);
                        updatesLocal++;
                    }

                    for (Message m : order) {
                        updateMessage(locks, m);
                        updatesLocal++;
                    }

                    for (int u : affected) {
                        if (u == v.v) {
                            continue;
                        }
                        if (fair) {
                            locks[u].lock();
                        }
                        pq.changePriority(vertices[u], getPriority(vertices[u]), 1e-7);
                        if (fair) {
                            locks[u].unlock();
                        }
                    }
                    if (fair) {
                        locks[v.v].lock();
                    }
                    pq.insert(v, getPriority(v));
                    if (fair) {
                        locks[v.v].unlock();
                    }
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
            }
        }

        System.out.println(String.format("Updates: %d", updates.get()));

        return mrf.getNodeProbabilities();
    }
}
