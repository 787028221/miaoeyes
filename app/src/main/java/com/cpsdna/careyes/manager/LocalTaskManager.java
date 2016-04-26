package com.cpsdna.careyes.manager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import xcoding.commons.util.TimeoutException;

public final class LocalTaskManager
{
    
    private LocalTaskManager(){}
    
    private static Map<String, TaskData> tasks = new HashMap<String, TaskData>();
    
    static {
        new Thread() {
            public void run() {
                while(true) {
                    synchronized (tasks) {
                        long now = System.currentTimeMillis();
                        Iterator<Entry<String, TaskData>> iterator = tasks.entrySet().iterator();
                        while(iterator.hasNext()) {
                            Entry<String, TaskData> entry = iterator.next();
                            TaskData data = entry.getValue();
                            long interval = now - data.createTime;
                            if(data.data == null) {
                                if(interval >= 30000) iterator.remove();
                            }else {
                                if(interval >= 1000 * 60 * 1.5) {
                                    iterator.remove();
                                }
                            }
                        }
                    }
                    try {
                        Thread.sleep(1500);
                    }catch(InterruptedException e) {
                    }
                }
            }
        }.start();
    }
    
    public static void addTask(String taskId) {
        synchronized (tasks) {
            tasks.put(taskId, new TaskData());
        }
    }
    
    public static boolean updateTask(String taskId,String data) {
        synchronized (tasks) {
            TaskData task = tasks.get(taskId);
            if(task == null) return false;
            task.data = data;
            return true;
        }
    }
    
    public static String getTask(String taskId) throws TimeoutException {
        TaskData data;
        synchronized (tasks) {
            data = tasks.get(taskId);
        }
        if(data == null) throw new TimeoutException();
        return data.data;
    }
    
    private static class TaskData {
        
        private String data;
        private long createTime = System.currentTimeMillis();
        
    }
    
}
