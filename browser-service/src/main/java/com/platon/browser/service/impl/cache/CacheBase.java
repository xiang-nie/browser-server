package com.platon.browser.service.impl.cache;

import com.alibaba.fastjson.JSON;
import com.platon.browser.config.ChainsConfig;
import com.platon.browser.dao.entity.Block;
import com.platon.browser.dao.entity.Transaction;
import com.platon.browser.dto.RespPage;
import com.platon.browser.enums.I18nEnum;
import com.platon.browser.util.I18nUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;

/**
 * @Auther: Chendongming
 * @Date: 2019/4/11 15:43
 * @Description:
 */
public class CacheBase {
    private final Logger logger = LoggerFactory.getLogger(NodeCacheServiceImpl.class);

    protected boolean validateParam(ChainsConfig chainsConfig, String chainId, Collection items){
        if (!chainsConfig.getChainIds().contains(chainId)){
            // 非法链ID
            logger.debug("Invalid Chain ID: {}", chainId);
            return false;
        }
        if(items.size()==0){
            // 无更新内容
            logger.debug("Empty Content");
            return false;
        }
        return true;
    }

    protected static class CachePageInfo<T>{
        Set<String> data;
        RespPage<T> page;
    }

    protected <T> void updateCache(String cacheKey,Collection<T> data, RedisTemplate<String,String> redisTemplate, long maxItemNum){
        long size = redisTemplate.opsForZSet().size(cacheKey);
        Set<ZSetOperations.TypedTuple<String>> tupleSet = new HashSet<>();
        data.forEach(item -> {
            Long startOffset=0l,endOffset=0l,score=0l;
            if(item instanceof Block) startOffset=endOffset=score = ((Block)item).getTimestamp().getTime();
            if(item instanceof Transaction) startOffset=endOffset=score = ((Transaction)item).getTimestamp().getTime();
            // 根据score来判断缓存中的记录是否已经存在
            Set<String> exist = redisTemplate.opsForZSet().rangeByScore(cacheKey,startOffset,endOffset);
            if(exist.size()==0){
                // 在缓存中不存在的才放入缓存
                tupleSet.add(new DefaultTypedTuple(JSON.toJSONString(item),score.doubleValue()));
            }
        });
        if(tupleSet.size()>0){
            redisTemplate.opsForZSet().add(cacheKey, tupleSet);
        }
        if(size>maxItemNum){
            // 更新后的缓存条目数量大于所规定的数量，则需要删除最旧的 (size-maxItemNum)个
            redisTemplate.opsForZSet().removeRange(cacheKey,0,size-maxItemNum);
        }
    }

    protected <T> CachePageInfo getCachePageInfo(String cacheKey,int pageNum,int pageSize,T target, I18nUtil i18n, RedisTemplate<String,String> redisTemplate, long maxItemNum){
        RespPage<T> page = new RespPage<>();
        page.setErrMsg(i18n.i(I18nEnum.SUCCESS));

        CachePageInfo<T> cpi = new CachePageInfo<>();
        Long size = redisTemplate.opsForZSet().size(cacheKey);
        Long pagingTotalCount = size;
        if(pagingTotalCount>maxItemNum){
            // 如果缓存数量大于maxItemNum，则以maxItemNum作为分页数量
            pagingTotalCount = maxItemNum;
        }
        page.setTotalCount(pagingTotalCount.intValue());

        Long pageCount = pagingTotalCount/pageSize;
        if(pagingTotalCount%pageSize!=0){
            pageCount+=1;
        }
        page.setTotalPages(pageCount.intValue());

        // Redis的缓存分页从索引0开始
        if(pageNum<=0){
            pageNum=1;
        }
        if(pageSize<=0){
            pageSize=1;
        }
        Set<String> cache = redisTemplate.opsForZSet().reverseRange(cacheKey,(pageNum-1)*pageSize,(pageNum*pageSize)-1);
        cpi.data = cache;
        cpi.page = page;
        return cpi;
    }

    /**
     * 通过多个键值批量查询值
     * @param keys
     * @param useParallel
     * @param clazz
     * @param redisTemplate
     * @param <T>
     * @return
     */
    protected <T> Map<String,T> batchQueryByKeys(List<String> keys, Boolean useParallel, Class<T> clazz,RedisTemplate<String,String> redisTemplate){
        if(null == keys || keys.size() == 0 ) return null;
        if(null == useParallel) useParallel = true;

        List<Object> results = redisTemplate.executePipelined( (RedisCallback<Object>) connection -> {
            StringRedisConnection stringRedisConn = (StringRedisConnection)connection;
            keys.forEach(key->stringRedisConn.get(key));
            return null;
        });
        if(null == results || results.size() == 0 ) return null;

        Map<String,T> resultMap  =  null;
        if(useParallel){
            Map<String,T> resultMapOne  = Collections.synchronizedMap(new HashMap<>());
            keys.parallelStream().forEach(key -> {
                String res = (String)results.get(keys.indexOf(key));
                resultMapOne.put(key, JSON.parseObject(res,clazz));
            });
            resultMap = resultMapOne;
        }else{
            Map<String,T> resultMapTwo  = new HashMap<>();
            keys.forEach(key-> {
                String res = (String)results.get(keys.indexOf(key));
                resultMapTwo.put(key,JSON.parseObject(res,clazz));
            });
            resultMap = resultMapTwo;
        }
        return  resultMap;
    }
}
