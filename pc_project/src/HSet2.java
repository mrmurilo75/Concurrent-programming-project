import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

public class HSet2<E> implements IHSet<E>{

    private LinkedList<E>[] table;
    private int size;

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final ReadLock r = rwl.readLock();
    private final WriteLock w = rwl.writeLock();
    private final Condition wait_for_elem = w.newCondition();

    /**
     * Constructor.
     * @param ht_size Initial size for internal hash table.
     */
    public HSet2(int ht_size) {
        table = createTable(ht_size);
        size = 0;
    }

    // Auxiliary method to return the list where
    // an element should be stored.
    private LinkedList<E> getEntry(E elem) {
        return table[Math.abs(elem.hashCode() % table.length)];
    }

    // Auxiliary method to create the hash table.
    private LinkedList<E>[] createTable(int ht_size) {
        @SuppressWarnings("unchecked")
        LinkedList<E>[] t = (LinkedList<E>[]) new LinkedList[ht_size];
        for (int i = 0; i < t.length; i++) {
            t[i] = new LinkedList<>();
        }
        return t;
    }

    @Override
    public int capacity() {
//        r.lock();
//        try {
//            return table.length;
//        } finally {
//            r.unlock();
//        }
        return table.length;
    }

    @Override
    public int size() {
        r.lock();
        try {
            return size;
        } finally {
            r.unlock();
        }
    }

    @Override
    public boolean add(E elem) {
        if (elem == null) {
            throw new IllegalArgumentException();
        }
        w.lock();
        try {
            LinkedList<E> list = getEntry(elem);
            boolean r = ! list.contains(elem);
            if (r) {
                list.addFirst(elem);
                wait_for_elem.signalAll(); // there may threads waiting in waitElem
                size++;
            }
            return r;
        } finally {
            w.unlock();
        }
    }

    @Override
    public boolean remove(E elem) {
        if (elem == null) {
            throw new IllegalArgumentException();
        }
        w.lock();
        try {
            boolean r = getEntry(elem).remove(elem);
            if (r) {
                size--;
            }
            return r;
        } finally {
            w.unlock();
        }
    }

    @Override
    public boolean contains(E elem) {
        if (elem == null) {
            throw new IllegalArgumentException();
        }
        r.lock();
        try {
            return getEntry(elem).contains(elem);
        } finally {
            r.unlock();
        }
    }

    @Override
    public void waitFor(E elem) {
        if (elem == null) {
            throw new IllegalArgumentException();
        }
        w.lock();
        try {
            while (! getEntry(elem).contains(elem)) {
                try {
                    wait_for_elem.await();
                }
                catch(InterruptedException e) {
                    // Ignore interrupts
                }
            }
        } finally {
            w.unlock();
        }
    }

    @Override
    public void rehash() {
        w.lock();
        try {
            LinkedList<E>[] oldTable = table;
            table = createTable(2 * oldTable.length);
            for (LinkedList<E> list : oldTable) {
                for (E elem : list ) {
                    getEntry(elem).add(elem);
                }
            }
        } finally {
            w.unlock();
        }
    }
}

