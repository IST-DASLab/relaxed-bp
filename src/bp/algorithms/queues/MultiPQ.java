package bp.algorithms.queues;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by vaksenov on 29.07.2019.
 */
public class MultiPQ<K extends IdentifiedClass> implements PQ<K> {

    public class Node<K> extends PriorityNode<K> {
        public int queue;

        public Node(K value, double priority, int queue) {
            super(value, priority);
            this.queue = queue;
        }

        public Node<K> copy() {
            return new Node<>(value, priority, queue);
        }

        public void copyFrom(PriorityNode<K> node) {
            super.copyFrom(node);
            this.queue = ((Node<K>) node).queue;
        }
    }

    Heap<K>[] queues;
    Node<K>[] nodes;
    ReentrantLock[] locks;

    public MultiPQ(int maxSize, int relax) {
        nodes = new Node[maxSize];
        queues = new Heap[relax];
        for (int i = 0; i < queues.length; i++) {
            queues[i] = new Heap<>(maxSize);
        }
        locks = new ReentrantLock[relax];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public void insert(K value, double priority) {
        int queue = ThreadLocalRandom.current().nextInt(queues.length);
        Node<K> node = nodes[value.id];
        if (node == null) {
            node = nodes[value.id] = new Node<>(value, priority, queue);
        } else {
            node.priority = priority;
        }
        locks[queue].lock();
        synchronized (node) {
            node.queue = queue;
            queues[node.queue].insert(node);
        }
        locks[node.queue].unlock();
    }

    public void changePriority(K value, double newPriority) {
        Node<K> node = nodes[value.id];
        while (true) {
            int queue = node.queue;
            if (queue == -1) {
                return;
            }
            locks[queue].lock();
            if (node.queue != queue) {
                locks[queue].unlock();
                continue;
            }
            synchronized (node) {
                queues[queue].changePriority(node, newPriority);
            }
            locks[queue].unlock();
            return;
        }
    }

    public void changePriority (K value, double newPriority, double sensitivity) {
        if (Math.abs(nodes[value.id].priority - newPriority) < sensitivity)
            return;
        changePriority(value, newPriority);
    }

    public K extractMin() {
        while (true) {
            int i = ThreadLocalRandom.current().nextInt(queues.length);
            int j = ThreadLocalRandom.current().nextInt(queues.length);
            Node<K> vi = (Node<K>) queues[i].realPeek();
            Node<K> vj = (Node<K>) queues[j].realPeek();
            if (vi == null && vj == null) {
                continue;
            }
            Node<K> toExtract = vi == null ? vj : (vj == null ? vi :
                    vi.compareTo(vj) < 0 ? vi : vj);
            int queue = toExtract.queue;
            if (queue == -1) {
                continue;
            }
            if (!locks[queue].tryLock()) {
                continue;
            }
            if (queues[queue].realPeek() != toExtract) {
                locks[queue].unlock();
                continue;
            }

            synchronized (toExtract) {
                queues[queue].extractMin();
                toExtract.queue = -1;
            }
            locks[queue].unlock();

            return toExtract.value;
        }
    }

    public PriorityNode<K> peek() {
        PriorityNode<K> peek = null;
        for (int i = 0; i < queues.length; i++) {
            Heap<K> queue = queues[i];
            PriorityNode<K> next = queue.peek();
            if (next == null) {
                continue;
            }
            next = next.copy();
            if (peek == null || peek.compareTo(next) > 0) {
                peek = next;
            }
        }
        return peek;
    }

    public boolean check() {
        boolean good = true;
        for (int i = 0; i < queues.length; i++) {
            locks[i].lock();
            good &= queues[i].check();
            PriorityNode<K> peek = queues[i].peek();
            if (peek != null) {
                good &= ((Node<K>)queues[i].peek).queue == i;
            }
            locks[i].unlock();
        }
        return good;
    }
}
