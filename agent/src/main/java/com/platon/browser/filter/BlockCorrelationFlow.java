package com.platon.browser.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.platon.browser.common.base.AppException;
import com.platon.browser.common.dto.agent.CandidateDto;
import com.platon.browser.common.enums.ErrorCodeEnum;
import com.platon.browser.dao.entity.Block;
import com.platon.browser.dao.entity.NodeRanking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.*;

/**
 * User: dongqile
 * Date: 2019/1/15
 * Time: 14:34
 */
@Component
public class BlockCorrelationFlow {

    private static Logger log = LoggerFactory.getLogger(BlockCorrelationFlow.class);

    @Value("${chain.id}")
    private String chainId;

    @Autowired
    private BlockFilter blockFilter;

    @Autowired
    private TransactionFilter transactionFilter;

    @Autowired
    private NodeFilter nodeFilter;


    @Transactional
    public Map<String,Object> doFilter (EthBlock ethBlock, List<Transaction> transactionList,List<TransactionReceipt> transactionReceiptList,
                                       BigInteger publicKey,String nodeInfoList,Map<String,Object> transactionReceiptMap) throws Exception {
        Block block = new Block();
        Map<String,Object> map = new HashMap <>();
        List<NodeRanking> nodeRankings = new ArrayList <>();

        try {
            block = blockFilter.blockAnalysis(ethBlock, transactionReceiptList, transactionList,nodeInfoList,publicKey,transactionReceiptMap);
            String blockString = JSON.toJSONString(block);
            log.debug("block info :" + blockString);
            if (StringUtils.isEmpty(block)) {
                log.error("Analysis Block is null !!!....");
            }
        } catch (Exception e) {
            log.error("Block Filter exception", e);
            log.error("Block analysis exception", e.getMessage());
            throw new AppException(ErrorCodeEnum.BLOCKCHAIN_ERROR);
        }

        try {
            if (transactionList.size() > 0 && transactionReceiptList.size() > 0
                    && transactionList != null && transactionReceiptList != null) {
                boolean res = transactionFilter.transactionAnalysis(transactionReceiptMap, transactionList, block.getTimestamp().getTime());
                if (!res) {
                    log.error("Analysis Transaction is null !!!....");
                }
            }
            log.debug("Transactions in the block are empty !!!...");
        } catch (Exception e) {
            log.error("Transaction Filter exception", e);
            log.error("Transaction analysis exception", e.getMessage());
            throw new AppException(ErrorCodeEnum.TX_ERROR);
        }

        try {
            List <CandidateDto> list = JSON.parseArray(nodeInfoList, CandidateDto.class);
            if (!StringUtils.isEmpty(nodeInfoList) && list.size() > 0) {
                 nodeRankings = nodeFilter.nodeAnalysis(nodeInfoList, block.getNumber().longValue(), ethBlock, block.getBlockReward(),publicKey);
                String nodeRankingString = JSONArray.toJSONString(nodeRankings);
                log.debug("node info :" + nodeRankingString);
                if (nodeRankings.size() < 0 && nodeRankings == null) {
                    log.error("Analysis NodeInfo is null !!!....");
                }
            }
            log.debug("NodeInfo in the block are empty !!!...");
        } catch (Exception e) {
            log.error("Node Filter exception", e);
            log.error("Node analysis exception", e.getMessage());
            throw new AppException(ErrorCodeEnum.NODE_ERROR);
        }
        map.put("block",block);
        map.put("nodeRanking",nodeRankings);
        return map;
    }

}

