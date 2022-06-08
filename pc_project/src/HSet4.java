import scala.concurrent.stm.Ref;
import scala.concurrent.stm.TArray;
import scala.concurrent.stm.japi.STM;

public class HSet4<E> implements IHSet<E>{

  private static class Node<T> {
    T value;
    Ref.View<Node<T>> prev = STM.newRef(null);
    Ref.View<Node<T>> next = STM.newRef(null);

    Node(T value, Node<T> prev, Node<T> next)
    {
      this.value = value;
      this.prev = STM.newRef(prev);
      this.next = STM.newRef(next);
    }
  }

  private final Ref.View<TArray.View<Node<E>>> table;
  private final Ref.View<Integer> size;

  public HSet4(int h_size) {
    table = STM.newRef(STM.newTArray(h_size));
    size = STM.newRef(0);

    set_sentinels();
  }

  @Override
  public int capacity() {
    return table.get().length();
  }

  @Override
  public int size() {
    return size.get();
  }

  @Override
  public boolean add(E elem) {
    if (elem == null) {
      throw new IllegalArgumentException();
    }

    return STM.atomic(() -> {
        if (contains(elem))
          return false;

        add_no_check(elem);
        size.set(size.get() + 1);

        return true;
      });
  }

  @Override
  public boolean remove(E elem) {
    if (elem == null) {
      throw new IllegalArgumentException();
    }

    return STM.atomic(() -> {
        Node<E> curr = get(elem).next.get();

        while (curr.value != null)
        {
          if (curr.value.equals(elem))
          {
            curr.prev.get().next.set(curr.next.get());
            curr.next.get().prev.set(curr.prev.get());
            size.set(size.get() - 1);

            return true;
          }

          curr = curr.next.get();
        }

        return false;
      });
  }

  @Override
  public boolean contains(E elem) {
    if (elem == null) {
      throw new IllegalArgumentException();
    }

    return STM.atomic(() -> {
        Node<E> curr = get(elem).next.get();

        while (curr.value != null)
        {
          if (curr.value.equals(elem))
            return true;

          curr = curr.next.get();
        }

        return false;
      });
  }

  @Override
  public void waitFor(E elem) {
    if (elem == null) {
      throw new IllegalArgumentException();
    }

    while (!contains(elem))
      ;
  }

  @Override
  public void rehash() {
    STM.atomic(() -> {
        TArray.View<Node<E>> oldtable = table.get();
        int length = oldtable.length();
        TArray.View<Node<E>> newtable = STM.newTArray(2 * length);

        table.set(newtable);
        set_sentinels();

        for (int i = 0; i < length; i++)
        {
          Node<E> node = oldtable.apply(i).next.get();

          while (node.value != null)
          {
            add_no_check(node.value);
            node = node.next.get();
          }
        }
      });
  }

  private Node<E> get(E elem)
  {
    return table.get().apply(
      Math.abs(elem.hashCode()) % table.get().length()
      );
  }

  private void add_no_check(E elem)
  {
    Node<E> prev = get(elem);
    Node<E> next = prev.next.get();
    Node<E> newnode = new Node<E>(elem, prev, next);
    prev.next.set(newnode);
    next.prev.set(newnode);
  }

  private void set_sentinels()
  {
    for (int i = 0; i < table.get().length(); i++)
    {
      Node<E> first = new Node<E>(null, null, null);
      Node<E> last = new Node<E>(null, first, null);
      first.next.set(last);
      table.get().update(i, first);
    }
  }
}
