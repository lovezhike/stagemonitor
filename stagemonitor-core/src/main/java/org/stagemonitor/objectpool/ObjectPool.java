package org.stagemonitor.objectpool;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class ObjectPool implements Closeable {

	private final int numPooledObjectsPerThread;

	private final Map<Class<? extends Recyclable>, RecyclableObjectFactory<?>> registeredObjectFactories = new ConcurrentHashMap<Class<? extends Recyclable>, RecyclableObjectFactory<?>>();

	private final ThreadLocal<Map<Class<? extends Recyclable>, Queue<Recyclable>>> objectPools = new ThreadLocal<Map<Class<? extends Recyclable>, Queue<Recyclable>>>() {
		@Override
		protected Map<Class<? extends Recyclable>, Queue<Recyclable>> initialValue() {
			return new HashMap<Class<? extends Recyclable>, Queue<Recyclable>>();
		}
	};

	public ObjectPool(int numPooledObjectsPerThread) {
		this.numPooledObjectsPerThread = numPooledObjectsPerThread;
	}

	public <T extends Recyclable> void registerRecyclableObjectFactory(RecyclableObjectFactory<T> recyclableObjectFactory, Class<T> type) {
		registeredObjectFactories.put(type, recyclableObjectFactory);
	}

	public <T extends Recyclable> T createInstance(Class<T> type) {
		final Queue<T> objectPool = getObjectPool(type);
		T obj = objectPool.poll();
		if (obj != null) {
			return obj;
		}
		obj = (T) registeredObjectFactories.get(type).createInstance();
		objectPool.offer(obj);
		return obj;
	}

	public <T extends Recyclable> void recycle(T obj, Class<? extends Recyclable> type) {
		obj.resetState();
		this.objectPools.get().get(type).offer(obj);
	}

	private <T extends Recyclable> Queue<T> getObjectPool(Class<? extends Recyclable> type) {
		Queue<Recyclable> objectPool = this.objectPools.get().get(type);
		if (objectPool == null) {
			objectPool = new ArrayDeque<Recyclable>(numPooledObjectsPerThread);
			this.objectPools.get().put(type, objectPool);
		}
		return (Queue<T>) objectPool;
	}

	@Override
	public void close() {
		objectPools.remove();
	}
}
