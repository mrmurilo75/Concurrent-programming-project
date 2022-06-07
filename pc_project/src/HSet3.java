import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

public class HSet3<E> implements IHSet<E>{

    private LinkedList<E>[] table;
    private final int rwl_size;

    private final ReentrantReadWriteLock[] rwl;
    private final WriteLock[] wl;
    private final ReadLock[] rl;
    private final Condition[] wait_for_elem;
//    private final ReadLock r = rwl.readLock();
//    private final WriteLock w = rwl.writeLock();
//    private final Condition wait_for_elem = w.newCondition();

    /**
     * Constructor.
     * @param ht_size Initial size for internal hash table.
     */
    public HSet3(int ht_size) {
        table = createTable(ht_size);

        rwl_size = ht_size;
        rwl = new ReentrantReadWriteLock[ht_size];
        wl = new WriteLock[ht_size];
        rl = new ReadLock[ht_size];
        wait_for_elem = new Condition[ht_size];
        for (int i = 0; i < ht_size; i++) {
            rwl[i] = new ReentrantReadWriteLock();
            wl[i] = rwl[i].writeLock();
            rl[i] = rwl[i].readLock();
            wait_for_elem[i] = wl[i].newCondition();
        }
    }

    // Auxiliary method to return the list where
    // an element should be stored.
    private LinkedList<E> getEntry(E elem) {
        return table[Math.abs(elem.hashCode() % table.length)];
    }

    private WriteLock getWriteLock(E elem) {
        return wl[Math.abs(elem.hashCode() % rwl_size)];
    }

    private ReadLock getReadLock(E elem) {
        return rl[Math.abs(elem.hashCode() % rwl_size)];
    }

    private Condition getCondition(E elem) {
        return wait_for_elem[Math.abs(elem.hashCode() % rwl_size)];
    }

    private void writeLockAll() {
        for (int i = 0; i < rwl_size; i++) {
            wl[i].lock();
        }
    }
    private void writeUnlockAll() {
        for (int i = 0; i < rwl_size; i++) {
            wl[i].unlock();
        }
    }

    private void readLockAll() {
        for (int i = 0; i < rwl_size; i++) {
            rl[i].lock();
        }
    }
    private void readUnlockAll() {
        for (int i = 0; i < rwl_size; i++) {
            rl[i].unlock();
        }
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
        return table.length;
    }

    @Override
    public int size() {
        readLockAll();
        try {
            int size = 0;
            for(LinkedList<E> t : table) {
                size += t.size();
            }
            return size;
        } finally {
            readUnlockAll();
        }
    }

    @Override
    public boolean add(E elem) {
        if (elem == null) {
            throw new IllegalArgumentException();
        }

        WriteLock w = getWriteLock(elem);
        Condition wait_for_elem = getCondition(elem);

        w.lock();
        try {
            LinkedList<E> list = getEntry(elem);
            boolean r = ! list.contains(elem);
            if (r) {
                list.addFirst(elem);
                wait_for_elem.signalAll(); // there may threads waiting in waitElem
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

        WriteLock w = getWriteLock(elem);

        w.lock();
        try {
            return getEntry(elem).remove(elem);
        } finally {
            w.unlock();
        }
    }

    @Override
    public boolean contains(E elem) {
        if (elem == null) {
            throw new IllegalArgumentException();
        }

        ReadLock r = getReadLock(elem);

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

        WriteLock w = getWriteLock(elem);
        Condition wait_for_elem = getCondition(elem);
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
        writeLockAll();
        try {
            LinkedList<E>[] oldTable = table;
            table = createTable(2 * oldTable.length);
            for (LinkedList<E> list : oldTable) {
                for (E elem : list ) {
                    getEntry(elem).add(elem);
                }
            }
        } finally {
            writeUnlockAll();
        }
    }

}
