package dev.aibuilder.world;
import java.util.*;

/** Pure history cursor; world snapshots remain owned by the server adapter. */
public final class TransactionHistory<C,T> {
    private C context;private List<T> undo=List.of(),redo=List.of();
    public void record(C context,List<T> changes){this.context=context;undo=List.copyOf(changes);redo=List.of();}
    public List<T> get(C context,boolean redoing){if(!Objects.equals(context,this.context))throw new IllegalArgumentException("История относится к другому миру или игроку.");List<T> entries=redoing?redo:undo;if(entries.isEmpty())throw new IllegalArgumentException("Нет доступной операции истории.");return entries;}
    public void completed(boolean redoing){if(redoing){if(redo.isEmpty())throw new IllegalStateException("Нет redo.");undo=redo;redo=List.of();}else{if(undo.isEmpty())throw new IllegalStateException("Нет undo.");redo=undo;undo=List.of();}}
    public void clear(){context=null;undo=List.of();redo=List.of();}
}
