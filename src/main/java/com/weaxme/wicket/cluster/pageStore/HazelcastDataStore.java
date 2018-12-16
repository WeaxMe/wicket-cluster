package com.weaxme.wicket.cluster.pageStore;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.wicket.pageStore.IDataStore;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


public class HazelcastDataStore implements IDataStore {

    public static final String STORE_NAME = "wicket-data-store";

    /**
     * Hazelcast distributed map
     */
    private final IMap<String, HazelcastPageData> pageStore;

    public HazelcastDataStore(HazelcastInstance hazelcast) {
        this.pageStore = hazelcast.getMap(STORE_NAME);
    }

    @Override
    public byte[] getData(String sessionId, int id) {
        HazelcastPageData pageData = pageStore.get(sessionId);
//        log.info("getData for session: {} with id {} exists: {}", sessionId, id, pageData != null && pageData.getDataMap().containsKey(id));
        if (pageData != null) {
            Map<Integer, byte[]> data = pageData.getDataMap();
            return data.get(id);
        }
        return null;
    }

    @Override
    public void removeData(String sessionId, int id) {
        HazelcastPageData pageData = pageStore.get(sessionId);
        if (pageData != null) {
            pageData.removeData(id);
            pageStore.put(sessionId, pageData);
        }
    }

    @Override
    public void removeData(String sessionId) {
        pageStore.remove(sessionId);
    }

    @Override
    public void storeData(String sessionId, int id, byte[] data) {
        HazelcastPageData pageData = pageStore.get(sessionId);
        if (pageData == null) {
            pageData = new HazelcastPageData(sessionId);
        }
        pageData.putData(id, data);
        pageStore.put(sessionId, pageData);
    }

    @Override
    public void destroy() {
        try {
            pageStore.clear();
        } catch (Exception ex) {
            /* Don't handle */
        }
    }

    @Override
    public boolean isReplicated() {
        return false;
    }

    @Override
    public boolean canBeAsynchronous() {
        return false;
    }


    private static class HazelcastPageData implements Serializable {

        private final String sessionId;
        private final Map<Integer, byte[]> dataMap;

        public HazelcastPageData(String sessionId) {
            this.sessionId = sessionId;
            dataMap = new HashMap<>();
        }

        public String getSessionId() {
            return sessionId;
        }

        public Map<Integer, byte[]> getDataMap() {
            return dataMap;
        }

        public void putData(int id, byte[] data) {
            dataMap.put(id, data);
        }

        public void removeData(int id) {
            dataMap.remove(id);
        }
    }
}
