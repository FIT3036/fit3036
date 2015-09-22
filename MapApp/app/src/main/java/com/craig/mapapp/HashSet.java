package com.craig.mapapp;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
 
//from stackexchange,
//http://stackoverflow.com/a/21689050
//class is not relevant to assignment.

/**
 * Set of elements with get method.
 * @author Lukáš Závitkovský
 * @author Jarrad Whitaker
 */
public class HashSet<T> implements Iterable<T>, java.util.Set<T> {
   
    public HashSet(){
        map = new HashMap<T,T>();
    }
   
    public boolean add(T obj){
    	if (map.containsKey(obj)) {
    		return false;
    	} else {
    		map.put(obj, obj);
    		return true;
    	}
    }
    
    public T getOrAdd(T obj) {
    	if (this.contains(obj)) {
    		return this.get(obj);
    	} else {
    		this.add(obj);
    		return obj;
    	}
    }
   
    public boolean isEmpty(){
        return map.isEmpty();
    }
   
    public boolean remove(Object o){
        return (map.remove(o) != null);
    }
   
    public boolean contains(Object o){
        return map.containsKey(o);
    }
   
    public T get(T obj){
        return map.get(obj);
    }
   
    @Override
    public Iterator<T> iterator(){
        return new MyIterator();
    }
   
    private final Map<T, T> map;
   
    // Iterator implementation for MySet
    private class MyIterator implements Iterator<T>{
 
        public MyIterator(){
            it = map.entrySet().iterator();
        }
       
        @Override
        public boolean hasNext() {
            return it.hasNext();
        }
 
        @Override
        public T next() {
            return it.next().getValue();
        }
 
        @Override
        public void remove() {
            it.remove();
        }
       
        private Iterator<Entry<T,T>> it;
       
    }

	@Override
	public int size() {
		return map.size();
	}

	@SuppressWarnings("unchecked")
	public Object[] toArray() {
		// TODO Auto-generated method stub
		return map.values().toArray((T[]) new Object[map.size()]);
	}
	
	public <Tr> Tr[] toArray(Tr[] a) {
		return map.values().toArray(a);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o : c){
			if (! this.contains(o)){
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		boolean added = false;
		for (T o: c) {
			added |= this.add(o);
		}
		return added;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean removed = false;
		for (T item : this) {
			if (!c.contains(item)){
				removed |= this.remove(item);
			}
		}
		return removed;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean removed = false;
		
		for (Object otherItem : c) {
			removed |= this.remove(otherItem);
		}
		return removed;
	}

	@Override
	public void clear() {
		this.map.clear();
		
	}
   
};