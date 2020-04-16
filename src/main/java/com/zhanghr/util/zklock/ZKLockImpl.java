package com.zhanghr.util.zklock;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;

/**
 * @author ：杨过
 * @date ：Created in 2020/2/19
 * @version: V1.0
 * @slogan: 天下风云出我辈，一入代码岁月催
 * @description:
 **/
@Slf4j
public class ZKLockImpl implements ZKLock,InitializingBean {

    private final static String LOCK_ROOT_PATH = "/ZkLock";

    private CountDownLatch countDownLatch = new CountDownLatch(1);

    @Autowired
    private CuratorFramework curatorFramework;


    @Override
    public boolean lock(String lockpath) {
        boolean result = false;
        String keyPath = LOCK_ROOT_PATH + lockpath;

        try {
            curatorFramework
                    .create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                    .forPath(keyPath);
            result = true;
            log.info("success to acquire mutex lock for path:{}",keyPath);
        } catch (Exception e) {
            log.info("Thread:{};failed to acquire mutex lock for path:{}",Thread.currentThread().getName(),keyPath);
            if(countDownLatch.getCount() <= 0){
                countDownLatch = new CountDownLatch(1);
            }
            try {
                countDownLatch.await();
            } catch (InterruptedException e1) {
                log.info("InterruptedException message:{}",e1.getMessage());
            }
        }
        return result;
    }

    @Override
    public boolean unlock(String lockpath) {
        String keyPath = LOCK_ROOT_PATH + lockpath;
        try {
            if(curatorFramework.checkExists().forPath(keyPath) != null){
                curatorFramework.delete().forPath(keyPath);
            }
        } catch (Exception e) {
            log.error("failed to release mutex lock");
            return false;
        }
        return true;
    }

    /**
     * 监听节点事件
     * @param lockPath
     *       加锁的路径
     */
    private void addWatcher(String lockPath) throws Exception {
        String keyPath;
        if(LOCK_ROOT_PATH.equals(lockPath)){
            keyPath = lockPath;
        }else{
            keyPath = LOCK_ROOT_PATH + lockPath;
        }

        final PathChildrenCache cache = new PathChildrenCache(curatorFramework,keyPath,false);
        cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
        /*
         * 添加监听器
         */
        cache.getListenable().addListener((client,event) -> {
            if(event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)){
                String oldPath = event.getData().getPath();
                log.info("oldPath delete:{},redis缓存已经更新！",oldPath);
                if(oldPath.contains(lockPath)){
                    //释放计数器，尝试加锁
                    countDownLatch.countDown();
                }
            }
        });
    }

    @Override
    public void afterPropertiesSet() {
        curatorFramework = curatorFramework.usingNamespace("zklock-namespace");
        //zk锁的根路径 不存在则创建
        try {
            if(curatorFramework.checkExists().forPath(LOCK_ROOT_PATH) == null){
                curatorFramework
                        .create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath(LOCK_ROOT_PATH);
            }
            //启动监听器
            addWatcher(LOCK_ROOT_PATH);
        } catch (Exception e) {
            log.error("connect zookeeper failed:{}",e.getMessage(),e);
        }
    }
























/* 以下锁工作方式类似全局的分布式synchronized功能 */
/*----------------------------------------------------------------------------------*/
    /*@Override
    public void syncLock(String lockpath) {
        String keyPath = LOCK_ROOT_PATH + lockpath;
        //自旋spin
        for(;;){
            try {
                curatorFramework
                        .create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath(keyPath);
                log.info("success to acquire mutex lock for path:{}",keyPath);
                break;
            } catch (Exception e) {
                log.info("failed to acquire mutex lock for path:{}",keyPath);
                if(countDownLatch.getCount() <= 0){
                    countDownLatch = new CountDownLatch(1);
                }
                try {
                    countDownLatch.await();
                } catch (InterruptedException e1) {
                    log.info("InterruptedException message:{}",e1.getMessage());
                }
            }
        }
    }

    @Override
    public boolean unSyncLock(String lockpath) {
        String keyPath = LOCK_ROOT_PATH + lockpath;
        try {
            if(curatorFramework.checkExists().forPath(keyPath) != null){
                curatorFramework.delete().forPath(keyPath);
            }
        } catch (Exception e) {
            log.error("failed to release mutex lock");
            return false;
        }
        return true;
    }*/

    /**
     * 监听节点事件
     * @param lockPath
     *       加锁的路径
     */
    /*private void addWatcher(String lockPath) throws Exception {
        String keyPath;
        if(LOCK_ROOT_PATH.equals(lockPath)){
            keyPath = lockPath;
        }else{
            keyPath = LOCK_ROOT_PATH + lockPath;
        }

        final PathChildrenCache cache = new PathChildrenCache(curatorFramework,keyPath,false);
        cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
        *//*
         * 添加监听器
         *//*
        cache.getListenable().addListener((client,event) -> {
            if(event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)){
                String oldPath = event.getData().getPath();
                log.info("oldPath:{},已经被断开！",oldPath);
                if(oldPath.contains(lockPath)){
                    //释放计数器，尝试加锁
                    countDownLatch.countDown();
                }
            }
        });
    }*/


}
